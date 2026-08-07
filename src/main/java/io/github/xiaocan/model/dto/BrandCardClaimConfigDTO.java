package io.github.xiaocan.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BrandCardClaimConfigDTO {
    private Integer accountId;
    private String cron;
    @NotNull(message = "silk_id 不能为空")
    private Long silkId;
    @JsonProperty("xVayne")
    @NotNull(message = "X-Vayne 不能为空")
    @Min(value = 1, message = "X-Vayne 必须是正整数")
    private Long xVayne;
    @JsonProperty("xSivir")
    private String xSivir;
    @NotNull(message = "请设置是否启用")
    private Boolean enabled;
    @Min(value = 1, message = "最大请求次数至少为 1")
    @Max(value = 30, message = "最大请求次数不能超过 30")
    private Integer maxAttempts = 12;
    @Min(value = 50, message = "最小请求间隔不能低于 50ms")
    @Max(value = 2000, message = "最小请求间隔不能超过 2000ms")
    private Integer minIntervalMs = 100;
    @Min(value = 50, message = "最大请求间隔不能低于 50ms")
    @Max(value = 2000, message = "最大请求间隔不能超过 2000ms")
    private Integer maxIntervalMs = 400;
}
