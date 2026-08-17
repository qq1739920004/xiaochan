package io.github.xiaocan.model;

import java.time.Instant;

public record BrandCardClaimAttemptEvent(
        int sequence,
        Instant requestTime,
        Instant responseTime,
        BrandCardClaimAttemptResult result
) {
}
