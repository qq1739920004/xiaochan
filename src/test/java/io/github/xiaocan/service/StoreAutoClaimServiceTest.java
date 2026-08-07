package io.github.xiaocan.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.github.xiaocan.mapper.BrandCardClaimConfigMapper;
import io.github.xiaocan.mapper.StoreAutoClaimHistoryMapper;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.StoreAutoClaimHistoryEntity;
import io.github.xiaocan.model.entity.BrandCardClaimConfigEntity;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.model.StoreInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.github.xiaocan.service.impl.StoreAutoClaimServiceImpl;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAutoClaimServiceTest {

    @Mock
    private BrandCardClaimConfigMapper brandCardClaimConfigMapper;
    @Mock
    private StoreAutoClaimHistoryMapper historyMapper;
    @Mock
    private StoreAutoClaimClient claimClient;

    @Test
    void recordsMissingCredentialsWithoutSendingAClaim() {
        when(brandCardClaimConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        StoreAutoClaimService service = new StoreAutoClaimServiceImpl(
                brandCardClaimConfigMapper, historyMapper, claimClient);

        StoreAutoClaimResult result = service.execute(monitorConfig(), location(), candidate());

        assertEquals(StoreAutoClaimStopReason.MISSING_CREDENTIALS, result.stopReason());
        verifyNoInteractions(claimClient);
        verify(historyMapper).insert(ArgumentMatchers.<StoreAutoClaimHistoryEntity>any());
    }

    @Test
    void sendsOneClaimAndPersistsSuccessfulOrder() {
        BrandCardClaimConfigEntity credentials = new BrandCardClaimConfigEntity();
        credentials.setId(11);
        credentials.setUserId(8);
        credentials.setSilkId(126938104L);
        credentials.setXVayne(1836966L);
        credentials.setXSivir("token-value");
        when(brandCardClaimConfigMapper.selectOne(any(Wrapper.class))).thenReturn(credentials);
        when(claimClient.claim(any())).thenReturn(
                StoreAutoClaimAttempt.success(0, "抢单成功", 888L));
        StoreAutoClaimService service = new StoreAutoClaimServiceImpl(
                brandCardClaimConfigMapper, historyMapper, claimClient);
        MonitorConfigEntity config = monitorConfig();
        config.setExtConfig("{\"autoClaimConfig\":{\"enabled\":true,\"maxAttempts\":1,\"minIntervalMs\":100,\"maxIntervalMs\":100}}");

        StoreAutoClaimResult result = service.execute(config, location(), candidate());

        assertEquals(888L, result.promotionOrderId());
        verify(claimClient).claim(ArgumentMatchers.argThat(request ->
                request.redpackId() == null && Long.valueOf(456L).equals(request.promotionId())));
        verify(historyMapper).insert(ArgumentMatchers.<StoreAutoClaimHistoryEntity>argThat(history ->
                Long.valueOf(888L).equals(history.getPromotionOrderId()) && history.getSuccess()));
    }

    private MonitorConfigEntity monitorConfig() {
        MonitorConfigEntity config = new MonitorConfigEntity();
        config.setId(5);
        config.setUserId(8);
        config.setType(MonitorTypeEnums.STORE_ACTIVITY);
        return config;
    }

    private LocationEntity location() {
        LocationEntity location = new LocationEntity();
        location.setCityCode(310114);
        location.setLongitude("121.4");
        location.setLatitude("31.2");
        return location;
    }

    private StoreInfo candidate() {
        StoreInfo store = new StoreInfo();
        store.setStoreId(123);
        store.setName("测试门店");
        store.setPromotionId("456");
        store.setType(1);
        store.setRebateCondition(99);
        store.setRebatePrice(new BigDecimal("12.00"));
        store.setStartTime("10:00");
        store.setEndTime("22:00");
        store.setLeftNumber(1);
        return store;
    }
}
