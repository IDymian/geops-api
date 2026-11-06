package io.github.idymian.geops.coupons.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.idymian.geops.coupons.application.dto.CouponDtos.RedemptionResponse;
import io.github.idymian.geops.coupons.domain.Coupon;
import io.github.idymian.geops.coupons.domain.CouponCodes;
import io.github.idymian.geops.coupons.domain.UserCoupon;
import io.github.idymian.geops.coupons.infrastructure.UserCouponRepository;
import io.github.idymian.geops.identity.infrastructure.UserAccountRepository;
import io.github.idymian.geops.offers.application.BusinessService;
import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.shared.audit.AuditLog;
import io.github.idymian.geops.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cashier side: a provider redeems a customer's code at the counter.
 */
@Service
public class RedemptionService {

    private final UserCouponRepository userCoupons;
    private final UserAccountRepository users;
    private final BusinessService businessService;
    private final AuditLog auditLog;
    private final Clock clock;

    public RedemptionService(UserCouponRepository userCoupons, UserAccountRepository users,
                             BusinessService businessService, AuditLog auditLog, Clock clock) {
        this.userCoupons = userCoupons;
        this.users = users;
        this.businessService = businessService;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public RedemptionResponse redeem(UUID providerId, String rawCode) {
        Business business = businessService.requireBusiness(providerId);
        String code = CouponCodes.normalize(rawCode);

        // Row lock: the same code scanned twice at once is redeemed only once.
        UserCoupon userCoupon = userCoupons.findForRedemption(code)
                // Another business's code answers like an unknown one: codes are not confirmed to strangers.
                .filter(found -> found.getCoupon().getOffer().getBusiness().getId().equals(business.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUPON_CODE_NOT_FOUND",
                        "This code does not belong to your business."));

        Instant now = clock.instant();
        switch (userCoupon.statusAt(now)) {
            case REDEEMED -> throw new ApiException(HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED",
                    "This code was already redeemed on " + userCoupon.getRedeemedAt() + ".");
            case EXPIRED -> throw new ApiException(HttpStatus.CONFLICT, "COUPON_EXPIRED", "This code has expired.");
            case CLAIMED -> userCoupon.redeem(providerId, now);
        }

        Coupon coupon = userCoupon.getCoupon();
        auditLog.record(providerId, "COUPON_REDEEMED", "user_coupon", userCoupon.getId(), Map.of(
                "couponId", coupon.getId().toString(),
                "offerId", coupon.getOffer().getId().toString(),
                "businessId", business.getId().toString()));

        return new RedemptionResponse(userCoupon.getCode(), coupon.getTitle(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getOffer().getTitle(), coupon.getOffer().getOfferPrice(),
                firstName(userCoupon.getUserId()), now);
    }

    private String firstName(UUID userId) {
        return users.findById(userId)
                .map(user -> user.getFullName().strip().split("\\s+")[0])
                .orElse(null);
    }
}
