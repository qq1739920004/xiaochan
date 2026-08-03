package io.github.xiaocan.tasks;

import io.github.xiaocan.model.StoreAutoClaimConfig;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.service.LocationService;
import io.github.xiaocan.service.MonitoryConfigService;
import io.github.xiaocan.service.StoreAutoClaimService;
import io.github.xiaocan.service.XiaoChanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAutoClaimTaskTest {
    @Mock
    private MonitoryConfigService configService;
    @Mock
    private LocationService locationService;
    @Mock
    private XiaoChanService xiaoChanService;
    @Mock
    private StoreAutoClaimService claimService;
    @Mock
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    void waitsForActivityStartAndChoosesHigherReviewRebate() {
        MonitorConfigEntity config = config();
        LocationEntity location = new LocationEntity();
        location.setId(9L);
        location.setCityCode(310114);
        location.setLongitude("121.4");
        location.setLatitude("31.2");
        when(configService.list(MonitorTypeEnums.STORE_ACTIVITY, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(noReview(), higherReview()));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(config, location, higherReview());
    }

    @Test
    void doesNotClaimBeforeActivityStart() {
        MonitorConfigEntity config = config();
        LocationEntity location = new LocationEntity();
        location.setId(9L);
        location.setCityCode(310114);
        location.setLongitude("121.4");
        location.setLatitude("31.2");
        when(configService.list(MonitorTypeEnums.STORE_ACTIVITY, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(noReview()));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 9, 59));

        org.mockito.Mockito.verifyNoInteractions(claimService, taskScheduler);
    }

    private MonitorConfigEntity config() {
        MonitorConfigEntity config = new MonitorConfigEntity();
        config.setId(5);
        config.setLocationId(9L);
        config.setType(MonitorTypeEnums.STORE_ACTIVITY);
        config.setStatus(MonitorConfigStatusEnums.ENABLE);
        config.setStartHour(0);
        config.setEndHour(23);
        config.setWeeks("1,2,3,4,5,6,7");
        config.setExtConfig("{\"storeInfo\":{\"name\":\"测试门店\",\"uniqId\":\"store-1\"},"
                + "\"autoClaimConfig\":{\"enabled\":true}}");
        return config;
    }

    private StoreInfo noReview() {
        StoreInfo store = baseStore(99, "10.00");
        store.setStartTime("10:00");
        store.setEndTime("22:00");
        return store;
    }

    private StoreInfo higherReview() {
        StoreInfo store = baseStore(2, "12.00");
        store.setStartTime("10:00");
        store.setEndTime("22:00");
        return store;
    }

    private StoreInfo baseStore(int condition, String rebate) {
        StoreInfo store = new StoreInfo();
        store.setStoreId(123);
        store.setUniqId("store-1");
        store.setName("测试门店");
        store.setPromotionId("456");
        store.setType(1);
        store.setRebateCondition(condition);
        store.setRebatePrice(new BigDecimal(rebate));
        store.setLeftNumber(1);
        return store;
    }
}
