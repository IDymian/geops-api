package io.github.idymian.geops.coupons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import io.github.idymian.geops.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class CouponsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    // --- issuing and claiming ---

    @Test
    void providerIssuesACouponAndAConsumerClaimsAPersonalCode() throws Exception {
        String provider = providerWithBusiness();
        String offerId = createOffer(provider, "Pan francés 2x1", "10.00", true);
        String consumer = signUp("CONSUMER", "Ana Torres");

        String couponId = field(createCoupon(provider, offerId, couponJson("10% extra", "PERCENT", "10", 50, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimable").value(true))
                .andExpect(jsonPath("$.remaining").value(50)), "$.id");

        mockMvc.perform(get("/api/v1/offers/" + offerId + "/coupons").header("Authorization", bearer(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("10% extra"));

        String code = field(claim(consumer, couponId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.offerTitle").value("Pan francés 2x1")), "$.code");
        assertThat(code).hasSize(8).doesNotContain("0", "O", "1", "I", "L");

        mockMvc.perform(get("/api/v1/users/me/coupons").header("Authorization", bearer(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value(code))
                .andExpect(jsonPath("$[0].businessAddress").value("Av. Siempre Viva 123"));
    }

    @Test
    void couponRulesAreEnforced() throws Exception {
        String provider = providerWithBusiness();
        String offerId = createOffer(provider, "Ceviche", "20.00", true);

        createCoupon(provider, offerId, couponJson("Mucho", "PERCENT", "150", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_DISCOUNT"));
        createCoupon(provider, offerId, couponJson("Regalado", "FIXED", "25", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_DISCOUNT"));
        createCoupon(provider, offerId, couponJson("Eterno", "FIXED", "5", null, inDays(30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_PERIOD"));
        createCoupon(provider, offerId, couponJson("", "FIXED", "5", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void rolesAndOwnershipAreEnforced() throws Exception {
        String owner = providerWithBusiness();
        String other = providerWithBusiness();
        String consumer = signUp("CONSUMER", "Ana Torres");
        String offerId = createOffer(owner, "Mío", "10.00", true);
        String couponId = field(createCoupon(owner, offerId, couponJson("Cupón", "FIXED", "2", null, null)), "$.id");

        createCoupon(other, offerId, couponJson("Ajeno", "FIXED", "2", null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        createCoupon(consumer, offerId, couponJson("Consumidor", "FIXED", "2", null, null))
                .andExpect(status().isForbidden());
        claim(owner, couponId).andExpect(status().isForbidden());
    }

    @Test
    void perUserLimitAndStockAreRespected() throws Exception {
        String provider = providerWithBusiness();
        String offerId = createOffer(provider, "Keke", "15.00", true);
        String once = field(createCoupon(provider, offerId, couponJson("Uno por persona", "FIXED", "3", 10, null)),
                "$.id");
        String single = field(createCoupon(provider, offerId, couponJson("Solo uno", "FIXED", "3", 1, null)), "$.id");
        String ana = signUp("CONSUMER", "Ana Torres");
        String luis = signUp("CONSUMER", "Luis Pérez");

        claim(ana, once).andExpect(status().isCreated());
        claim(ana, once).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_LIMIT_REACHED"));

        claim(ana, single).andExpect(status().isCreated());
        claim(luis, single).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_SOLD_OUT"));

        mockMvc.perform(get("/api/v1/offers/" + offerId + "/coupons").header("Authorization", bearer(luis)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(once));
    }

    @Test
    void concurrentClaimsNeverExceedTheStock() throws Exception {
        String provider = providerWithBusiness();
        String offerId = createOffer(provider, "Remate", "8.00", true);
        String couponId = field(createCoupon(provider, offerId, couponJson("Últimos 3", "FIXED", "2", 3, null)), "$.id");
        List<String> consumers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            consumers.add(signUp("CONSUMER", "Cliente " + i));
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(consumers.size())) {
            List<Future<Integer>> results = new ArrayList<>();
            for (String consumer : consumers) {
                Callable<Integer> attempt = () -> claim(consumer, couponId).andReturn().getResponse().getStatus();
                results.add(pool.submit(attempt));
            }
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(3);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(5);
        Integer claimed = jdbc.sql("SELECT claimed_count FROM coupons WHERE id = CAST(:id AS uuid)")
                .param("id", couponId).query(Integer.class).single();
        assertThat(claimed).isEqualTo(3);
    }

    @Test
    void couponsOfAPausedOfferCannotBeClaimed() throws Exception {
        String provider = providerWithBusiness();
        String offerId = createOffer(provider, "Pausada", "10.00", true);
        String couponId = field(createCoupon(provider, offerId, couponJson("Cupón", "FIXED", "2", null, null)), "$.id");
        String consumer = signUp("CONSUMER", "Ana Torres");

        mockMvc.perform(put("/api/v1/businesses/me/offers/" + offerId).header("Authorization", bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON).content(offerJson("Pausada", "10.00", false)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/offers/" + offerId + "/coupons").header("Authorization", bearer(consumer)))
                .andExpect(status().isNotFound());
        claim(consumer, couponId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_UNAVAILABLE"));
    }

    // --- redemption ---

    @Test
    void theBusinessRedeemsACodeOnlyOnce() throws Exception {
        String provider = providerWithBusiness();
        String other = providerWithBusiness();
        String offerId = createOffer(provider, "Pan francés 2x1", "10.00", true);
        String couponId = field(createCoupon(provider, offerId, couponJson("S/ 2 menos", "FIXED", "2", null, null)),
                "$.id");
        String consumer = signUp("CONSUMER", "Ana Torres");
        String code = field(claim(consumer, couponId), "$.code");
        // What a cashier might type: lowercase, with a dash and spaces.
        String typed = " " + code.substring(0, 4).toLowerCase() + "-" + code.substring(4).toLowerCase() + " ";

        redeem(other, code).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_CODE_NOT_FOUND"));

        String userCouponId = field(mockMvc.perform(get("/api/v1/users/me/coupons")
                .header("Authorization", bearer(consumer))), "$[0].id");
        redeem(provider, typed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.customerFirstName").value("Ana"))
                .andExpect(jsonPath("$.offerTitle").value("Pan francés 2x1"));
        redeem(provider, code).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_REDEEMED"));

        mockMvc.perform(get("/api/v1/users/me/coupons").header("Authorization", bearer(consumer)))
                .andExpect(jsonPath("$[0].status").value("REDEEMED"))
                .andExpect(jsonPath("$[0].redeemedAt").exists());
        Long audits = jdbc.sql("""
                        SELECT count(*) FROM audit_log
                        WHERE action = 'COUPON_REDEEMED' AND entity_id = :id AND metadata ->> 'offerId' = :offerId
                        """)
                .param("id", userCouponId).param("offerId", offerId).query(Long.class).single();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void unknownCodesAreRejected() throws Exception {
        String provider = providerWithBusiness();

        redeem(provider, "ZZZZZZZZ").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_CODE_NOT_FOUND"));
        redeem(provider, "").andExpect(status().isBadRequest());
    }

    // --- stats ---

    @Test
    void statsCountClaimsAndRedemptions() throws Exception {
        String provider = providerWithBusiness();
        String bread = createOffer(provider, "Pan francés 2x1", "10.00", true);
        String cake = createOffer(provider, "Keke", "15.00", true);
        createOffer(provider, "Pausada", "5.00", false);
        String breadCoupon = field(createCoupon(provider, bread, couponJson("Pan", "FIXED", "1", null, null)), "$.id");
        String cakeCoupon = field(createCoupon(provider, cake, couponJson("Keke", "FIXED", "1", null, null)), "$.id");

        String first = field(claim(signUp("CONSUMER", "Ana Torres"), breadCoupon), "$.code");
        String second = field(claim(signUp("CONSUMER", "Luis Pérez"), breadCoupon), "$.code");
        claim(signUp("CONSUMER", "Rosa Díaz"), cakeCoupon);
        redeem(provider, first).andExpect(status().isOk());
        redeem(provider, second).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/businesses/me/stats").header("Authorization", bearer(provider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOffers").value(3))
                .andExpect(jsonPath("$.liveOffers").value(2))
                .andExpect(jsonPath("$.couponsClaimed").value(3))
                .andExpect(jsonPath("$.couponsRedeemed").value(2))
                .andExpect(jsonPath("$.redemptionRate").value(67))
                .andExpect(jsonPath("$.daily", hasSize(7)))
                .andExpect(jsonPath("$.daily[6].claimed").value(3))
                .andExpect(jsonPath("$.daily[6].redeemed").value(2))
                .andExpect(jsonPath("$.topOffers", hasSize(2)))
                .andExpect(jsonPath("$.topOffers[0].title").value("Pan francés 2x1"));

        mockMvc.perform(get("/api/v1/businesses/me/coupons").header("Authorization", bearer(provider)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.title == 'Pan')].redeemedCount").value(2));
    }

    @Test
    void aNewBusinessHasEmptyStats() throws Exception {
        String provider = providerWithBusiness();

        mockMvc.perform(get("/api/v1/businesses/me/stats").header("Authorization", bearer(provider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponsClaimed").value(0))
                .andExpect(jsonPath("$.redemptionRate").value(0))
                .andExpect(jsonPath("$.daily", hasSize(7)))
                .andExpect(jsonPath("$.topOffers", hasSize(0)));
    }

    // --- helpers ---

    private String signUp(String role, String fullName) throws Exception {
        return field(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s@example.com", "password": "Secreta123", "fullName": "%s", "role": "%s"}
                """.formatted(UUID.randomUUID(), fullName, role))).andExpect(status().isCreated()), "$.accessToken");
    }

    private String providerWithBusiness() throws Exception {
        String provider = signUp("PROVIDER", "Bodega Lucho");
        mockMvc.perform(put("/api/v1/businesses/me").header("Authorization", bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "Negocio %s", "address": "Av. Siempre Viva 123",
                                 "latitude": -12.0464, "longitude": -77.0428}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isOk());
        return provider;
    }

    private String createOffer(String provider, String title, String price, boolean active) throws Exception {
        return field(mockMvc.perform(post("/api/v1/businesses/me/offers").header("Authorization", bearer(provider))
                        .contentType(MediaType.APPLICATION_JSON).content(offerJson(title, price, active)))
                .andExpect(status().isCreated()), "$.id");
    }

    private static String offerJson(String title, String price, boolean active) {
        return """
                {"title": "%s", "categorySlug": "bakery", "offerPrice": %s, "endsAt": "%s", "active": %s}
                """.formatted(title, price, inDays(7), active);
    }

    private static String couponJson(String title, String type, String value, Integer stock, Instant validUntil) {
        return """
                {"title": "%s", "discountType": "%s", "discountValue": %s, "stock": %s, "validUntil": %s}
                """.formatted(title, type, value, stock, validUntil == null ? "null" : "\"" + validUntil + "\"");
    }

    private ResultActions createCoupon(String provider, String offerId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/businesses/me/offers/" + offerId + "/coupons")
                .header("Authorization", bearer(provider)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions claim(String consumer, String couponId) throws Exception {
        return mockMvc.perform(post("/api/v1/coupons/" + couponId + "/claim").header("Authorization", bearer(consumer)));
    }

    private ResultActions redeem(String provider, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/businesses/me/redemptions").header("Authorization", bearer(provider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"%s\"}".formatted(code)));
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
