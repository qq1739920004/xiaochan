package io.github.xiaocan.service;

import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimAttemptEvent;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;
import io.github.xiaocan.service.impl.BrandCardClaimServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandCardClaimExecutorTest {

    @Test
    void defaultConcurrentClaimIntervalUsesOneHundredToThreeHundredMilliseconds() throws Exception {
        assertEquals(100, privateIntConstant("DEFAULT_MIN_INTERVAL_MS"));
        assertEquals(300, privateIntConstant("DEFAULT_MAX_INTERVAL_MS"));
    }

    @Test
    void automaticClaimWaitsUntilNineThirtyBeforeFirstRequest() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:58+08:00");
        List<Instant> callTimes = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            callTimes.add(clock.instant());
            return BrandCardClaimAttemptResult.stop(40021, "今日限量大牌券已抢完，明日再来吧～",
                    BrandCardClaimStopReason.SOLD_OUT);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(100)
        );

        BrandCardClaimExecutionResult result = executor.executeAutomatic(126938104L, "token", 12,
                Duration.ofMillis(100), Duration.ofMillis(400));

        assertFalse(result.success());
        assertEquals(1, result.attempts());
        assertEquals(BrandCardClaimStopReason.SOLD_OUT, result.stopReason());
        assertEquals(callTimes.get(0), result.firstAttemptAt());
        assertEquals(LocalDateTime.of(2026, 7, 31, 9, 30),
                LocalDateTime.ofInstant(callTimes.get(0), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void retriesTemporaryFailuresWithConfiguredIntervalUntilSuccess() {
        MutableClock clock = new MutableClock("2026-07-31T09:30:00+08:00");
        AtomicInteger attempts = new AtomicInteger();
        List<Instant> callTimes = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            callTimes.add(clock.instant());
            int current = attempts.incrementAndGet();
            if (current < 3) {
                return BrandCardClaimAttemptResult.retryable(null, "网络超时");
            }
            return BrandCardClaimAttemptResult.stop(0, "领取成功", BrandCardClaimStopReason.SUCCESS);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(150)
        );

        BrandCardClaimExecutionResult result = executor.executeAutomatic(126938104L, "token", 12,
                Duration.ofMillis(100), Duration.ofMillis(400));

        assertTrue(result.success());
        assertEquals(3, result.attempts());
        assertEquals(BrandCardClaimStopReason.SUCCESS, result.stopReason());
        assertEquals(Duration.ofMillis(150), Duration.between(callTimes.get(0), callTimes.get(1)));
        assertEquals(Duration.ofMillis(150), Duration.between(callTimes.get(1), callTimes.get(2)));
    }

    @Test
    void limitsAutomaticRetriesToFiveEvenWhenAnOldConfigRequestsMore() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:58+08:00");
        List<Instant> callTimes = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            callTimes.add(clock.instant());
            return BrandCardClaimAttemptResult.retryable(null, "网络超时");
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(100)
        );

        BrandCardClaimExecutionResult result = executor.executeAutomatic(126938104L, "token", 12,
                Duration.ofMillis(100), Duration.ofMillis(400));

        assertEquals(5, result.attempts());
        assertEquals(Instant.parse("2026-07-31T01:30:00Z"), callTimes.get(0));
        assertEquals(BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED, result.stopReason());
    }

    @Test
    void continuousWindowKeepsRetryingNotStartedResponseUntilDeadline() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:55+08:00");
        List<Instant> callTimes = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            callTimes.add(clock.instant());
            return BrandCardClaimAttemptResult.stop(40026, "还没到开抢时间，再等等",
                    BrandCardClaimStopReason.BUSINESS_FAILED);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(100)
        );
        Instant start = Instant.parse("2026-07-31T01:29:57Z");
        Instant deadline = Instant.parse("2026-07-31T01:30:01Z");

        BrandCardClaimExecutionResult result = executor.executeContinuous(126938104L, "token", null,
                100, Duration.ofMillis(100), Duration.ofMillis(300), start, deadline);

        assertEquals(start, callTimes.get(0));
        assertTrue(callTimes.size() > 1);
        assertTrue(callTimes.stream().allMatch(time -> time.isBefore(deadline)));
        assertEquals(BrandCardClaimStopReason.TIME_WINDOW_EXPIRED, result.stopReason());
    }

    @Test
    void continuousWindowDoesNotPauseAtNineThirty() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:59.920+08:00");
        List<Instant> callTimes = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            callTimes.add(clock.instant());
            if (attempts.incrementAndGet() == 1) {
                clock.advance(Duration.ofMillis(30));
                return BrandCardClaimAttemptResult.stop(40026, "还没到开抢时间，再等等",
                        BrandCardClaimStopReason.BUSINESS_FAILED);
            }
            return BrandCardClaimAttemptResult.stop(0, "领取成功", BrandCardClaimStopReason.SUCCESS);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(200)
        );
        Instant start = Instant.parse("2026-07-31T01:29:58Z");
        Instant deadline = Instant.parse("2026-07-31T01:30:01Z");

        BrandCardClaimExecutionResult result = executor.executeContinuous(126938104L, "token", null,
                100, Duration.ofMillis(50), Duration.ofMillis(100), start, deadline);

        assertTrue(result.success());
        assertEquals(List.of(
                Instant.parse("2026-07-31T01:29:59.920Z"),
                Instant.parse("2026-07-31T01:30:00.050Z")
        ), callTimes);
    }

    @Test
    void continuousWindowStopsImmediatelyWhenSoldOut() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:55+08:00");
        AtomicInteger attempts = new AtomicInteger();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            attempts.incrementAndGet();
            return BrandCardClaimAttemptResult.stop(40021, "今日限量大牌券已抢完，明日再来吧～",
                    BrandCardClaimStopReason.SOLD_OUT);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(100)
        );

        BrandCardClaimExecutionResult result = executor.executeContinuous(126938104L, "token", null,
                100, Duration.ofMillis(100), Duration.ofMillis(300),
                Instant.parse("2026-07-31T01:29:57Z"), Instant.parse("2026-07-31T01:30:01Z"));

        assertEquals(1, attempts.get());
        assertEquals(BrandCardClaimStopReason.SOLD_OUT, result.stopReason());
    }

    @Test
    void continuousWindowReportsEveryAttemptAndTheSuccessfulSequence() {
        MutableClock clock = new MutableClock("2026-07-31T09:29:55+08:00");
        AtomicInteger requests = new AtomicInteger();
        List<Integer> sequences = new ArrayList<>();
        List<BrandCardClaimStopReason> stopReasons = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> {
            if (requests.incrementAndGet() < 3) {
                return BrandCardClaimAttemptResult.stop(40026, "还没到开抢时间",
                        BrandCardClaimStopReason.BUSINESS_FAILED);
            }
            return BrandCardClaimAttemptResult.stop(0, "领取成功", BrandCardClaimStopReason.SUCCESS);
        };

        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(100)
        );

        BrandCardClaimExecutionResult result = executor.executeContinuous(126938104L, "token", null,
                100, Duration.ofMillis(100), Duration.ofMillis(300),
                Instant.parse("2026-07-31T01:29:57Z"), Instant.parse("2026-07-31T01:30:01Z"),
                event -> {
                    sequences.add(event.sequence());
                    stopReasons.add(event.result().stopReason());
                });

        assertTrue(result.success());
        assertEquals(List.of(1, 2, 3), sequences);
        assertEquals(BrandCardClaimStopReason.SUCCESS, stopReasons.get(2));
    }

    @Test
    void concurrentWindowLimitsInFlightRequestsToFiveAndStopsOnSuccess() throws Exception {
        AtomicInteger started = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peakInFlight = new AtomicInteger();
        CountDownLatch fiveRequestsStarted = new CountDownLatch(5);
        CountDownLatch allowResponses = new CountDownLatch(1);
        BrandCardClaimClient client = (silkId, xSivir) -> {
            int sequence = started.incrementAndGet();
            int currentInFlight = inFlight.incrementAndGet();
            peakInFlight.accumulateAndGet(currentInFlight, Math::max);
            fiveRequestsStarted.countDown();
            try {
                allowResponses.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return sequence == 1
                    ? BrandCardClaimAttemptResult.stop(0, "领取成功", BrandCardClaimStopReason.SUCCESS)
                    : BrandCardClaimAttemptResult.retryable(null, "请继续");
        };
        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                Clock.systemUTC(),
                duration -> java.util.concurrent.TimeUnit.NANOSECONDS.sleep(duration.toNanos()),
                () -> Duration.ofMillis(20)
        );
        Instant target = Instant.now().plusMillis(20);
        Instant deadline = target.plusMillis(400);
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            Future<BrandCardClaimExecutionResult> execution = runner.submit(() ->
                    executor.executeConcurrentContinuous(126938104L, "token", null,
                            Duration.ofMillis(20), 5, target, deadline, event -> {
                            }));

            assertTrue(fiveRequestsStarted.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(5, peakInFlight.get());
            allowResponses.countDown();

            BrandCardClaimExecutionResult result = execution.get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(result.success());
            assertTrue(peakInFlight.get() <= 5);
        } finally {
            allowResponses.countDown();
            runner.shutdownNow();
        }
    }

    @Test
    void 并发连续领取使用配置的最大次数和随机间隔() throws Exception {
        MutableClock clock = new MutableClock("2026-07-31T09:29:59+08:00");
        List<BrandCardClaimAttemptEvent> events = new ArrayList<>();
        BrandCardClaimClient client = (silkId, xSivir) -> BrandCardClaimAttemptResult.retryable(null, "请继续");
        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                client,
                clock,
                duration -> clock.advance(duration),
                () -> Duration.ofMillis(10)
        );

        BrandCardClaimExecutionResult result = executor.executeConcurrentContinuous(
                126938104L, "token", null, 3, 1,
                Instant.parse("2026-07-31T01:29:59Z"),
                Instant.parse("2026-07-31T01:30:01Z"), events::add);

        assertEquals(3, result.attempts());
        assertEquals(BrandCardClaimStopReason.MAX_ATTEMPTS_REACHED, result.stopReason());
        assertEquals(List.of(1, 2, 3), events.stream().map(BrandCardClaimAttemptEvent::sequence).toList());
        assertTrue(Duration.between(events.get(0).requestTime(), events.get(1).requestTime())
                .compareTo(Duration.ofMillis(10)) >= 0);
    }

    private static final class MutableClock extends Clock {
        private final ZoneId zone = ZoneId.of("Asia/Shanghai");
        private Instant instant;

        private MutableClock(String isoDateTime) {
            this.instant = Instant.from(java.time.OffsetDateTime.parse(isoDateTime));
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private int privateIntConstant(String name) throws Exception {
        java.lang.reflect.Field field = BrandCardClaimServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
