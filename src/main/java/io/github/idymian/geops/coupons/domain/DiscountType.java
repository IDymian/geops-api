package io.github.idymian.geops.coupons.domain;

/** How a coupon discounts the offer price. */
public enum DiscountType {
    /** {@code discountValue} is a percentage (1 to 100). */
    PERCENT,
    /** {@code discountValue} is an amount in soles. */
    FIXED
}
