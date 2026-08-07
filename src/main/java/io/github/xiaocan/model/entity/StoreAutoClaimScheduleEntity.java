package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("store_auto_claim_schedule")
public class StoreAutoClaimScheduleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private Integer monitorConfigId;
    private Integer accountId;
    private LocalDate runDate;
    private String storeUniqId;
    private Long promotionId;
    private Integer rebateCondition;
    private LocalDateTime scheduledAt;
    private String status;
    private LocalDateTime discoveredAt;
    private LocalDateTime executedAt;
    private Boolean requestSent;
    private String resultMsg;
}
