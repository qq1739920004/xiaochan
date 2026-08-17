package io.github.xiaocan.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandCardClaimAttemptHistoryVO {
    private Long id;
    private Long historyId;
    private Integer sequence;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "Asia/Shanghai")
    private LocalDateTime requestTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "Asia/Shanghai")
    private LocalDateTime responseTime;
    private Long durationMs;
    private Integer resultCode;
    private String resultMsg;
    private Boolean retryable;
    private String stopReason;
    private Boolean success;
}
