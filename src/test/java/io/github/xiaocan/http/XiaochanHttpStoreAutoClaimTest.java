package io.github.xiaocan.http;

import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.StoreAutoClaimRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XiaochanHttpStoreAutoClaimTest {

    @Test
    void buildsGrabPromotionQuotaRequestWithoutRedpackWhenUnavailable() {
        XiaochanHttp.StoreAutoClaimRequestParts request = XiaochanHttp.buildStoreAutoClaimRequest(request(null));

        assertEquals("Silkworm", request.headers().get("servername"));
        assertEquals("SilkwormService.GrabPromotionQuota", request.headers().get("methodname"));
        assertEquals("token-value", request.headers().get("X-Sivir"));
        assertEquals("1836966", request.headers().get("X-Vayne"));
        assertEquals("126938104", request.headers().get("x-Teemo"));
        assertEquals("iOS", request.headers().get("X-Platform"));
        assertEquals("3.19.1.0", request.headers().get("X-Version"));
        assertEquals("XC;iOS;3.19.1", request.headers().get("User-Agent"));
        assertTrue(request.headers().get("X-Nami").contains("126938104"));
        assertFalse(request.body().contains("redpack_id"));
        assertTrue(request.body().contains("\"promotion_id\":987654321"));
    }

    @Test
    void addsRedpackOnlyWhenProvidedAndGeneratesFreshHeaders() {
        XiaochanHttp.StoreAutoClaimRequestParts first = XiaochanHttp.buildStoreAutoClaimRequest(request(123L));
        XiaochanHttp.StoreAutoClaimRequestParts second = XiaochanHttp.buildStoreAutoClaimRequest(request(123L));

        assertTrue(first.body().contains("\"redpack_id\":123"));
        assertNotEquals(first.headers().get("X-Nami"), second.headers().get("X-Nami"));
        assertNotEquals(first.headers().get("X-Session-Id"), second.headers().get("X-Session-Id"));
    }

    @Test
    void treatsUnauthorizedHttpResponseAsTerminal() {
        StoreAutoClaimAttempt attempt = XiaochanHttp.classifyStoreClaimHttpFailure(401);

        assertFalse(attempt.retryable());
        assertEquals(401, attempt.code());
        assertEquals(StoreAutoClaimStopReason.AUTH_INVALID, attempt.stopReason());
    }

    @Test
    void keepsServerErrorsRetryable() {
        StoreAutoClaimAttempt attempt = XiaochanHttp.classifyStoreClaimHttpFailure(502);

        assertTrue(attempt.retryable());
        assertEquals(502, attempt.code());
    }

    private StoreAutoClaimRequest request(Long redpackId) {
        return new StoreAutoClaimRequest(126938104L, "token-value", 1836966L, 310114, "121.400", "31.200",
                987654321L, 1, redpackId);
    }
}
