package io.github.xiaocan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import io.github.xiaocan.model.dto.XiaochanAccountDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XiaochanAccountJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCamelCaseXVayneFromAccountForm() throws Exception {
        XiaochanAccountDTO dto = objectMapper.readValue("""
                {"accountName":"主账号","silkId":126938104,"xVayne":1836966,"xSivir":"token-value","enabled":true}
                """, XiaochanAccountDTO.class);

        assertEquals(126938104L, dto.getSilkId());
        assertEquals(1836966L, dto.getXVayne());
        assertEquals("token-value", dto.getXSivir());
        String json = objectMapper.writeValueAsString(dto);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"xVayne\":1836966"));
    }

    @Test
    void acceptsCamelCaseXVayneWithStandardBeanNaming() throws Exception {
        ObjectMapper standardBeanMapper = new ObjectMapper()
                .configure(MapperFeature.USE_STD_BEAN_NAMING, true);

        XiaochanAccountDTO dto = standardBeanMapper.readValue(
                "{\"silkId\":126938104,\"xVayne\":1836966,\"xSivir\":\"token-value\",\"enabled\":true}",
                XiaochanAccountDTO.class);

        assertEquals(1836966L, dto.getXVayne());
    }
}
