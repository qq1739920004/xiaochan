package io.github.xiaocan.model;

public record BrandCardClaimExecutionResult(
        int attempts,
        boolean success,
        Integer resultCode,
        String resultMessage,
        BrandCardClaimStopReason stopReason
) {
    public static BrandCardClaimExecutionResult fromAttempt(int attempts, BrandCardClaimAttemptResult attempt) {
        return new BrandCardClaimExecutionResult(
                attempts,
                attempt.stopReason() == BrandCardClaimStopReason.SUCCESS,
                attempt.code(),
                attempt.message(),
                attempt.stopReason()
        );
    }
}
