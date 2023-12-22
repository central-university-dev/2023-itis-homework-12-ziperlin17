package ru.shop.backend.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SearchApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext cont = SpringApplication.run(SearchApplication.class, args);
    }
}