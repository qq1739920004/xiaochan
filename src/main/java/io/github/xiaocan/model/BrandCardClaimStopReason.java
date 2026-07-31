package io.github.xiaocan.model;

public enum BrandCardClaimStopReason {
    SUCCESS,
    SOLD_OUT,
    ALREADY_CLAIMED,
    NEED_VERIFY,
    AUTH_INVALID,
    BUSINESS_FAILED,
    MAX_ATTEMPTS_REACHED,
    TIME_WINDOW_EXPIRED
}
