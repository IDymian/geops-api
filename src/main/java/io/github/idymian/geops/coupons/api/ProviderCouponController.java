package io.github.idymian.geops.coupons.api;

import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.coupons.application.CouponService;
import io.github.idymian.geops.coupons.application.RedemptionService;
import io.github.idymian.geops.coupons.application.StatsService;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.CouponRequest;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.CouponResponse;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.RedeemRequest;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.RedemptionResponse;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.StatsResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business panel, coupons side: issue coupons, redeem codes at the counter, see the numbers.
 */
@RestController
@RequestMapping("/api/v1/businesses/me")
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Business panel")
@SecurityRequirement(name = "bearerAuth")
public class ProviderCouponController {

    private final CouponService couponService;
    private final RedemptionService redemptionService;
    private final StatsService statsService;

    public ProviderCouponController(CouponService couponService, RedemptionService redemptionService,
                                    StatsService statsService) {
        this.couponService = couponService;
        this.redemptionService = redemptionService;
        this.statsService = statsService;
    }

    @PostMapping("/offers/{offerId}/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue a coupon for one of my offers")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, COUPON_INVALID_PERIOD, COUPON_INVALID_DISCOUNT")
    @ApiResponse(responseCode = "404", description = "BUSINESS_NOT_FOUND, OFFER_NOT_FOUND")
    public CouponResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId,
                                 @Valid @RequestBody CouponRequest request) {
        return couponService.create(userId(jwt), offerId, request);
    }

    @GetMapping("/coupons")
    @Operation(summary = "All my coupons with claimed and redeemed counts, newest first")
    public List<CouponResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return couponService.listMine(userId(jwt));
    }

    @PostMapping("/redemptions")
    @Operation(summary = "Redeem a customer's code at the counter")
    @ApiResponse(responseCode = "404", description = "COUPON_CODE_NOT_FOUND (unknown or another business's code)")
    @ApiResponse(responseCode = "409", description = "COUPON_ALREADY_REDEEMED, COUPON_EXPIRED")
    public RedemptionResponse redeem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RedeemRequest request) {
        return redemptionService.redeem(userId(jwt), request.code());
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard: offers, claims, redemptions, last 7 days and top offers")
    public StatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return statsService.forBusiness(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
