package com.olehshklyar.lodestar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class LodestarApplication {

    public static void main(String[] args) {
        SpringApplication.run(LodestarApplication.class, args);
    }
}
