package io.github.xiaocan.http;

import io.github.xiaocan.model.BrandCardClaimStopReason;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XiaochanHttpBrandCardTest {

    @Test
    void buildsBrandCardClaimBodyAndHeaders() {
        XiaochanHttp.BrandCardRequestParts request =
                XiaochanHttp.buildBrandCardClaimRequestParts(126938104L, "token-value", 1836966L);

        assertEquals("{\"type\":99,\"silk_id\":126938104}", request.body());

        Map<String, String> headers = request.headers();
        assertEquals("SilkwormVip", headers.get("servername"));
        assertEquals("VipRightsService.GrabExtraBrandCard", headers.get("methodname"));
        assertEquals("token-value", headers.get("X-Sivir"));
        assertEquals("1836966", headers.get("X-Vayne"));
        assertEquals("126938104", headers.get("x-Teemo"));
        assertEquals("iOS", headers.get("X-Platform"));
        assertEquals("3.19.1.0", headers.get("X-Version"));
        assertEquals("XC;iOS;3.19.1", headers.get("User-Agent"));
        assertTrue(headers.get("X-Nami").contains("126938104"));
        assertTrue(headers.get("X-Garen").matches("\\d{13}"));
        assertEquals(32, headers.get("X-Ashe").length());
        assertTrue(headers.get("X-Session-Id").length() >= 32);
    }

    @Test
    void generatesFreshDynamicHeadersForEachBrandCardClaimRequest() {
        XiaochanHttp.BrandCardRequestParts first =
                XiaochanHttp.buildBrandCardClaimRequestParts(126938104L, "token-value");
        XiaochanHttp.BrandCardRequestParts second =
                XiaochanHttp.buildBrandCardClaimRequestParts(126938104L, "token-value");

        assertNotEquals(first.headers().get("X-Nami"), second.headers().get("X-Nami"));
        assertNotEquals(first.headers().get("X-Session-Id"), second.headers().get("X-Session-Id"));
    }

    @Test
    void treatsUnauthorizedBrandCardResponseAsTerminal() {
        assertEquals(BrandCardClaimStopReason.AUTH_INVALID,
                XiaochanHttp.classifyBrandCardHttpFailure(401).stopReason());
        assertTrue(XiaochanHttp.classifyBrandCardHttpFailure(503).retryable());
    }
}
