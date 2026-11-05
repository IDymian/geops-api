package io.github.idymian.geops.offers.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.offers.application.dto.OfferDtos;
import io.github.idymian.geops.offers.application.dto.OfferDtos.BusinessSummary;
import io.github.idymian.geops.offers.application.dto.OfferDtos.NearbyOfferResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Radius search over live offers with PostGIS.
 *
 * <p>{@code ST_DWithin} on {@code geography} measures real meters and uses the GiST
 * index on {@code businesses.location}; {@code ST_Distance} only sorts the matches.
 */
@Repository
public class NearbyOfferQuery {

    private static final String SQL = """
            SELECT o.id, o.title, o.image_url, c.slug AS category_slug,
                   o.original_price, o.offer_price, o.ends_at,
                   b.id AS business_id, b.name AS business_name, b.address, b.phone,
                   b.latitude, b.longitude,
                   ST_Distance(b.location, point.geo) AS distance_m
            FROM offers o
            JOIN businesses b ON b.id = o.business_id
            JOIN categories c ON c.id = o.category_id
            CROSS JOIN (SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS geo) AS point
            WHERE o.active
              AND o.starts_at <= :now AND o.ends_at > :now
              AND ST_DWithin(b.location, point.geo, :radius)
              %s
            ORDER BY distance_m, o.id
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public NearbyOfferQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<NearbyOfferResponse> find(double latitude, double longitude, int radiusMeters,
                                          String categorySlug, int limit, Instant now) {
        boolean byCategory = categorySlug != null && !categorySlug.isBlank();
        JdbcClient.StatementSpec statement = jdbc.sql(SQL.formatted(byCategory ? "AND c.slug = :category" : ""))
                .param("lat", latitude)
                .param("lng", longitude)
                .param("radius", radiusMeters)
                .param("now", Timestamp.from(now))
                .param("limit", limit);
        if (byCategory) {
            statement = statement.param("category", categorySlug);
        }
        return statement.query(NearbyOfferQuery::map).list();
    }

    private static NearbyOfferResponse map(ResultSet rs, int row) throws SQLException {
        var originalPrice = rs.getBigDecimal("original_price");
        var offerPrice = rs.getBigDecimal("offer_price");
        return new NearbyOfferResponse(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("image_url"),
                rs.getString("category_slug"),
                originalPrice,
                offerPrice,
                OfferDtos.discountPercent(originalPrice, offerPrice),
                rs.getTimestamp("ends_at").toInstant(),
                new BusinessSummary(
                        rs.getObject("business_id", UUID.class),
                        rs.getString("business_name"),
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")),
                (int) Math.round(rs.getDouble("distance_m")));
    }
}
