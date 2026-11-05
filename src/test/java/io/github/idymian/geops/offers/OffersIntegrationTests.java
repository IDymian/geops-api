package io.github.idymian.geops.offers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import io.github.idymian.geops.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class OffersIntegrationTests extends AbstractIntegrationTest {

    /** About 1 km in degrees of latitude. */
    private static final double KM = 1.0 / 111.32;

    @Autowired
    MockMvc mockMvc;

    // --- categories ---

    @Test
    void categoriesAreSeeded() throws Exception {
        String consumer = signUp("CONSUMER");

        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].slug").value("food"));
    }

    // --- business panel ---

    @Test
    void onlyProvidersCanUseTheBusinessPanel() throws Exception {
        String consumer = signUp("CONSUMER");

        mockMvc.perform(get("/api/v1/businesses/me").header("Authorization", bearer(consumer)))
                .andExpect(status().isForbidden());
        saveBusiness(consumer, "Intruso", 0, 0).andExpect(status().isForbidden());
    }

    @Test
    void providerMustCreateTheBusinessBeforePublishing() throws Exception {
        String provider = signUp("PROVIDER");

        mockMvc.perform(get("/api/v1/businesses/me").header("Authorization", bearer(provider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));
        createOffer(provider, offerJson("Pan 2x1", "bakery", "10.00", "5.00", null, inDays(7)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));
    }

    @Test
    void savingTheBusinessTwiceUpdatesTheSameProfile() throws Exception {
        String provider = signUp("PROVIDER");

        String firstId = field(saveBusiness(provider, "Bodega Lucho", -12.0464, -77.0428)
                .andExpect(status().isOk()), "$.id");
        String secondId = field(saveBusiness(provider, "Bodega Lucho e Hijos", -12.05, -77.04)
                .andExpect(status().isOk()), "$.id");

        assertThat(secondId).isEqualTo(firstId);
        mockMvc.perform(get("/api/v1/businesses/me").header("Authorization", bearer(provider)))
                .andExpect(jsonPath("$.name").value("Bodega Lucho e Hijos"))
                .andExpect(jsonPath("$.latitude").value(-12.05));
    }

    @Test
    void businessCoordinatesAreValidated() throws Exception {
        String provider = signUp("PROVIDER");

        saveBusiness(provider, "Lejos", 200, -77)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    // --- offers ---

    @Test
    void providerPublishesAndListsOffers() throws Exception {
        String provider = providerWithBusinessAt(-12.0464, -77.0428);

        createOffer(provider, offerJson("Pan francés 2x1", "bakery", "10.00", "5.00", null, inDays(7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercent").value(50))
                .andExpect(jsonPath("$.category.slug").value("bakery"))
                .andExpect(jsonPath("$.live").value(true));

        mockMvc.perform(get("/api/v1/businesses/me/offers").header("Authorization", bearer(provider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void offerRulesAreEnforced() throws Exception {
        String provider = providerWithBusinessAt(-12.0464, -77.0428);

        createOffer(provider, offerJson("Ya pasó", "food", null, "5.00", null, Instant.now().minus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endsAt").exists());
        createOffer(provider, offerJson("Al revés", "food", null, "5.00", inDays(10), inDays(5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OFFER_INVALID_PERIOD"));
        createOffer(provider, offerJson("Más caro", "food", "5.00", "8.00", null, inDays(5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OFFER_INVALID_PRICE"));
        createOffer(provider, offerJson("Sin categoría", "unknown", null, "5.00", null, inDays(5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void providersCannotEditEachOthersOffers() throws Exception {
        String owner = providerWithBusinessAt(-12.0464, -77.0428);
        String other = providerWithBusinessAt(-12.0500, -77.0400);
        String offerId = field(createOffer(owner, offerJson("Mío", "food", null, "5.00", null, inDays(5))), "$.id");

        mockMvc.perform(put("/api/v1/businesses/me/offers/" + offerId).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerJson("Robado", "food", null, "1.00", null, inDays(5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    // --- discovery ---

    @Test
    void nearbyReturnsLiveOffersWithinTheRadiusClosestFirst() throws Exception {
        // A random spot per run, so offers from other tests never fall inside the radius.
        double lat = ThreadLocalRandom.current().nextDouble(-40, -20);
        double lng = ThreadLocalRandom.current().nextDouble(-75, -60);

        String here = providerWithBusinessAt(lat, lng);
        String oneAndHalfKm = providerWithBusinessAt(lat + 1.5 * KM, lng);
        String eightKm = providerWithBusinessAt(lat + 8 * KM, lng);

        String bakeryHere = field(createOffer(here, offerJson("Pan aquí", "bakery", null, "3.00", null, inDays(3))), "$.id");
        String foodNear = field(createOffer(oneAndHalfKm, offerJson("Menú cerca", "food", null, "12.00", null, inDays(3))), "$.id");
        String bakeryFar = field(createOffer(eightKm, offerJson("Pan lejos", "bakery", null, "3.00", null, inDays(3))), "$.id");
        // Not live: paused, and not started yet.
        createOffer(here, offerJson("Pausada", "bakery", null, "1.00", null, inDays(3)).replace("\"active\": true", "\"active\": false"));
        createOffer(here, offerJson("Mañana", "bakery", null, "1.00", inDays(1), inDays(3)));

        String consumer = signUp("CONSUMER");

        String body2Km = nearby(consumer, lat, lng, 2000, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> within2Km = JsonPath.read(body2Km, "$[*].id");
        List<Integer> distances = JsonPath.read(body2Km, "$[*].distanceMeters");
        assertThat(within2Km).containsExactly(bakeryHere, foodNear);
        assertThat(distances.get(0)).isBetween(0, 5);
        assertThat(distances.get(1)).isBetween(1450, 1550);

        List<String> within10Km = JsonPath.read(nearby(consumer, lat, lng, 10_000, null)
                .andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(within10Km).containsExactly(bakeryHere, foodNear, bakeryFar);

        List<String> bakeries = JsonPath.read(nearby(consumer, lat, lng, 10_000, "bakery")
                .andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(bakeries).containsExactly(bakeryHere, bakeryFar);
    }

    @Test
    void nearbyParametersAreValidated() throws Exception {
        String consumer = signUp("CONSUMER");

        nearby(consumer, -12.0, -77.0, 50, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.radius").exists());
        mockMvc.perform(get("/api/v1/offers/nearby?lat=abc&lng=-77").header("Authorization", bearer(consumer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void discoveryRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/offers/nearby?lat=-12&lng=-77")).andExpect(status().isUnauthorized());
    }

    @Test
    void offerDetailShowsOnlyLiveOffers() throws Exception {
        String provider = providerWithBusinessAt(-12.0464, -77.0428);
        String consumer = signUp("CONSUMER");
        String live = field(createOffer(provider, offerJson("Activa", "food", "20.00", "15.00", null, inDays(2))), "$.id");
        String paused = field(createOffer(provider,
                offerJson("Pausada", "food", null, "15.00", null, inDays(2)).replace("\"active\": true", "\"active\": false")), "$.id");

        mockMvc.perform(get("/api/v1/offers/" + live).header("Authorization", bearer(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountPercent").value(25))
                .andExpect(jsonPath("$.business.latitude").value(-12.0464));
        mockMvc.perform(get("/api/v1/offers/" + paused).header("Authorization", bearer(consumer)))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String signUp(String role) throws Exception {
        return field(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s@example.com", "password": "Secreta123", "fullName": "Test", "role": "%s"}
                """.formatted(UUID.randomUUID(), role))).andExpect(status().isCreated()), "$.accessToken");
    }

    private String providerWithBusinessAt(double lat, double lng) throws Exception {
        String provider = signUp("PROVIDER");
        saveBusiness(provider, "Negocio " + UUID.randomUUID().toString().substring(0, 8), lat, lng)
                .andExpect(status().isOk());
        return provider;
    }

    private ResultActions saveBusiness(String token, String name, double lat, double lng) throws Exception {
        return mockMvc.perform(put("/api/v1/businesses/me").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "address": "Av. Siempre Viva 123", "phone": "+51 999 888 777",
                         "latitude": %s, "longitude": %s}
                        """.formatted(name, lat, lng)));
    }

    private ResultActions createOffer(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/businesses/me/offers").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions nearby(String token, double lat, double lng, int radius, String category) throws Exception {
        var request = get("/api/v1/offers/nearby").header("Authorization", bearer(token))
                .param("lat", String.valueOf(lat)).param("lng", String.valueOf(lng))
                .param("radius", String.valueOf(radius));
        if (category != null) {
            request = request.param("category", category);
        }
        return mockMvc.perform(request);
    }

    private static String offerJson(String title, String category, String originalPrice, String offerPrice,
                                    Instant startsAt, Instant endsAt) {
        return """
                {"title": "%s", "categorySlug": "%s", "originalPrice": %s, "offerPrice": %s,
                 "startsAt": %s, "endsAt": "%s", "active": true}
                """.formatted(title, category, originalPrice, offerPrice,
                startsAt == null ? "null" : "\"" + startsAt + "\"", endsAt);
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
