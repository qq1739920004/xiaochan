package io.github.xiaocan.service;

import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimRequest;

@FunctionalInterface
public interface StoreAutoClaimClient {
    StoreAutoClaimAttempt claim(StoreAutoClaimRequest request);

    default Long findAvailableRedpackId(StoreAutoClaimRequest request) {
        return null;
    }
}
