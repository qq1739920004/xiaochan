package io.github.xiaocan.model;

import java.math.BigDecimal;

public record StoreAutoClaimRequest(
        Long silkId,
        String xSivir,
        Long xVayne,
        Integer cityCode,
        String longitude,
        String latitude,
        Long promotionId,
        Integer storePlatform,
        Long redpackId,
        Integer storeId,
        BigDecimal storePlatformOrderMoney,
        BigDecimal promotionSilkAmount
) {
    public StoreAutoClaimRequest(Long silkId, String xSivir, Integer cityCode,
                                 String longitude, String latitude, Long promotionId,
                                 Integer storePlatform, Long redpackId) {
        this(silkId, xSivir, null, cityCode, longitude, latitude, promotionId, storePlatform,
                redpackId, null, null, null);
    }

    public StoreAutoClaimRequest(Long silkId, String xSivir, Long xVayne, Integer cityCode,
                                 String longitude, String latitude, Long promotionId,
                                 Integer storePlatform, Long redpackId) {
        this(silkId, xSivir, xVayne, cityCode, longitude, latitude, promotionId, storePlatform,
                redpackId, null, null, null);
    }
}
