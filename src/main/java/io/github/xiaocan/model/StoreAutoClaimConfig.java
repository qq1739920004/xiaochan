package io.github.xiaocan.model;

import lombok.Data;

@Data
public class StoreAutoClaimConfig {
    private Boolean enabled = false;
    /** 指定使用的小蚕账号；为空时兼容使用默认账号。 */
    private Integer accountId;
    private Integer maxAttempts = 5;
    private Integer minIntervalMs = 150;
    private Integer maxIntervalMs = 350;
}
