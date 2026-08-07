package io.github.xiaocan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;

import java.util.List;

public interface BrandCardClaimService {
    BrandCardClaimConfigVO getConfig();
    BrandCardClaimConfigVO getConfig(Integer accountId);
    List<BrandCardClaimConfigVO> listConfigs();
    void saveConfig(BrandCardClaimConfigDTO dto);
    void saveConfig(Integer accountId, BrandCardClaimConfigDTO dto);
    BrandCardClaimExecutionResult claimNow();
    BrandCardClaimExecutionResult claimNow(Integer accountId);
    Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto);
    Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto, Integer accountId);
    void runScheduledClaims();
}
