package io.github.xiaocan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.dto.StoreAutoClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.StoreAutoClaimHistoryVO;

public interface StoreAutoClaimService {
    StoreAutoClaimResult execute(MonitorConfigEntity monitorConfig, LocationEntity location,
                                 StoreInfo candidate);

    Page<StoreAutoClaimHistoryVO> pageHistory(StoreAutoClaimHistoryQueryDTO query);
}
