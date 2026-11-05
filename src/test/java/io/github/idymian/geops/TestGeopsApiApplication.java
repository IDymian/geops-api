package io.github.idymian.geops;

import org.springframework.boot.SpringApplication;

/**
 * Runs the API locally against throwaway containers: {@code ./mvnw spring-boot:test-run}.
 */
public class TestGeopsApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(GeopsApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
