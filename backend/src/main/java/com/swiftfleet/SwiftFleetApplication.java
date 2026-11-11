package com.swiftfleet;

import com.swiftfleet.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class SwiftFleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwiftFleetApplication.class, args);
    }
}

