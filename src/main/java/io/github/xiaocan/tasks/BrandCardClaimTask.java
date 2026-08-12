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

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void claimBrandCard() {
        log.debug("brand card claim scheduler tick");
        brandCardClaimService.runScheduledClaims();
    }
}
