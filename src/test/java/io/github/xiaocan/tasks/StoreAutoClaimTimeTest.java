package io.github.xiaocan.tasks;

import io.github.xiaocan.model.StoreInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreAutoClaimTimeTest {

    @Test
    void treatsAnActivityEndingAt2359AsActiveDuringTheDay() {
        StoreInfo store = new StoreInfo();
        store.setStartTime("00:00");
        store.setEndTime("23:59");

        assertTrue(StoreAutoClaimTask.isActiveAt(store, java.time.LocalTime.of(17, 53)));
    }
}
