package io.github.xiaocan.service;

import io.github.xiaocan.model.BrandCardClaimAttemptResult;

@FunctionalInterface
public interface BrandCardClaimClient {
    BrandCardClaimAttemptResult claim(Long silkId, String xSivir);

    default BrandCardClaimAttemptResult claim(Long silkId, String xSivir, Long xVayne) {
        return claim(silkId, xSivir);
    }
}
