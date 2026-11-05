package io.github.idymian.geops.offers.application;

import java.util.List;

import io.github.idymian.geops.offers.application.dto.OfferDtos.CategoryResponse;
import io.github.idymian.geops.offers.infrastructure.CategoryRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category list: requested on every app start and almost never changes,
 * so it is served from the Redis cache.
 */
@Service
public class CatalogService {

    public static final String CATEGORIES_CACHE = "categories";

    private final CategoryRepository categories;

    public CatalogService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Cacheable(CATEGORIES_CACHE)
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderByIdAsc().stream().map(CategoryResponse::from).toList();
    }
}
