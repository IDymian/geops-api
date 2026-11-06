package io.github.idymian.geops.coupons.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.idymian.geops.coupons.application.dto.CouponDtos.AvailableCouponResponse;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.CouponRequest;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.CouponResponse;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.UserCouponResponse;
import io.github.idymian.geops.coupons.domain.Coupon;
import io.github.idymian.geops.coupons.domain.CouponCodes;
import io.github.idymian.geops.coupons.domain.DiscountType;
import io.github.idymian.geops.coupons.domain.UserCoupon;
import io.github.idymian.geops.coupons.domain.UserCouponStatus;
import io.github.idymian.geops.coupons.infrastructure.CouponRepository;
import io.github.idymian.geops.coupons.infrastructure.UserCouponRepository;
import io.github.idymian.geops.offers.application.BusinessService;
import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.offers.domain.Offer;
import io.github.idymian.geops.offers.infrastructure.OfferRepository;
import io.github.idymian.geops.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupons: providers issue them for their offers, consumers claim a personal code.
 */
@Service
public class CouponService {

    private static final int CODE_ATTEMPTS = 5;

    private final CouponRepository coupons;
    private final UserCouponRepository userCoupons;
    private final OfferRepository offers;
    private final BusinessService businessService;
    private final Clock clock;

    public CouponService(CouponRepository coupons, UserCouponRepository userCoupons, OfferRepository offers,
                         BusinessService businessService, Clock clock) {
        this.coupons = coupons;
        this.userCoupons = userCoupons;
        this.offers = offers;
        this.businessService = businessService;
        this.clock = clock;
    }

    // --- provider ---

    @Transactional
    public CouponResponse create(UUID ownerId, UUID offerId, CouponRequest request) {
        Business business = businessService.requireBusiness(ownerId);
        Offer offer = offers.findWithDetailsById(offerId)
                .filter(found -> found.getBusiness().getId().equals(business.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offer not found."));

        Instant now = clock.instant();
        Instant validFrom = offer.getStartsAt().isAfter(now) ? offer.getStartsAt() : now;
        Instant validUntil = request.validUntil() != null ? request.validUntil() : offer.getEndsAt();
        if (!validUntil.isAfter(validFrom) || validUntil.isAfter(offer.getEndsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_INVALID_PERIOD",
                    "The coupon must be valid in the future and cannot outlive its offer.");
        }
        validateDiscount(request, offer);

        Coupon coupon = coupons.save(new Coupon(offer, request.title(), request.discountType(),
                request.discountValue(), request.stock(),
                request.perUserLimit() != null ? request.perUserLimit() : 1, validFrom, validUntil));
        return CouponResponse.from(coupon, 0, now);
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listMine(UUID ownerId) {
        Business business = businessService.requireBusiness(ownerId);
        List<Coupon> found = coupons.findByBusiness(business.getId());
        Map<UUID, Long> redeemed = new HashMap<>();
        if (!found.isEmpty()) {
            for (Object[] row : userCoupons.countByCouponAndStatus(
                    found.stream().map(Coupon::getId).toList(), UserCouponStatus.REDEEMED)) {
                redeemed.put((UUID) row[0], (Long) row[1]);
            }
        }
        Instant now = clock.instant();
        return found.stream()
                .map(coupon -> CouponResponse.from(coupon, redeemed.getOrDefault(coupon.getId(), 0L), now))
                .toList();
    }

    private static void validateDiscount(CouponRequest request, Offer offer) {
        boolean valid = switch (request.discountType()) {
            case PERCENT -> request.discountValue().compareTo(BigDecimal.valueOf(100)) <= 0;
            // A fixed discount larger than the price would mean paying the customer.
            case FIXED -> request.discountValue().compareTo(offer.getOfferPrice()) <= 0;
        };
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_INVALID_DISCOUNT",
                    request.discountType() == DiscountType.PERCENT
                            ? "A percentage discount cannot exceed 100."
                            : "A fixed discount cannot exceed the offer price.");
        }
    }

    // --- consumer ---

    /** Coupons of a live offer that can be claimed right now. */
    @Transactional(readOnly = true)
    public List<AvailableCouponResponse> available(UUID offerId) {
        Instant now = clock.instant();
        boolean live = offers.findById(offerId).map(offer -> offer.isLive(now)).orElse(false);
        if (!live) {
            throw new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offer not found.");
        }
        return coupons.findAvailable(offerId, now).stream().map(AvailableCouponResponse::from).toList();
    }

    @Transactional
    public UserCouponResponse claim(UUID userId, UUID couponId) {
        // Row lock: concurrent claims of this coupon wait here, so the checks below cannot race.
        Coupon coupon = coupons.findForClaim(couponId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "Coupon not found."));
        Instant now = clock.instant();
        if (!coupon.isClaimableAt(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_UNAVAILABLE", "This coupon is no longer available.");
        }
        if (!coupon.hasStock()) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_SOLD_OUT", "All codes of this coupon were claimed.");
        }
        if (userCoupons.countByCouponIdAndUserId(couponId, userId) >= coupon.getPerUserLimit()) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_LIMIT_REACHED",
                    "You already claimed this coupon the maximum number of times.");
        }

        coupon.registerClaim();
        UserCoupon claimed = userCoupons.save(new UserCoupon(coupon, userId, uniqueCode(), now));
        return UserCouponResponse.from(claimed, now);
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> mine(UUID userId) {
        Instant now = clock.instant();
        return userCoupons.findByUserIdOrderByClaimedAtDesc(userId).stream()
                .map(userCoupon -> UserCouponResponse.from(userCoupon, now))
                .toList();
    }

    /** A collision is about one in a trillion; the unique index is the final guarantee. */
    private String uniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = CouponCodes.generate();
            if (!userCoupons.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique coupon code");
    }
}
