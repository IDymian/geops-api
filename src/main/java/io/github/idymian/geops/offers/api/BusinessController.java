package io.github.idymian.geops.offers.api;

import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.offers.application.BusinessService;
import io.github.idymian.geops.offers.application.OfferService;
import io.github.idymian.geops.offers.application.dto.OfferDtos.BusinessRequest;
import io.github.idymian.geops.offers.application.dto.OfferDtos.BusinessResponse;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferRequest;
import io.github.idymian.geops.offers.application.dto.OfferDtos.OfferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business panel: the signed-in provider manages their business and its offers.
 * Everything is scoped to "me", so a provider can never address someone else's data.
 */
@RestController
@RequestMapping("/api/v1/businesses/me")
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Business panel", description = "PROVIDER only")
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

    private final BusinessService businessService;
    private final OfferService offerService;

    public BusinessController(BusinessService businessService, OfferService offerService) {
        this.businessService = businessService;
        this.offerService = offerService;
    }

    @GetMapping
    @Operation(summary = "My business profile")
    @ApiResponse(responseCode = "404", description = "BUSINESS_NOT_FOUND")
    public BusinessResponse get(@AuthenticationPrincipal Jwt jwt) {
        return businessService.getMine(userId(jwt));
    }

    @PutMapping
    @Operation(summary = "Create or update my business profile and location")
    public BusinessResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessRequest request) {
        return businessService.saveMine(userId(jwt), request);
    }

    @GetMapping("/offers")
    @Operation(summary = "All my offers, newest first (including inactive and expired)")
    public List<OfferResponse> listOffers(@AuthenticationPrincipal Jwt jwt) {
        return offerService.listMine(userId(jwt));
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish an offer")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, CATEGORY_NOT_FOUND, OFFER_INVALID_PERIOD, OFFER_INVALID_PRICE")
    @ApiResponse(responseCode = "404", description = "BUSINESS_NOT_FOUND")
    public OfferResponse createOffer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OfferRequest request) {
        return offerService.create(userId(jwt), request);
    }

    @PutMapping("/offers/{offerId}")
    @Operation(summary = "Edit, pause (active=false) or reactivate one of my offers")
    @ApiResponse(responseCode = "404", description = "OFFER_NOT_FOUND")
    public OfferResponse updateOffer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId,
                                     @Valid @RequestBody OfferRequest request) {
        return offerService.update(userId(jwt), offerId, request);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
