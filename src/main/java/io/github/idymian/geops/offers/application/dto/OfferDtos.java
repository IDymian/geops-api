package io.github.idymian.geops.offers.application.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.offers.domain.Category;
import io.github.idymian.geops.offers.domain.Offer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies of the offers context.
 */
public final class OfferDtos {

    private OfferDtos() {
    }

    // --- categories ---

    /** Serializable because it is cached in Redis. */
    public record CategoryResponse(short id, String slug, String name) implements Serializable {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getSlug(), category.getName());
        }
    }

    // --- businesses ---

    public record BusinessRequest(
            @NotBlank @Size(max = 120) @Schema(example = "Panadería San Martín") String name,
            @Size(max = 1000) @Schema(example = "Pan artesanal y pasteles desde 1998") String description,
            @Size(max = 20) @Pattern(regexp = "^$|^[0-9+ ()-]{6,20}$", message = "must be a valid phone number")
            @Schema(example = "+51 987 654 321") String phone,
            @NotBlank @Size(max = 200) @Schema(example = "Jr. de la Unión 456, Cercado de Lima") String address,
            @NotNull @DecimalMin("-90") @DecimalMax("90") @Schema(example = "-12.0464") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") @Schema(example = "-77.0428") Double longitude) {
    }

    public record BusinessResponse(
            UUID id, String name, String description, String phone, String address,
            double latitude, double longitude, String logoUrl) {

        public static BusinessResponse from(Business business) {
            return new BusinessResponse(business.getId(), business.getName(), business.getDescription(),
                    business.getPhone(), business.getAddress(), business.getLatitude(), business.getLongitude(),
                    business.getLogoUrl());
        }
    }

    /** What consumers see about the business behind an offer. */
    public record BusinessSummary(
            UUID id, String name, String address, String phone, double latitude, double longitude) {

        public static BusinessSummary from(Business business) {
            return new BusinessSummary(business.getId(), business.getName(), business.getAddress(),
                    business.getPhone(), business.getLatitude(), business.getLongitude());
        }
    }

    // --- offers ---

    public record OfferRequest(
            @NotBlank @Size(min = 3, max = 120) @Schema(example = "2x1 en pan francés") String title,
            @Size(max = 1000) @Schema(example = "Válido de lunes a viernes") String description,
            @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https URL") String imageUrl,
            @NotBlank @Schema(example = "bakery") String categorySlug,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(example = "10.00") BigDecimal originalPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(example = "5.00") BigDecimal offerPrice,
            @Schema(description = "Defaults to now") Instant startsAt,
            @NotNull @Future Instant endsAt,
            @Schema(description = "Defaults to true") Boolean active) {
    }

    public record OfferResponse(
            UUID id, String title, String description, String imageUrl, CategoryResponse category,
            BigDecimal originalPrice, BigDecimal offerPrice, Integer discountPercent,
            Instant startsAt, Instant endsAt, boolean active, boolean live) {

        public static OfferResponse from(Offer offer, Instant now) {
            return new OfferResponse(offer.getId(), offer.getTitle(), offer.getDescription(), offer.getImageUrl(),
                    CategoryResponse.from(offer.getCategory()), offer.getOriginalPrice(), offer.getOfferPrice(),
                    OfferDtos.discountPercent(offer.getOriginalPrice(), offer.getOfferPrice()),
                    offer.getStartsAt(), offer.getEndsAt(), offer.isActive(), offer.isLive(now));
        }
    }

    public record OfferDetailResponse(
            UUID id, String title, String description, String imageUrl, CategoryResponse category,
            BigDecimal originalPrice, BigDecimal offerPrice, Integer discountPercent,
            Instant startsAt, Instant endsAt, BusinessSummary business) {

        public static OfferDetailResponse from(Offer offer) {
            return new OfferDetailResponse(offer.getId(), offer.getTitle(), offer.getDescription(),
                    offer.getImageUrl(), CategoryResponse.from(offer.getCategory()), offer.getOriginalPrice(),
                    offer.getOfferPrice(), OfferDtos.discountPercent(offer.getOriginalPrice(), offer.getOfferPrice()),
                    offer.getStartsAt(), offer.getEndsAt(), BusinessSummary.from(offer.getBusiness()));
        }
    }

    /** One result of a radius search, closest first. */
    public record NearbyOfferResponse(
            UUID id, String title, String imageUrl, String categorySlug,
            BigDecimal originalPrice, BigDecimal offerPrice, Integer discountPercent, Instant endsAt,
            BusinessSummary business, @Schema(description = "Meters from the search point") int distanceMeters) {
    }

    /** Whole-number discount, or null when there is no original price to compare. */
    public static Integer discountPercent(BigDecimal originalPrice, BigDecimal offerPrice) {
        if (originalPrice == null || originalPrice.signum() <= 0) {
            return null;
        }
        return originalPrice.subtract(offerPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
