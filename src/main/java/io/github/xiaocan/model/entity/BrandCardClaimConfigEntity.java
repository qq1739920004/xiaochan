package io.github.xiaocan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_card_claim_config")
public class BrandCardClaimConfigEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Long silkId;
    private Long xVayne;
    private String xSivir;
    private Boolean enabled;
    private String cron;
    private Integer maxAttempts;
    private Integer minIntervalMs;
    private Integer maxIntervalMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
