package io.github.xiaocan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.XiaochanAccountVO;
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
