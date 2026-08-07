package io.github.xiaocan.service;

import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimRequest;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreAutoClaimExecutorTest {

    @Test
    void retriesOnlyTransportFailuresUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        StoreAutoClaimClient client = request -> calls.incrementAndGet() < 3
                ? StoreAutoClaimAttempt.retryable("网络超时")
                : StoreAutoClaimAttempt.success(0, "抢单成功", 888L);
        StoreAutoClaimExecutor executor = new StoreAutoClaimExecutor(client, duration -> { }, () -> Duration.ofMillis(150));

        var result = executor.execute(request(), 5, Duration.ofMillis(100), Duration.ofMillis(400));

        assertTrue(result.success());
        assertEquals(3, result.attempts());
        assertEquals(888L, result.promotionOrderId());
    }

    @Test
    void stopsImmediatelyForBusinessFailure() {
        StoreAutoClaimExecutor executor = new StoreAutoClaimExecutor(
                request -> StoreAutoClaimAttempt.stop(40021, "已抢完", StoreAutoClaimStopReason.SOLD_OUT_OR_EXPIRED),
                duration -> { },
                () -> Duration.ofMillis(150)
        );

        var result = executor.execute(request(), 5, Duration.ofMillis(100), Duration.ofMillis(400));

        assertEquals(1, result.attempts());
        assertEquals(StoreAutoClaimStopReason.SOLD_OUT_OR_EXPIRED, result.stopReason());
    }

    @Test
    void oneShotExecutionDoesNotRetryTransportFailure() {
        AtomicInteger calls = new AtomicInteger();
        StoreAutoClaimExecutor executor = new StoreAutoClaimExecutor(
                request -> {
                    calls.incrementAndGet();
                    return StoreAutoClaimAttempt.retryable("网络超时");
                },
                duration -> { },
                () -> Duration.ofMillis(150)
        );

        var result = executor.executeOnce(request());

        assertEquals(1, calls.get());
        assertEquals(1, result.attempts());
        assertEquals(StoreAutoClaimStopReason.REQUEST_FAILED, result.stopReason());
    }

    private StoreAutoClaimRequest request() {
        return new StoreAutoClaimRequest(1L, "token", 310114, "121.4", "31.2", 99L, 1, null);
    }
}
