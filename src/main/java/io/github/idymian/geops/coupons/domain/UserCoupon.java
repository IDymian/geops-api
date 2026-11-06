package io.github.idymian.geops.coupons.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The unique code a consumer got by claiming a {@link Coupon}. Redeemed once, at the business.
 */
@Entity
@Table(name = "user_coupons")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false, updatable = false)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserCouponStatus status;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "redeemed_by")
    private UUID redeemedBy;

    protected UserCoupon() {
        // for JPA
    }

    public UserCoupon(Coupon coupon, UUID userId, String code, Instant claimedAt) {
        this.coupon = coupon;
        this.userId = userId;
        this.code = code;
        this.status = UserCouponStatus.CLAIMED;
        this.claimedAt = claimedAt;
    }

    /** The stored status, or EXPIRED once an unredeemed coupon is past its validity. */
    public UserCouponStatus statusAt(Instant now) {
        if (status == UserCouponStatus.CLAIMED && !now.isBefore(coupon.getValidUntil())) {
            return UserCouponStatus.EXPIRED;
        }
        return status;
    }

    /** Call only while holding the row lock, after checking {@link #statusAt(Instant)} is CLAIMED. */
    public void redeem(UUID providerId, Instant now) {
        if (statusAt(now) != UserCouponStatus.CLAIMED) {
            throw new IllegalStateException("Coupon code " + id + " cannot be redeemed");
        }
        this.status = UserCouponStatus.REDEEMED;
        this.redeemedAt = now;
        this.redeemedBy = providerId;
    }

    public UUID getId() {
        return id;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
