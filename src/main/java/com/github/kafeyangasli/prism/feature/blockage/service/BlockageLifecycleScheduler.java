package com.github.kafeyangasli.prism.feature.blockage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class BlockageLifecycleScheduler {

    private final BlockageService blockageService;

    @Scheduled(fixedRate = 60000)
    public void reconcileBlockageLifecycle() {
        try {
            int count = blockageService.reconcileBlockageLifecycle();
            if (count > 0) {
                log.info("Reconciled {} blockage lifecycle status transitions", count);
            }
        } catch (Exception e) {
            log.error("Error during blockage lifecycle reconciliation", e);
        }
    }
}
