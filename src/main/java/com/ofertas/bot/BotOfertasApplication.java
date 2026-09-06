package com.ofertas.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BotOfertasApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotOfertasApplication.class, args);
    }
}