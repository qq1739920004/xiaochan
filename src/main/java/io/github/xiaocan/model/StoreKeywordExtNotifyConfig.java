package io.github.xiaocan.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 门店关键字监控扩展配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreKeywordExtNotifyConfig extends AbstractExtNotifyConfig {

    /**
     * 门店关键字
     */
    @NotEmpty
    private String keyword;

    /**
     * 是否限制距离（超过5000米的门店过滤掉），默认true
     */
    private Boolean limitDistance = true;

    /**
     * 自动抢单配置。凭证不存于监控配置，而是从共享的小蚕接口凭证中读取。
     */
    private StoreAutoClaimConfig autoClaimConfig = new StoreAutoClaimConfig();
}
