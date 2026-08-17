package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_card_claim_attempt_history")
public class BrandCardClaimAttemptHistoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long historyId;
    private Integer userId;
    private Integer configId;
    private Integer accountId;
    private Integer sequence;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private Long durationMs;
    private Integer resultCode;
    private String resultMsg;
    private Boolean retryable;
    private String stopReason;
    private Boolean success;
}
