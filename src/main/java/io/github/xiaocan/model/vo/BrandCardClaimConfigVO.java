package io.github.xiaocan.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BrandCardClaimConfigVO {
    private Integer accountId;
    private Long silkId;
    @JsonProperty("xVayne")
    private Long xVayne;
    @JsonProperty("xSivirMasked")
    private String xSivirMasked;
    private Boolean enabled;
    private String cron;
    private Integer maxAttempts;
    private Integer minIntervalMs;
    private Integer maxIntervalMs;
}
