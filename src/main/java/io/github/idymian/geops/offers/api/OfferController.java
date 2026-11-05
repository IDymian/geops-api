package io.github.idymian.geops.offers.api;

import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.offers.application.CatalogService;
import io.github.idymian.geops.offers.application.OfferService;
import io.github.idymian.geops.offers.application.dto.OfferDtos.CategoryResponse;
import io.github.idymian.geops.offers.application.dto.OfferDtos.NearbyOfferResponse;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer discovery for signed-in users.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Offers", description = "Discover offers near you")
@SecurityRequirement(name = "bearerAuth")
public class OfferController {

    private final OfferService offerService;
    private final CatalogService catalogService;

    public OfferController(OfferService offerService, CatalogService catalogService) {
        this.offerService = offerService;
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    @Operation(summary = "Offer categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories();
    }

    @GetMapping("/offers/nearby")
    @Operation(summary = "Live offers within a radius, closest first")
    public List<NearbyOfferResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") @Parameter(example = "-12.0464") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") @Parameter(example = "-77.0428") double lng,
            @RequestParam(defaultValue = "2000")
            @Min(OfferService.MIN_RADIUS_METERS) @Max(OfferService.MAX_RADIUS_METERS)
            @Parameter(description = "Meters (100 to 20000)") int radius,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z-]{1,40}$")
            @Parameter(description = "Category slug", example = "bakery") String category,
            @RequestParam(defaultValue = "50") @Min(1) @Max(OfferService.MAX_RESULTS) int limit) {
        return offerService.nearby(lat, lng, radius, category, limit);
    }

    @GetMapping("/offers/{offerId}")
    @Operation(summary = "A live offer with its business")
    public OfferDetailResponse get(@PathVariable UUID offerId) {
        return offerService.getLive(offerId);
    }
}
