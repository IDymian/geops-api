package io.github.idymian.geops.coupons.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.coupons.domain.Coupon;
import io.github.idymian.geops.coupons.domain.DiscountType;
import io.github.idymian.geops.coupons.domain.UserCoupon;
import io.github.idymian.geops.coupons.domain.UserCouponStatus;
import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.offers.domain.Offer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of the coupons context.
 */
public final class CouponDtos {

    private CouponDtos() {
    }

    // --- provider ---

    public record CouponRequest(
            @NotBlank @Size(min = 3, max = 120) @Schema(example = "10% extra pagando en tienda") String title,
            @NotNull @Schema(example = "PERCENT") DiscountType discountType,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) @Schema(example = "10") BigDecimal discountValue,
            @Positive @Schema(description = "Codes available; omit for unlimited", example = "50") Integer stock,
            @Positive @Max(10) @Schema(description = "Defaults to 1", example = "1") Integer perUserLimit,
            @Future @Schema(description = "Defaults to the offer end; cannot be later") Instant validUntil) {
    }

    public record CouponResponse(
            UUID id, UUID offerId, String offerTitle, String title, DiscountType discountType,
            BigDecimal discountValue, Integer stock, int claimedCount, long redeemedCount,
            @Schema(description = "null when unlimited") Integer remaining, int perUserLimit,
            Instant validFrom, Instant validUntil, @Schema(description = "Consumers can claim it now") boolean claimable) {

        public static CouponResponse from(Coupon coupon, long redeemedCount, Instant now) {
            return new CouponResponse(coupon.getId(), coupon.getOffer().getId(), coupon.getOffer().getTitle(),
                    coupon.getTitle(), coupon.getDiscountType(), coupon.getDiscountValue(), coupon.getStock(),
                    coupon.getClaimedCount(), redeemedCount, coupon.remaining(), coupon.getPerUserLimit(),
                    coupon.getValidFrom(), coupon.getValidUntil(), coupon.isClaimableAt(now) && coupon.hasStock());
        }
    }

    public record RedeemRequest(
            @NotBlank @Size(max = 20) @Schema(example = "K7QM-4XPA", description = "Dashes and spaces are ignored")
            String code) {
    }

    public record RedemptionResponse(
            String code, String couponTitle, DiscountType discountType, BigDecimal discountValue,
            String offerTitle, BigDecimal offerPrice,
            @Schema(description = "Only the first name, enough to greet the customer") String customerFirstName,
            Instant redeemedAt) {
    }

    // --- consumer ---

    public record AvailableCouponResponse(
            UUID id, String title, DiscountType discountType, BigDecimal discountValue,
            @Schema(description = "null when unlimited") Integer remaining, int perUserLimit, Instant validUntil) {

        public static AvailableCouponResponse from(Coupon coupon) {
            return new AvailableCouponResponse(coupon.getId(), coupon.getTitle(), coupon.getDiscountType(),
                    coupon.getDiscountValue(), coupon.remaining(), coupon.getPerUserLimit(), coupon.getValidUntil());
        }
    }

    public record UserCouponResponse(
            UUID id, @Schema(description = "Shown as a QR code and as text") String code, UserCouponStatus status,
            String title, DiscountType discountType, BigDecimal discountValue,
            UUID offerId, String offerTitle, String businessName, String businessAddress,
            Instant claimedAt, Instant validUntil, Instant redeemedAt) {

        public static UserCouponResponse from(UserCoupon userCoupon, Instant now) {
            Coupon coupon = userCoupon.getCoupon();
            Offer offer = coupon.getOffer();
            Business business = offer.getBusiness();
            return new UserCouponResponse(userCoupon.getId(), userCoupon.getCode(), userCoupon.statusAt(now),
                    coupon.getTitle(), coupon.getDiscountType(), coupon.getDiscountValue(), offer.getId(),
                    offer.getTitle(), business.getName(), business.getAddress(), userCoupon.getClaimedAt(),
                    coupon.getValidUntil(), userCoupon.getRedeemedAt());
        }
    }

    // --- stats ---

    public record StatsResponse(
            long totalOffers, long liveOffers, long couponsClaimed, long couponsRedeemed,
            @Schema(description = "Redeemed / claimed, 0 to 100") int redemptionRate,
            @Schema(description = "Last 7 days in Lima time, oldest first") List<DailyStat> daily,
            @Schema(description = "Up to 5 offers with the most redemptions") List<OfferStat> topOffers) {
    }

    public record DailyStat(LocalDate date, long claimed, long redeemed) {
    }

    public record OfferStat(UUID offerId, String title, long claimed, long redeemed) {
    }
}
