package io.github.xiaocan.service;

import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimRequest;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreAutoClaimStopReason;

import java.time.Duration;
import java.util.function.Supplier;

public class StoreAutoClaimExecutor {
    private final StoreAutoClaimClient client;
    private final Sleeper sleeper;
    private final Supplier<Duration> intervalSupplier;

    public StoreAutoClaimExecutor(StoreAutoClaimClient client, Sleeper sleeper,
                                  Supplier<Duration> intervalSupplier) {
        this.client = client;
        this.sleeper = sleeper;
        this.intervalSupplier = intervalSupplier;
    }

    public StoreAutoClaimResult execute(StoreAutoClaimRequest request, int maxAttempts,
                                        Duration minInterval, Duration maxInterval) {
        StoreAutoClaimAttempt lastAttempt = null;
        int attempts = 0;
        while (attempts < maxAttempts) {
            attempts++;
            lastAttempt = client.claim(request);
            if (!lastAttempt.retryable()) {
                return StoreAutoClaimResult.fromAttempt(attempts, lastAttempt);
            }
            if (attempts >= maxAttempts) {
                break;
            }
            sleep(clamp(intervalSupplier.get(), minInterval, maxInterval));
        }
        return new StoreAutoClaimResult(
                attempts,
                false,
                lastAttempt == null ? null : lastAttempt.code(),
                lastAttempt == null ? "未执行抢单请求" : lastAttempt.message(),
                null,
                StoreAutoClaimStopReason.REQUEST_FAILED
        );
    }

    public StoreAutoClaimResult executeOnce(StoreAutoClaimRequest request) {
        StoreAutoClaimAttempt attempt = client.claim(request);
        if (!attempt.retryable()) {
            return StoreAutoClaimResult.fromAttempt(1, attempt);
        }
        return new StoreAutoClaimResult(
                1,
                false,
                attempt.code(),
                attempt.message(),
                null,
                StoreAutoClaimStopReason.MAX_ATTEMPTS_REACHED
        );
    }

    private Duration clamp(Duration value, Duration min, Duration max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
