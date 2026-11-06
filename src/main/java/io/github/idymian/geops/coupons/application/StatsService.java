package io.github.idymian.geops.coupons.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.coupons.application.dto.CouponDtos.DailyStat;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.OfferStat;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.StatsResponse;
import io.github.idymian.geops.offers.application.BusinessService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business dashboard numbers, computed in SQL so the database does the counting.
 */
@Service
public class StatsService {

    /** Businesses are in Peru: "today" and the daily buckets follow Lima time. */
    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int DAYS = 7;
    private static final int TOP_OFFERS = 5;

    private static final String OFFER_TOTALS = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE active AND starts_at <= :now AND ends_at > :now) AS live
            FROM offers WHERE business_id = :businessId
            """;

    private static final String COUPON_TOTALS = """
            SELECT count(*) AS claimed,
                   count(*) FILTER (WHERE uc.status = 'REDEEMED') AS redeemed
            FROM user_coupons uc
            JOIN coupons c ON c.id = uc.coupon_id
            JOIN offers o ON o.id = c.offer_id
            WHERE o.business_id = :businessId
            """;

    /** One row per day, including days without activity. */
    private static final String DAILY = """
            WITH days AS (
                SELECT CAST(d AS date) AS day
                FROM generate_series(CAST(:fromDay AS date), CAST(:toDay AS date), interval '1 day') AS d
            ),
            mine AS (
                SELECT uc.claimed_at, uc.redeemed_at
                FROM user_coupons uc
                JOIN coupons c ON c.id = uc.coupon_id
                JOIN offers o ON o.id = c.offer_id
                WHERE o.business_id = :businessId
                  AND (uc.claimed_at >= :since OR uc.redeemed_at >= :since)
            ),
            claims AS (
                SELECT CAST(claimed_at AT TIME ZONE 'America/Lima' AS date) AS day, count(*) AS n
                FROM mine GROUP BY 1
            ),
            redemptions AS (
                SELECT CAST(redeemed_at AT TIME ZONE 'America/Lima' AS date) AS day, count(*) AS n
                FROM mine WHERE redeemed_at IS NOT NULL GROUP BY 1
            )
            SELECT days.day, coalesce(claims.n, 0) AS claimed, coalesce(redemptions.n, 0) AS redeemed
            FROM days
            LEFT JOIN claims ON claims.day = days.day
            LEFT JOIN redemptions ON redemptions.day = days.day
            ORDER BY days.day
            """;

    private static final String TOP = """
            SELECT o.id, o.title,
                   count(uc.id) AS claimed,
                   count(uc.id) FILTER (WHERE uc.status = 'REDEEMED') AS redeemed
            FROM offers o
            JOIN coupons c ON c.offer_id = o.id
            JOIN user_coupons uc ON uc.coupon_id = c.id
            WHERE o.business_id = :businessId
            GROUP BY o.id, o.title
            ORDER BY redeemed DESC, claimed DESC, o.title
            LIMIT :limit
            """;

    private final JdbcClient jdbc;
    private final BusinessService businessService;
    private final Clock clock;

    public StatsService(JdbcClient jdbc, BusinessService businessService, Clock clock) {
        this.jdbc = jdbc;
        this.businessService = businessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsResponse forBusiness(UUID ownerId) {
        UUID businessId = businessService.requireBusiness(ownerId).getId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, LIMA);
        LocalDate firstDay = today.minusDays(DAYS - 1);

        long[] offerTotals = jdbc.sql(OFFER_TOTALS)
                .param("businessId", businessId)
                .param("now", Timestamp.from(now))
                .query((rs, row) -> new long[] {rs.getLong("total"), rs.getLong("live")})
                .single();
        long[] couponTotals = jdbc.sql(COUPON_TOTALS)
                .param("businessId", businessId)
                .query((rs, row) -> new long[] {rs.getLong("claimed"), rs.getLong("redeemed")})
                .single();
        List<DailyStat> daily = jdbc.sql(DAILY)
                .param("businessId", businessId)
                .param("fromDay", firstDay)
                .param("toDay", today)
                .param("since", Timestamp.from(firstDay.atStartOfDay(LIMA).toInstant()))
                .query((rs, row) -> new DailyStat(rs.getObject("day", LocalDate.class), rs.getLong("claimed"),
                        rs.getLong("redeemed")))
                .list();
        List<OfferStat> top = jdbc.sql(TOP)
                .param("businessId", businessId)
                .param("limit", TOP_OFFERS)
                .query((rs, row) -> new OfferStat(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getLong("claimed"), rs.getLong("redeemed")))
                .list();

        long claimed = couponTotals[0];
        long redeemed = couponTotals[1];
        int rate = claimed == 0 ? 0 : (int) Math.round(redeemed * 100.0 / claimed);
        return new StatsResponse(offerTotals[0], offerTotals[1], claimed, redeemed, rate, daily, top);
    }
}
