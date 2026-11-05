package io.github.idymian.geops.offers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Offer category. Reference data seeded by Flyway; read-only for the application.
 */
@Entity
@Immutable
@Table(name = "categories")
public class Category {

    @Id
    private Short id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    protected Category() {
        // for JPA
    }

    public Short getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
