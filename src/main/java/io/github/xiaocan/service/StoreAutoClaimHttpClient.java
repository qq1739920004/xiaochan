package io.github.xiaocan.service;

import io.github.xiaocan.http.XiaochanHttp;
import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimRequest;
import org.springframework.stereotype.Component;

@Component
public class StoreAutoClaimHttpClient implements StoreAutoClaimClient {
    @Override
    public StoreAutoClaimAttempt claim(StoreAutoClaimRequest request) {
        return XiaochanHttp.grabPromotionQuota(request);
    }

    @Override
    public Long findAvailableRedpackId(StoreAutoClaimRequest request) {
        return XiaochanHttp.getAvailableRedpackId(request);
    }
}
