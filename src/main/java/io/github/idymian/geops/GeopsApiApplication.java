package io.github.idymian.geops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GeopsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeopsApiApplication.class, args);
    }
}
