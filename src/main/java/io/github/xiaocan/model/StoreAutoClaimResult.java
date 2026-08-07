package io.github.xiaocan.model;

public record StoreAutoClaimResult(
        int attempts,
        boolean success,
        Integer resultCode,
        String resultMessage,
        Long promotionOrderId,
        StoreAutoClaimStopReason stopReason
) {
    public static StoreAutoClaimResult fromAttempt(int attempts, StoreAutoClaimAttempt attempt) {
        return new StoreAutoClaimResult(
                attempts,
                attempt.stopReason() == StoreAutoClaimStopReason.SUCCESS,
                attempt.code(),
                attempt.message(),
                attempt.promotionOrderId(),
                attempt.stopReason()
        );
    }
}
