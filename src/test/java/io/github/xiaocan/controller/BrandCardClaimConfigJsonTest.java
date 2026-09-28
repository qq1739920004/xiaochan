package io.github.xiaocan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.XiaochanAccountVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandCardClaimConfigJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesCamelCaseXSiVirPropertiesForFrontendRequestsAndResponses() throws Exception {
        BrandCardClaimConfigDTO dto = objectMapper.readValue("""
                {"silkId":126938104,"xVayne":1836966,"xSivir":"token-value","enabled":true}
                """, BrandCardClaimConfigDTO.class);
        BrandCardClaimConfigVO vo = new BrandCardClaimConfigVO();
        vo.setXVayne(1836966L);
        vo.setXSivirMasked("token...alue");

        String json = objectMapper.writeValueAsString(vo);

        assertEquals("token-value", dto.getXSivir());
        assertEquals(1836966L, dto.getXVayne());
        assertTrue(json.contains("\"xSivirMasked\""));
        assertTrue(json.contains("\"xVayne\":1836966"));
    }

    @Test
    void 支持保存并回显大牌券执行参数() throws Exception {
        BrandCardClaimConfigDTO dto = objectMapper.readValue("""
                {"silkId":126938104,"xVayne":1836966,"enabled":true,
                 "maxAttempts":280,"minIntervalMs":1,"maxIntervalMs":10,
                 "startDelayMs":3000,"windowDurationMs":2000,
                 "maxInFlight":5,"requestTimeoutMs":1000}
                """, BrandCardClaimConfigDTO.class);
        BrandCardClaimConfigVO vo = new BrandCardClaimConfigVO();
        vo.setMaxAttempts(dto.getMaxAttempts());
        vo.setMinIntervalMs(dto.getMinIntervalMs());
        vo.setMaxIntervalMs(dto.getMaxIntervalMs());
        vo.setStartDelayMs(dto.getStartDelayMs());
        vo.setWindowDurationMs(dto.getWindowDurationMs());
        vo.setMaxInFlight(dto.getMaxInFlight());
        vo.setRequestTimeoutMs(dto.getRequestTimeoutMs());

        String json = objectMapper.writeValueAsString(vo);

        assertEquals(280, dto.getMaxAttempts());
        assertEquals(3000, dto.getStartDelayMs());
        assertTrue(json.contains("\"maxInFlight\":5"));
        assertTrue(json.contains("\"requestTimeoutMs\":1000"));
    }

    @Test
    void 最大并发支持超过五的配置() {
        BrandCardClaimConfigDTO dto = new BrandCardClaimConfigDTO();
        dto.setSilkId(126938104L);
        dto.setXVayne(1836966L);
        dto.setEnabled(true);
        dto.setMaxInFlight(20);

        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void accountResponseNeverContainsFullSessionCredential() throws Exception {
        XiaochanAccountVO vo = new XiaochanAccountVO();
        vo.setAccountName("主账号");
        vo.setSilkId(126938104L);
        vo.setXSivirMasked("eyJhbG...WpE");

        String json = objectMapper.writeValueAsString(vo);

        assertTrue(json.contains("\"xSivirMasked\""));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("xSivir\""));
    }
}
