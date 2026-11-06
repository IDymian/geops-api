package io.github.idymian.geops.coupons.domain;

public enum UserCouponStatus {
    CLAIMED,
    REDEEMED,
    /** Never stored by the application: computed when the coupon validity has passed. */
    EXPIRED
}
