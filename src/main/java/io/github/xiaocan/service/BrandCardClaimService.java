package io.github.xiaocan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;

public interface BrandCardClaimService {
    BrandCardClaimConfigVO getConfig();
    void saveConfig(BrandCardClaimConfigDTO dto);
    BrandCardClaimExecutionResult claimNow();
    Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto);
    void runScheduledClaims();
}
