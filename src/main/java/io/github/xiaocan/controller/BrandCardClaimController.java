package io.github.xiaocan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.BaseResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;
import io.github.xiaocan.service.BrandCardClaimService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand-card")
public class BrandCardClaimController {
    @Resource
    private BrandCardClaimService brandCardClaimService;

    @GetMapping("/config")
    public BaseResult<BrandCardClaimConfigVO> getConfig() {
        return BaseResult.ok(brandCardClaimService.getConfig());
    }

    @PostMapping("/config")
    public BaseResult<Void> saveConfig(@Valid @RequestBody BrandCardClaimConfigDTO dto) {
        brandCardClaimService.saveConfig(dto);
        return BaseResult.ok();
    }

    @PostMapping("/claim-now")
    public BaseResult<BrandCardClaimExecutionResult> claimNow() {
        return BaseResult.ok(brandCardClaimService.claimNow());
    }

    @PostMapping("/history/page")
    public BaseResult<Page<BrandCardClaimHistoryVO>> pageHistory(
            @RequestBody(required = false) BrandCardClaimHistoryQueryDTO dto) {
        return BaseResult.ok(brandCardClaimService.pageHistory(
                dto == null ? new BrandCardClaimHistoryQueryDTO() : dto));
    }
}
