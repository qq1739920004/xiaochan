package io.github.xiaocan.service;

import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimAttemptEvent;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.time.ZoneId;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.function.Consumer;

public class BrandCardClaimExecutor {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration EXECUTION_WINDOW = Duration.ofSeconds(3);
    private static final int MAX_AUTOMATIC_ATTEMPTS = 5;
    private static final Duration FINAL_WAIT_SLICE = Duration.ofMillis(2);

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
        Instant firstAttemptAt = null;
        int attempts = 0;
        int attemptLimit = Math.min(Math.max(1, maxAttempts), MAX_AUTOMATIC_ATTEMPTS);

        while (attempts < attemptLimit && !clock.instant().isAfter(deadline)) {
            attempts++;
            if (firstAttemptAt == null) {
                firstAttemptAt = clock.instant();
            }
            lastAttempt = client.claim(silkId, xSivir, xVayne);
            if (!lastAttempt.retryable()) {
                return BrandCardClaimExecutionResult.fromAttempt(attempts, lastAttempt, firstAttemptAt);
            }

            Duration interval = clamp(intervalSupplier.get(), minInterval, maxInterval);
            if (clock.instant().plus(interval).isAfter(deadline)) {
                break;
            }
            sleep(interval);
        }

        BrandCardClaimStopReason reason = attempts >= attemptLimit
                ? BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED
                : BrandCardClaimStopReason.TIME_WINDOW_EXPIRED;
        return new BrandCardClaimExecutionResult(
                attempts,
                false,
                lastAttempt == null ? null : lastAttempt.code(),
                lastAttempt == null ? "未进入领取时间窗口" : lastAttempt.message(),
                reason,
                firstAttemptAt
        );
    }

    public BrandCardClaimExecutionResult executeContinuous(Long silkId, String xSivir, Long xVayne,
                                                             int maxAttempts, Duration minInterval,
                                                             Duration maxInterval, Instant target,
                                                             Instant deadline) {
        return executeContinuous(silkId, xSivir, xVayne, maxAttempts, minInterval, maxInterval,
                target, deadline, event -> {
                });
    }

    public BrandCardClaimExecutionResult executeContinuous(Long silkId, String xSivir, Long xVayne,
                                                             int maxAttempts, Duration minInterval,
                                                             Duration maxInterval, Instant target,
                                                             Instant deadline,
                                                             Consumer<BrandCardClaimAttemptEvent> attemptConsumer) {
        waitUntil(target);
        BrandCardClaimAttemptResult lastAttempt = null;
        Instant firstAttemptAt = null;
        int attempts = 0;

        while (attempts < maxAttempts && clock.instant().isBefore(deadline)) {
            attempts++;
            Instant requestTime = clock.instant();
            if (firstAttemptAt == null) {
                firstAttemptAt = requestTime;
            }
            lastAttempt = client.claim(silkId, xSivir, xVayne);
            attemptConsumer.accept(new BrandCardClaimAttemptEvent(
                    attempts, requestTime, clock.instant(), lastAttempt));
            if (isContinuousWindowTerminal(lastAttempt)) {
                return BrandCardClaimExecutionResult.fromAttempt(attempts, lastAttempt, firstAttemptAt);
            }

            Duration interval = clamp(intervalSupplier.get(), minInterval, maxInterval);
            Instant nextAttemptAt = clock.instant().plus(interval);
            if (!nextAttemptAt.isBefore(deadline)) {
                break;
            }
            sleep(Duration.between(clock.instant(), nextAttemptAt));
        }

        BrandCardClaimStopReason reason = attempts >= maxAttempts
                ? BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED
                : BrandCardClaimStopReason.TIME_WINDOW_EXPIRED;
        return new BrandCardClaimExecutionResult(
                attempts,
                false,
                lastAttempt == null ? null : lastAttempt.code(),
                lastAttempt == null ? "未进入连续领取窗口" : lastAttempt.message(),
                reason,
                firstAttemptAt
        );
    }

    public BrandCardClaimExecutionResult executeConcurrentContinuous(Long silkId, String xSivir, Long xVayne,
                                                                       Duration interval, int maxInFlight,
                                                                       Instant target, Instant deadline,
                                                                       Consumer<BrandCardClaimAttemptEvent> attemptConsumer) {
        return executeConcurrentContinuous(silkId, xSivir, xVayne, Integer.MAX_VALUE, maxInFlight,
                () -> interval, target, deadline, attemptConsumer);
    }

    public BrandCardClaimExecutionResult executeConcurrentContinuous(Long silkId, String xSivir, Long xVayne,
                                                                       int maxAttempts, int maxInFlight,
                                                                       Instant target, Instant deadline,
                                                                       Consumer<BrandCardClaimAttemptEvent> attemptConsumer) {
        return executeConcurrentContinuous(silkId, xSivir, xVayne, maxAttempts, maxInFlight,
                intervalSupplier, target, deadline, attemptConsumer);
    }

    private BrandCardClaimExecutionResult executeConcurrentContinuous(Long silkId, String xSivir, Long xVayne,
                                                                        int maxAttempts, int maxInFlight,
                                                                        Supplier<Duration> requestIntervalSupplier,
                                                                        Instant target, Instant deadline,
                                                                        Consumer<BrandCardClaimAttemptEvent> attemptConsumer) {
        waitUntil(target);
        int concurrency = Math.max(1, maxInFlight);
        int attemptLimit = Math.max(1, maxAttempts);
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        CompletionService<BrandCardClaimAttemptEvent> completions = new ExecutorCompletionService<>(workers);
        BrandCardClaimAttemptResult lastAttempt = null;
        BrandCardClaimAttemptResult terminalAttempt = null;
        Instant firstAttemptAt = null;
        Instant nextRequestAt = target;
        int attempts = 0;
        int inFlight = 0;
        boolean stopSubmitting = false;

        try {
            while (inFlight > 0 || (!stopSubmitting && clock.instant().isBefore(deadline))) {
                Future<BrandCardClaimAttemptEvent> completed;
                while ((completed = completions.poll()) != null) {
                    inFlight--;
                    BrandCardClaimAttemptEvent event = completed.get();
                    attemptConsumer.accept(event);
                    lastAttempt = event.result();
                    if (terminalAttempt == null && isContinuousWindowTerminal(event.result())) {
                        terminalAttempt = event.result();
                        stopSubmitting = true;
                    }
                }

                if (stopSubmitting) {
                    if (inFlight > 0) {
                        sleep(Duration.ofMillis(1));
                    }
                    continue;
                }

                Instant now = clock.instant();
                if (attempts >= attemptLimit) {
                    stopSubmitting = true;
                    continue;
                }

                if (now.isBefore(deadline) && inFlight < concurrency && !now.isBefore(nextRequestAt)) {
                    int sequence = ++attempts;
                    if (firstAttemptAt == null) {
                        firstAttemptAt = now;
                    }
                    completions.submit(() -> executeAttempt(sequence, silkId, xSivir, xVayne));
                    inFlight++;
                    nextRequestAt = now.plus(validInterval(requestIntervalSupplier.get()));
                    continue;
                }

                if (inFlight > 0) {
                    sleep(Duration.ofMillis(1));
                } else if (now.isBefore(deadline)) {
                    Instant nextWakeUp = nextRequestAt.isBefore(deadline) ? nextRequestAt : deadline;
                    sleep(Duration.between(now, nextWakeUp));
                }
            }
        } catch (Exception e) {
            BrandCardClaimAttemptResult failed = BrandCardClaimAttemptResult.retryable(null,
                    "请求调度异常: " + e.getMessage());
            return new BrandCardClaimExecutionResult(attempts, false, failed.code(), failed.message(),
                    BrandCardClaimStopReason.TIME_WINDOW_EXPIRED, firstAttemptAt);
        } finally {
            workers.shutdown();
        }

        if (terminalAttempt != null) {
            return BrandCardClaimExecutionResult.fromAttempt(attempts, terminalAttempt, firstAttemptAt);
        }
        BrandCardClaimStopReason reason = clock.instant().isBefore(deadline)
                ? BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED
                : BrandCardClaimStopReason.TIME_WINDOW_EXPIRED;
        return new BrandCardClaimExecutionResult(
                attempts,
                false,
                lastAttempt == null ? null : lastAttempt.code(),
                lastAttempt == null ? "未进入连续领取窗口" : lastAttempt.message(),
                reason,
                firstAttemptAt
        );
    }

    private BrandCardClaimAttemptEvent executeAttempt(int sequence, Long silkId, String xSivir, Long xVayne) {
        Instant requestTime = clock.instant();
        BrandCardClaimAttemptResult result;
        try {
            result = client.claim(silkId, xSivir, xVayne);
        } catch (RuntimeException e) {
            result = BrandCardClaimAttemptResult.retryable(null, "请求异常: " + e.getMessage());
        }
        return new BrandCardClaimAttemptEvent(sequence, requestTime, clock.instant(), result);
    }

    private Duration validInterval(Duration interval) {
        return interval == null || interval.isNegative() || interval.isZero()
                ? Duration.ofMillis(1) : interval;
    }

    private boolean isContinuousWindowTerminal(BrandCardClaimAttemptResult attempt) {
        if (attempt.retryable() || attempt.stopReason() == null) {
            return false;
        }
        return EnumSet.of(
                BrandCardClaimStopReason.SUCCESS,
                BrandCardClaimStopReason.SOLD_OUT,
                BrandCardClaimStopReason.ALREADY_CLAIMED,
                BrandCardClaimStopReason.NEED_VERIFY,
                BrandCardClaimStopReason.AUTH_INVALID
        ).contains(attempt.stopReason());
    }

    private void waitUntil(Instant target) {
        while (true) {
            Duration remaining = Duration.between(clock.instant(), target);
            if (remaining.isNegative() || remaining.isZero()) {
                return;
            }
            Duration wait = remaining.compareTo(FINAL_WAIT_SLICE) > 0
                    ? remaining.minus(FINAL_WAIT_SLICE) : remaining;
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
