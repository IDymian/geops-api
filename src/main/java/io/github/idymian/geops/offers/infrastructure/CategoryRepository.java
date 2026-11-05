package io.github.idymian.geops.offers.infrastructure;

import java.util.List;
import java.util.Optional;

import io.github.idymian.geops.offers.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Short> {

    Optional<Category> findBySlug(String slug);

    List<Category> findAllByOrderByIdAsc();
}
