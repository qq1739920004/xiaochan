package io.github.xiaocan.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StoreAutoClaimHistoryVO {
    private Long id;
    private Integer monitorConfigId;
    private Integer storeId;
    private String storeName;
    private Long promotionId;
    private Integer storePlatform;
    private Integer rebateCondition;
    private BigDecimal rebatePrice;
    private String activityStartTime;
    private String activityEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer requestCount;
    private Boolean success;
    private Long promotionOrderId;
    private Integer resultCode;
    private String resultMsg;
    private String stopReason;
}
