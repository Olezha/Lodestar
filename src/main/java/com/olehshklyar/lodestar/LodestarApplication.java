package com.olehshklyar.lodestar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LodestarApplication {

    public static void main(String[] args) {
        SpringApplication.run(LodestarApplication.class, args);
    }
}
