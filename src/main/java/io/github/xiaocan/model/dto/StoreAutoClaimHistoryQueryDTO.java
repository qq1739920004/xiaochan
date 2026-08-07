package io.github.xiaocan.model.dto;

import lombok.Data;

@Data
public class StoreAutoClaimHistoryQueryDTO {
    private Integer monitorConfigId;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
