package io.github.xiaocan.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandCardClaimHistoryVO {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer requestCount;
    private Boolean success;
    private Integer resultCode;
    private String resultMsg;
    private String stopReason;
}
