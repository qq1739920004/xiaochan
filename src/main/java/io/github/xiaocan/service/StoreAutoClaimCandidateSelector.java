package io.github.xiaocan.service;

import io.github.xiaocan.model.StoreInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class StoreAutoClaimCandidateSelector {

    public Optional<StoreInfo> select(List<StoreInfo> storeInfos) {
        StoreInfo review = findBest(storeInfos, 2);
        StoreInfo noReview = findBest(storeInfos, 99);
        if (review != null && (noReview == null || review.getRebatePrice().compareTo(noReview.getRebatePrice()) > 0)) {
            return Optional.of(review);
        }
        if (noReview != null) {
            return Optional.of(noReview);
        }
        return storeInfos.stream()
                .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                .max(Comparator.comparing(StoreInfo::getRebatePrice,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private StoreInfo findBest(List<StoreInfo> storeInfos, int rebateCondition) {
        return storeInfos.stream()
                .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                .filter(store -> store.getRebateCondition() != null && store.getRebateCondition() == rebateCondition)
                .filter(store -> store.getRebatePrice() != null)
                .max(Comparator.comparing(StoreInfo::getRebatePrice))
                .orElse(null);
    }
}
