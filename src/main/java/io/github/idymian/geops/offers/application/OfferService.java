package io.github.idymian.geops.offers.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.offers.application.dto.OfferDtos.NearbyOfferResponse;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferDetailResponse;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferRequest;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferResponse;
import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.offers.domain.Category;
import io.github.idymian.geops.offers.domain.Offer;
import io.github.idymian.geops.offers.infrastructure.CategoryRepository;
import io.github.idymian.geops.offers.infrastructure.NearbyOfferQuery;
import io.github.idymian.geops.offers.infrastructure.OfferRepository;
import io.github.idymian.geops.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offers: providers manage their own, consumers search live ones nearby.
 */
@Service
public class OfferService {

    public static final int MIN_RADIUS_METERS = 100;
    public static final int MAX_RADIUS_METERS = 20_000;
    public static final int MAX_RESULTS = 100;

    private final OfferRepository offers;
    private final CategoryRepository categories;
    private final BusinessService businessService;
    private final NearbyOfferQuery nearbyQuery;
    private final Clock clock;

    public OfferService(OfferRepository offers, CategoryRepository categories, BusinessService businessService,
                        NearbyOfferQuery nearbyQuery, Clock clock) {
        this.offers = offers;
        this.categories = categories;
        this.businessService = businessService;
        this.nearbyQuery = nearbyQuery;
        this.clock = clock;
    }

    // --- provider ---

    @Transactional(readOnly = true)
    public List<OfferResponse> listMine(UUID ownerId) {
        Business business = businessService.requireBusiness(ownerId);
        Instant now = clock.instant();
        return offers.findByBusinessIdOrderByCreatedAtDesc(business.getId()).stream()
                .map(offer -> OfferResponse.from(offer, now))
                .toList();
    }

    @Transactional
    public OfferResponse create(UUID ownerId, OfferRequest request) {
        Business business = businessService.requireBusiness(ownerId);
        Offer offer = new Offer(business);
        apply(offer, request);
        return OfferResponse.from(offers.save(offer), clock.instant());
    }

    @Transactional
    public OfferResponse update(UUID ownerId, UUID offerId, OfferRequest request) {
        Business business = businessService.requireBusiness(ownerId);
        Offer offer = offers.findWithDetailsById(offerId)
                // Someone else's offer answers exactly like a missing one: ids are not confirmed.
                .filter(found -> found.getBusiness().getId().equals(business.getId()))
                .orElseThrow(OfferService::offerNotFound);
        apply(offer, request);
        return OfferResponse.from(offer, clock.instant());
    }

    private void apply(Offer offer, OfferRequest request) {
        Category category = categories.findBySlug(request.categorySlug()).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND", "Unknown category."));
        Instant startsAt = request.startsAt() != null ? request.startsAt() : clock.instant();
        if (!request.endsAt().isAfter(startsAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER_INVALID_PERIOD", "The offer must end after it starts.");
        }
        if (request.originalPrice() != null && request.originalPrice().compareTo(request.offerPrice()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER_INVALID_PRICE",
                    "The offer price cannot be higher than the original price.");
        }
        offer.update(category, request.title(), request.description(), request.imageUrl(), request.originalPrice(),
                request.offerPrice(), startsAt, request.endsAt(), request.active() == null || request.active());
    }

    // --- consumer ---

    @Transactional(readOnly = true)
    public List<NearbyOfferResponse> nearby(double latitude, double longitude, int radiusMeters, String category,
                                            int limit) {
        return nearbyQuery.find(latitude, longitude, radiusMeters, category, limit, clock.instant());
    }

    /** Only live offers are visible to consumers. */
    @Transactional(readOnly = true)
    public OfferDetailResponse getLive(UUID offerId) {
        return offers.findWithDetailsById(offerId)
                .filter(offer -> offer.isLive(clock.instant()))
                .map(OfferDetailResponse::from)
                .orElseThrow(OfferService::offerNotFound);
    }

    private static ApiException offerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offer not found.");
    }
}
