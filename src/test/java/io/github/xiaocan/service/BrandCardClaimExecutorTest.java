package io.github.xiaocan.service;

import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandCardClaimExecutorTest {

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
}
