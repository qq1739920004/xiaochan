package io.github.xiaocan.model;

public enum StoreAutoClaimStopReason {
    SUCCESS,
    SOLD_OUT_OR_EXPIRED,
    ALREADY_CLAIMED,
    AUTH_INVALID,
    NEED_VERIFY,
    MISSING_CREDENTIALS,
    BUSINESS_FAILURE,
    MAX_ATTEMPTS_REACHED,
    TIME_WINDOW_EXPIRED
}
