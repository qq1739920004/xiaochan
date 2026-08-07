package io.github.xiaocan.service;

import io.github.xiaocan.model.StoreInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreAutoClaimCandidateSelectorTest {

    private final StoreAutoClaimCandidateSelector selector = new StoreAutoClaimCandidateSelector();

    @Test
    void choosesReviewOnlyWhenItsBestRebateIsStrictlyHigher() {
        StoreInfo selected = selector.select(List.of(store(99, "12.00"), store(2, "15.00"))).orElseThrow();

        assertEquals(2, selected.getRebateCondition());
        assertEquals(new BigDecimal("15.00"), selected.getRebatePrice());
    }

    @Test
    void choosesNoReviewWhenRebatesAreEqual() {
        StoreInfo selected = selector.select(List.of(store(2, "12.00"), store(99, "12.00"))).orElseThrow();

        assertEquals(99, selected.getRebateCondition());
    }

    @Test
    void ignoresSoldOutAndUnsupportedConditions() {
        StoreInfo soldOut = store(99, "50.00");
        soldOut.setLeftNumber(0);

        StoreInfo selected = selector.select(List.of(soldOut, store(1, "40.00"), store(99, "10.00"))).orElseThrow();

        assertEquals(99, selected.getRebateCondition());
        assertEquals(new BigDecimal("10.00"), selected.getRebatePrice());
    }

    private StoreInfo store(int rebateCondition, String rebatePrice) {
        StoreInfo store = new StoreInfo();
        store.setRebateCondition(rebateCondition);
        store.setRebatePrice(new BigDecimal(rebatePrice));
        store.setLeftNumber(1);
        return store;
    }
}
