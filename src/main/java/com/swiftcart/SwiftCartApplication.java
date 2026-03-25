package com.swiftcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SwiftCart — Production-quality e-commerce backend.
 *
 * @EnableJpaAuditing   — powers createdAt/updatedAt in BaseEntity
 * @EnableAsync         — allows @Async on event listeners
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableConfigurationProperties
public class SwiftCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwiftCartApplication.class, args);
    }
}
