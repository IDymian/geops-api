package io.github.idymian.geops.coupons.api;

import java.util.List;
import java.util.UUID;

import io.github.idymian.geops.coupons.application.CouponService;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.AvailableCouponResponse;
import io.github.idymian.geops.coupons.application.dto.CouponDtos.UserCouponResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer side of coupons: see what an offer gives, claim a personal code, list my codes.
 */
@RestController
@Tag(name = "Coupons")
@SecurityRequirement(name = "bearerAuth")
public class ConsumerCouponController {

    private final CouponService couponService;

    public ConsumerCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/api/v1/offers/{offerId}/coupons")
    @Operation(summary = "Coupons of a live offer that can be claimed now")
    @ApiResponse(responseCode = "404", description = "OFFER_NOT_FOUND")
    public List<AvailableCouponResponse> available(@PathVariable UUID offerId) {
        return couponService.available(offerId);
    }

    @PostMapping("/api/v1/coupons/{couponId}/claim")
    @PreAuthorize("hasRole('CONSUMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Claim a coupon and get a personal code")
    @ApiResponse(responseCode = "404", description = "COUPON_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "COUPON_UNAVAILABLE, COUPON_SOLD_OUT, COUPON_LIMIT_REACHED")
    public UserCouponResponse claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID couponId) {
        return couponService.claim(UUID.fromString(jwt.getSubject()), couponId);
    }

    @GetMapping("/api/v1/users/me/coupons")
    @PreAuthorize("hasRole('CONSUMER')")
    @Operation(summary = "My coupon codes, newest first (CLAIMED, REDEEMED or EXPIRED)")
    public List<UserCouponResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return couponService.mine(UUID.fromString(jwt.getSubject()));
    }
}
