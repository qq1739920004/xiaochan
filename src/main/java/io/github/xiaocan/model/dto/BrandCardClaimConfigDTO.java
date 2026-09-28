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
    @Max(value = 10000, message = "最大请求次数不能超过 10000")
    private Integer maxAttempts = 5;
    @Min(value = 1, message = "最小请求间隔不能低于 1ms")
    @Max(value = 60000, message = "最小请求间隔不能超过 60000ms")
    private Integer minIntervalMs = 100;
    @Min(value = 1, message = "最大请求间隔不能低于 1ms")
    @Max(value = 60000, message = "最大请求间隔不能超过 60000ms")
    private Integer maxIntervalMs = 400;
    @Min(value = 0, message = "首次请求延迟不能小于 0ms")
    @Max(value = 60000, message = "首次请求延迟不能超过 60000ms")
    private Integer startDelayMs = 3000;
    @Min(value = 100, message = "执行窗口不能低于 100ms")
    @Max(value = 60000, message = "执行窗口不能超过 60000ms")
    private Integer windowDurationMs = 2000;
    @Min(value = 1, message = "最大并发至少为 1")
    private Integer maxInFlight = 5;
    @Min(value = 100, message = "请求超时不能低于 100ms")
    @Max(value = 60000, message = "请求超时不能超过 60000ms")
    private Integer requestTimeoutMs = 1000;
}
