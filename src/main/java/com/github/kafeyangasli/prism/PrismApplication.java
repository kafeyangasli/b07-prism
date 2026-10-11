package com.github.kafeyangasli.prism;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.github.kafeyangasli.prism.feature.reservation.service.ReservationLifecycleService;

@SpringBootApplication
@EnableScheduling
public class PrismApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrismApplication.class, args);
    }

    @Bean
    public ApplicationRunner reconcileOnStartup(ReservationLifecycleService lifecycleService) {
        return args -> lifecycleService.processLifecycle();
    }
    
}