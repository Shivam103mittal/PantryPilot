package com.pantrypilot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PantryPilotApplication implements CommandLineRunner {

    @Value("${spring.datasource.url:NOT_FOUND}")
    private String dbUrl;

    public static void main(String[] args) {
        SpringApplication.run(PantryPilotApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Database URL from properties: " + dbUrl);
    }
}
