package io.github.idymian.geops.offers.infrastructure;

import java.util.Optional;
import java.util.UUID;

import io.github.idymian.geops.offers.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findByOwnerId(UUID ownerId);
}
