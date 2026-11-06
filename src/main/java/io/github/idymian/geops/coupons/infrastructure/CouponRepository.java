package io.github.idymian.geops.coupons.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.idymian.geops.coupons.domain.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /**
     * Locks the coupon row ({@code SELECT ... FOR UPDATE}) so concurrent claims are
     * checked one at a time: the stock and the per-user limit can never be exceeded.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findForClaim(@Param("id") UUID id);

    /** Coupons a consumer can claim now for an offer (the caller checks the offer is live). */
    @Query("""
            select c from Coupon c
            where c.offer.id = :offerId
              and c.validFrom <= :now and c.validUntil > :now
              and (c.stock is null or c.claimedCount < c.stock)
            order by c.createdAt
            """)
    List<Coupon> findAvailable(@Param("offerId") UUID offerId, @Param("now") Instant now);

    @Query("""
            select c from Coupon c join fetch c.offer o
            where o.business.id = :businessId
            order by c.createdAt desc
            """)
    List<Coupon> findByBusiness(@Param("businessId") UUID businessId);
}
