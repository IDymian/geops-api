package io.github.idymian.geops.coupons.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.idymian.geops.coupons.domain.UserCoupon;
import io.github.idymian.geops.coupons.domain.UserCouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCoupon, UUID> {

    long countByCouponIdAndUserId(UUID couponId, UUID userId);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"coupon", "coupon.offer", "coupon.offer.business"})
    List<UserCoupon> findByUserIdOrderByClaimedAtDesc(UUID userId);

    /** Locks the code row so two cashiers can never redeem the same code twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCoupon uc where uc.code = :code")
    Optional<UserCoupon> findForRedemption(@Param("code") String code);

    /** Rows of {@code [couponId, count]} for the given status. */
    @Query("""
            select uc.coupon.id, count(uc) from UserCoupon uc
            where uc.coupon.id in :couponIds and uc.status = :status
            group by uc.coupon.id
            """)
    List<Object[]> countByCouponAndStatus(@Param("couponIds") Collection<UUID> couponIds,
                                          @Param("status") UserCouponStatus status);
}
