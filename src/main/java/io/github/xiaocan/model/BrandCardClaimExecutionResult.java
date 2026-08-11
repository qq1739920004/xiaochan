package io.github.xiaocan.model;

import java.time.Instant;

public record BrandCardClaimExecutionResult(
        int attempts,
        boolean success,
        Integer resultCode,
        String resultMessage,
        BrandCardClaimStopReason stopReason,
        Instant firstAttemptAt
) {
    public static BrandCardClaimExecutionResult fromAttempt(int attempts, BrandCardClaimAttemptResult attempt) {
        return fromAttempt(attempts, attempt, null);
    }

    public static BrandCardClaimExecutionResult fromAttempt(int attempts, BrandCardClaimAttemptResult attempt,
                                                             Instant firstAttemptAt) {
        return new BrandCardClaimExecutionResult(
                attempts,
                attempt.stopReason() == BrandCardClaimStopReason.SUCCESS,
                attempt.code(),
                attempt.message(),
                attempt.stopReason(),
                firstAttemptAt
        );
    }
}
