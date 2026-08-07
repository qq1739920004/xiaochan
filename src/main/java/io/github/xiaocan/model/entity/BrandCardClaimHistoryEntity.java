package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_card_claim_history")
public class BrandCardClaimHistoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private Integer configId;
    private Integer accountId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer requestCount;
    private Boolean success;
    private Integer resultCode;
    private String resultMsg;
    private String stopReason;
}
