package io.github.idymian.geops.offers.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.idymian.geops.offers.domain.Offer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @EntityGraph(attributePaths = "category")
    List<Offer> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    @EntityGraph(attributePaths = {"category", "business"})
    Optional<Offer> findWithDetailsById(UUID id);
}
