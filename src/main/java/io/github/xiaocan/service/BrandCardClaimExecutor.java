package io.github.xiaocan.service;

import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

public class BrandCardClaimExecutor {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration EXECUTION_WINDOW = Duration.ofSeconds(3);

    private final BrandCardClaimClient client;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Supplier<Duration> intervalSupplier;

    public BrandCardClaimExecutor(BrandCardClaimClient client, Clock clock, Sleeper sleeper,
                                  Supplier<Duration> intervalSupplier) {
        this.client = client;
        this.clock = clock;
        this.sleeper = sleeper;
        this.intervalSupplier = intervalSupplier;
    }

    public BrandCardClaimExecutionResult executeAutomatic(Long silkId, String xSivir, int maxAttempts,
                                                            Duration minInterval, Duration maxInterval) {
        return executeAutomatic(silkId, xSivir, null, maxAttempts, minInterval, maxInterval);
    }

    public BrandCardClaimExecutionResult executeAutomatic(Long silkId, String xSivir, Long xVayne,
                                                            int maxAttempts, Duration minInterval,
                                                            Duration maxInterval) {
        Instant target = LocalDate.now(clock).atTime(9, 30).atZone(ZONE_ID).toInstant();
        return executeAutomatic(silkId, xSivir, xVayne, maxAttempts, minInterval, maxInterval, target);
    }

    public BrandCardClaimExecutionResult executeAutomatic(Long silkId, String xSivir, Long xVayne,
                                                            int maxAttempts, Duration minInterval,
                                                            Duration maxInterval, Instant target) {
        waitUntil(target);
        Instant deadline = target.plus(EXECUTION_WINDOW);
        BrandCardClaimAttemptResult lastAttempt = null;
        int attempts = 0;

        while (attempts < maxAttempts && !clock.instant().isAfter(deadline)) {
            attempts++;
            lastAttempt = client.claim(silkId, xSivir, xVayne);
            if (!lastAttempt.retryable()) {
                return BrandCardClaimExecutionResult.fromAttempt(attempts, lastAttempt);
            }

            Duration interval = clamp(intervalSupplier.get(), minInterval, maxInterval);
            if (clock.instant().plus(interval).isAfter(deadline)) {
                break;
            }
            sleep(interval);
        }

        BrandCardClaimStopReason reason = attempts >= maxAttempts
                ? BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED
                : BrandCardClaimStopReason.TIME_WINDOW_EXPIRED;
        return new BrandCardClaimExecutionResult(
                attempts,
                false,
                lastAttempt == null ? null : lastAttempt.code(),
                lastAttempt == null ? "未进入领取时间窗口" : lastAttempt.message(),
                reason
        );
    }

    private void waitUntil(Instant target) {
        Duration wait = Duration.between(clock.instant(), target);
        if (!wait.isNegative() && !wait.isZero()) {
            sleep(wait);
        }
    }

    private Duration clamp(Duration interval, Duration min, Duration max) {
        if (interval.compareTo(min) < 0) {
            return min;
        }
        if (interval.compareTo(max) > 0) {
            return max;
        }
        return interval;
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
