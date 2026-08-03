package io.github.xiaocan.model;

import lombok.Data;

@Data
public class StoreAutoClaimConfig {
    private Boolean enabled = false;
    private Integer maxAttempts = 5;
    private Integer minIntervalMs = 150;
    private Integer maxIntervalMs = 350;
}
