package io.github.xiaocan.tasks;

import io.github.xiaocan.model.StoreInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertFalse(StoreAutoClaimTask.withinDistanceForTest(beyond));
    }

    @Test
    void matchesBranchSuffixButRejectsAStoreWithAnotherPrefix() {
        assertTrue(StoreAutoClaimTask.keywordMatchesForAutoClaim(
                "价探PriceTag美妆集合店", "价探PriceTag美妆集合店（嘉定宝龙广场店）"));
        assertFalse(StoreAutoClaimTask.keywordMatchesForAutoClaim(
                "价探PriceTag美妆集合店", "另一家价探PriceTag美妆集合店"));
    }
}
