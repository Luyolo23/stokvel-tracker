package com.stokveltracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StokvelTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(StokvelTrackerApplication.class, args);
    }
}
