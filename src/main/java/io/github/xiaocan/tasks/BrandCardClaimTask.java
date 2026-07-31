package io.github.xiaocan.tasks;

import io.github.xiaocan.service.BrandCardClaimService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BrandCardClaimTask {
    @Resource
    private BrandCardClaimService brandCardClaimService;

    @Scheduled(cron = "58 29 9 * * ?", zone = "Asia/Shanghai")
    public void claimBrandCard() {
        log.info("brand card claim preparation started");
        brandCardClaimService.runScheduledClaims();
    }
}
