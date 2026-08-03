package io.github.xiaocan.http;

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
        assertEquals("126938104", request.headers().get("x-Teemo"));
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

    private StoreAutoClaimRequest request(Long redpackId) {
        return new StoreAutoClaimRequest(126938104L, "token-value", 310114, "121.400", "31.200",
                987654321L, 1, redpackId);
    }
}
