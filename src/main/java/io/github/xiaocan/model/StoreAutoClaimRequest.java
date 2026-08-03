package io.github.xiaocan.model;

public record StoreAutoClaimRequest(
        Long silkId,
        String xSivir,
        Integer cityCode,
        String longitude,
        String latitude,
        Long promotionId,
        Integer storePlatform,
        Long redpackId
) {
}
