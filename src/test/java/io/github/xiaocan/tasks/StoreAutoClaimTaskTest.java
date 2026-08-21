package io.github.xiaocan.tasks;

import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.service.LocationService;
import io.github.xiaocan.service.MonitoryConfigService;
import io.github.xiaocan.service.StoreAutoClaimService;
import io.github.xiaocan.service.XiaoChanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreAutoClaimTaskTest {

    @BeforeEach
    void returnsNoConfigsForTheOtherMonitorTypeByDefault() {
        lenient().when(configService.list(MonitorTypeEnums.STORE_ACTIVITY, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of());
        lenient().when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of());
    }

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
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, true, 0, "抢单成功", 888L, StoreAutoClaimStopReason.SUCCESS));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(config, location, higherReview());
    }

    @Test
    void schedulesActivityBeforeItsStartInsteadOfClaimingEarly() {
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

        verifyNoInteractions(claimService);
        verify(taskScheduler).schedule(any(Runnable.class), any(java.util.Date.class));
    }

    @Test
    void keywordMonitorClaimsExactMatchingStoreAfterActivityStarts() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(activeStore("测试门店", "store-1", 99, "12.00")));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, true, 0, "抢单成功", 888L, StoreAutoClaimStopReason.SUCCESS));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(eq(config), eq(location), any(StoreInfo.class));
    }

    @Test
    void claimsEveryActivityAcceptedByTheNotificationMonitor() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        StoreInfo discovered = activeStore("通知通过的门店", "store-1", 99, "12.00");
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, true, 0, "抢单成功", 888L, StoreAutoClaimStopReason.SUCCESS));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.claimDiscovered(config, location, List.of(discovered),
                java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(config, location, discovered);
    }

    @Test
    void claimsNotifiedKeywordActivityEvenWhenTheStoreNameContainsBranchSuffix() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        StoreInfo discovered = activeStore("测试门店（徐家汇店）", "store-1", 99, "12.00");
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, true, 0, "抢单成功", 888L, StoreAutoClaimStopReason.SUCCESS));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.claimDiscovered(config, location, List.of(discovered),
                java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(config, location, discovered);
    }

    @Test
    void doesNotRetrySameActivityAfterOneShotTransportFailure() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(activeStore("测试门店", "store-1", 99, "12.00")));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, false, null, "网络超时",
                        null, StoreAutoClaimStopReason.REQUEST_FAILED));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0, 2));

        verify(claimService)
                .execute(eq(config), eq(location), any(StoreInfo.class));
    }

    @Test
    void doesNotRepeatTerminalActivityOnNextPoll() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(activeStore("测试门店", "store-1", 99, "12.00")));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, false, 40021, "已抢完",
                        null, StoreAutoClaimStopReason.SOLD_OUT_OR_EXPIRED));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0, 2));

        verify(claimService).execute(eq(config), eq(location), any(StoreInfo.class));
    }

    @Test
    void doesNotRepeatInvalidAuthActivityOnNextPoll() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(activeStore("测试门店", "store-1", 2, "16.00")));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, false, 401, "HTTP 状态码: 401",
                        null, StoreAutoClaimStopReason.AUTH_INVALID));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0, 2));

        verify(claimService).execute(eq(config), eq(location), any(StoreInfo.class));
    }

    @Test
    void keywordMonitorDoesNotClaimDifferentStoreName() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(activeStore("其他测试门店", "store-2", 99, "20.00")));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verifyNoInteractions(claimService, taskScheduler);
    }

    @Test
    void keywordMonitorClaimsStoreWithBranchSuffixWhenIdentityIsUnambiguous() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        StoreInfo store = activeStore("测试门店（分店）", "store-1", 99, "20.00");
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(store));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskScheduler).execute(any(Runnable.class));
        when(claimService.execute(any(), any(), any()))
                .thenReturn(new StoreAutoClaimResult(1, true, 0, "抢单成功", 888L, StoreAutoClaimStopReason.SUCCESS));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verify(claimService).execute(config, location, store);
    }

    @Test
    void keywordMonitorDoesNotClaimAmbiguousSameNameStores() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(
                        activeStore("测试门店", "store-1", 99, "20.00"),
                        activeStore("测试门店", "store-2", 2, "30.00")));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verifyNoInteractions(claimService, taskScheduler);
    }

    @Test
    void keywordMonitorDoesNotClaimMultipleStoresWithoutIdentity() {
        MonitorConfigEntity config = keywordConfig();
        LocationEntity location = location();
        StoreInfo first = activeStore("测试门店", null, 99, "20.00");
        StoreInfo second = activeStore("测试门店", null, 2, "30.00");
        first.setStoreId(null);
        second.setStoreId(null);
        when(configService.list(MonitorTypeEnums.STORE_KEYWORD, MonitorConfigStatusEnums.ENABLE))
                .thenReturn(List.of(config));
        when(locationService.getById(9L)).thenReturn(location);
        when(xiaoChanService.searchList("测试门店", 310114, "121.4", "31.2"))
                .thenReturn(List.of(first, second));

        StoreAutoClaimTask task = new StoreAutoClaimTask(
                configService, locationService, xiaoChanService, claimService, taskScheduler);
        task.pollAt(java.time.LocalDateTime.of(2026, 8, 3, 10, 0));

        verifyNoInteractions(claimService, taskScheduler);
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

    private MonitorConfigEntity keywordConfig() {
        MonitorConfigEntity config = config();
        config.setType(MonitorTypeEnums.STORE_KEYWORD);
        config.setExtConfig("{\"keyword\":\"测试门店\",\"limitDistance\":false,"
                + "\"autoClaimConfig\":{\"enabled\":true}}");
        return config;
    }

    private LocationEntity location() {
        LocationEntity location = new LocationEntity();
        location.setId(9L);
        location.setCityCode(310114);
        location.setLongitude("121.4");
        location.setLatitude("31.2");
        return location;
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

    private StoreInfo activeStore(String name, String uniqId, int condition, String rebate) {
        StoreInfo store = baseStore(condition, rebate);
        store.setName(name);
        store.setUniqId(uniqId);
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
        store.setDistance("100");
        return store;
    }
}
