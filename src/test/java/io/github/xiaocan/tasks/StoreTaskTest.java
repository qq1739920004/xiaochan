package io.github.xiaocan.tasks;

import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.service.StorePushedHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreTaskTest {

    @Test
    void keywordMonitorCanNotifyTheSameStoreForANewPromotion() {
        StorePushedHistoryService pushedHistoryService = mock(StorePushedHistoryService.class);
        when(pushedHistoryService.findByNotifyIdAndActivity(5, 123, 2002, 1, 99))
                .thenReturn(null);

        StoreTask task = new StoreTask();
        ReflectionTestUtils.setField(task, "storePushedHistoryService", pushedHistoryService);

        MonitorConfigEntity config = new MonitorConfigEntity();
        config.setId(5);
        config.setType(MonitorTypeEnums.STORE_KEYWORD);
        config.setStatus(MonitorConfigStatusEnums.ENABLE);
        config.setExtConfig("{\"keyword\":\"测试门店\",\"limitDistance\":false}");

        StoreInfo store = new StoreInfo();
        store.setName("测试门店");
        store.setStoreId(123);
        store.setPromotionId("2002");
        store.setType(1);
        store.setRebateCondition(99);
        store.setLeftNumber(1);

        assertThat(task.filterStoreInfos(config, List.of(store))).containsExactly(store);
    }
}
