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
        store.setEndTime("24:00");
        assertTrue(StoreAutoClaimTask.isActiveAt(store, java.time.LocalTime.of(23, 59)));
    }

    @Test
    void acceptsStoreAtFiveKilometersAndRejectsStoreBeyondIt() {
        StoreInfo within = new StoreInfo();
        within.setDistance("5000");
        StoreInfo beyond = new StoreInfo();
        beyond.setDistance("5001");

        assertTrue(StoreAutoClaimTask.withinDistanceForTest(within));
        org.junit.jupiter.api.Assertions.assertFalse(StoreAutoClaimTask.withinDistanceForTest(beyond));
    }
}
