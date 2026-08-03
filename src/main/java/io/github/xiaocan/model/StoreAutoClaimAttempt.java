package io.github.xiaocan.model;

public record StoreAutoClaimAttempt(
        Integer code,
        String message,
        Long promotionOrderId,
        boolean retryable,
        StoreAutoClaimStopReason stopReason
) {
    public static StoreAutoClaimAttempt retryable(String message) {
        return new StoreAutoClaimAttempt(null, message, null, true, null);
    }

    public static StoreAutoClaimAttempt success(Integer code, String message, Long promotionOrderId) {
        return new StoreAutoClaimAttempt(code, message, promotionOrderId, false,
                StoreAutoClaimStopReason.SUCCESS);
    }

    public static StoreAutoClaimAttempt stop(Integer code, String message, StoreAutoClaimStopReason reason) {
        return new StoreAutoClaimAttempt(code, message, null, false, reason);
    }
}
