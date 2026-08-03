package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("store_auto_claim_history")
public class StoreAutoClaimHistoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private Integer monitorConfigId;
    private Integer brandConfigId;
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
