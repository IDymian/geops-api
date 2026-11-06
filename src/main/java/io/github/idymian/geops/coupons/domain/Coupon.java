package io.github.idymian.geops.coupons.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.github.idymian.geops.offers.domain.Offer;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * The provider's coupon template for an offer: discount, stock and validity.
 * Each consumer who claims it gets their own {@link UserCoupon} code.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false, updatable = false)
    private Offer offer;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** {@code null} means unlimited. */
    private Integer stock;

    @Column(name = "claimed_count", nullable = false)
    private int claimedCount;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Coupon() {
        // for JPA
    }

    public Coupon(Offer offer, String title, DiscountType discountType, BigDecimal discountValue, Integer stock,
                  int perUserLimit, Instant validFrom, Instant validUntil) {
        this.offer = offer;
        this.title = title.strip();
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.stock = stock;
        this.perUserLimit = perUserLimit;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /** Within its validity and its offer is visible to consumers. */
    public boolean isClaimableAt(Instant now) {
        return !now.isBefore(validFrom) && now.isBefore(validUntil) && offer.isLive(now);
    }

    public boolean hasStock() {
        return stock == null || claimedCount < stock;
    }

    /** Codes left, or {@code null} when unlimited. */
    public Integer remaining() {
        return stock == null ? null : stock - claimedCount;
    }

    /** Call only while holding the row lock, after checking {@link #hasStock()}. */
    public void registerClaim() {
        if (!hasStock()) {
            throw new IllegalStateException("Coupon " + id + " has no stock left");
        }
        claimedCount++;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Offer getOffer() {
        return offer;
    }

    public String getTitle() {
        return title;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public Integer getStock() {
        return stock;
    }

    public int getClaimedCount() {
        return claimedCount;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }
}
