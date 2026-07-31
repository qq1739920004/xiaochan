package io.github.xiaocan.model;

public record BrandCardClaimAttemptResult(
        Integer code,
        String message,
        boolean retryable,
        BrandCardClaimStopReason stopReason
) {
    public static BrandCardClaimAttemptResult retryable(Integer code, String message) {
        return new BrandCardClaimAttemptResult(code, message, true, null);
    }

    public static BrandCardClaimAttemptResult stop(Integer code, String message, BrandCardClaimStopReason stopReason) {
        return new BrandCardClaimAttemptResult(code, message, false, stopReason);
    }
}
