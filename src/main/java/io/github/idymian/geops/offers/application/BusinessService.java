package io.github.idymian.geops.offers.application;

import java.util.UUID;

import io.github.idymian.geops.offers.application.dto.OfferDtos.BusinessRequest;
import io.github.idymian.geops.offers.application.dto.OfferDtos.BusinessResponse;
import io.github.idymian.geops.offers.domain.Business;
import io.github.idymian.geops.offers.infrastructure.BusinessRepository;
import io.github.idymian.geops.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in provider's own business profile.
 */
@Service
public class BusinessService {

    private final BusinessRepository businesses;

    public BusinessService(BusinessRepository businesses) {
        this.businesses = businesses;
    }

    @Transactional(readOnly = true)
    public BusinessResponse getMine(UUID ownerId) {
        return BusinessResponse.from(requireBusiness(ownerId));
    }

    /** Creates the business on first save, updates it afterwards (PUT is idempotent). */
    @Transactional
    public BusinessResponse saveMine(UUID ownerId, BusinessRequest request) {
        Business business = businesses.findByOwnerId(ownerId).orElseGet(() -> new Business(ownerId));
        business.updateProfile(request.name(), request.description(), request.phone(), request.address(),
                request.latitude(), request.longitude());
        return BusinessResponse.from(businesses.save(business));
    }

    Business requireBusiness(UUID ownerId) {
        return businesses.findByOwnerId(ownerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "BUSINESS_NOT_FOUND", "Create your business profile first."));
    }
}
