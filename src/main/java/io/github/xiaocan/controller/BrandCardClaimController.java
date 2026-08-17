package io.github.xiaocan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.BaseResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;
import io.github.xiaocan.model.vo.BrandCardClaimAttemptHistoryVO;
import io.github.xiaocan.service.BrandCardClaimService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brand-card")
public class BrandCardClaimController {
    @Resource
    private BrandCardClaimService brandCardClaimService;

    @GetMapping("/config")
    public BaseResult<BrandCardClaimConfigVO> getConfig() {
        return BaseResult.ok(brandCardClaimService.getConfig());
    }

    @GetMapping("/configs")
    public BaseResult<List<BrandCardClaimConfigVO>> listConfigs() {
        return BaseResult.ok(brandCardClaimService.listConfigs());
    }

    @PostMapping("/config")
    public BaseResult<Void> saveConfig(@Valid @RequestBody BrandCardClaimConfigDTO dto) {
        brandCardClaimService.saveConfig(dto);
        return BaseResult.ok();
    }

    @GetMapping("/config/{accountId}")
    public BaseResult<BrandCardClaimConfigVO> getConfig(@org.springframework.web.bind.annotation.PathVariable Integer accountId) {
        return BaseResult.ok(brandCardClaimService.getConfig(accountId));
    }

    @PostMapping("/config/{accountId}")
    public BaseResult<Void> saveConfig(@org.springframework.web.bind.annotation.PathVariable Integer accountId,
                                       @Valid @RequestBody BrandCardClaimConfigDTO dto) {
        brandCardClaimService.saveConfig(accountId, dto);
        return BaseResult.ok();
    }

    @PostMapping("/claim-now")
    public BaseResult<BrandCardClaimExecutionResult> claimNow(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer accountId) {
        return BaseResult.ok(accountId == null ? brandCardClaimService.claimNow() : brandCardClaimService.claimNow(accountId));
    }

    @PostMapping("/history/page")
    public BaseResult<Page<BrandCardClaimHistoryVO>> pageHistory(
            @RequestBody(required = false) BrandCardClaimHistoryQueryDTO dto) {
        return BaseResult.ok(brandCardClaimService.pageHistory(
                dto == null ? new BrandCardClaimHistoryQueryDTO() : dto));
    }

    @PostMapping("/history/page/{accountId}")
    public BaseResult<Page<BrandCardClaimHistoryVO>> pageHistoryByAccount(
            @org.springframework.web.bind.annotation.PathVariable Integer accountId,
            @RequestBody(required = false) BrandCardClaimHistoryQueryDTO dto) {
        return BaseResult.ok(brandCardClaimService.pageHistory(
                dto == null ? new BrandCardClaimHistoryQueryDTO() : dto, accountId));
    }

    @GetMapping("/history/{historyId}/attempts")
    public BaseResult<List<BrandCardClaimAttemptHistoryVO>> listAttemptHistory(
            @org.springframework.web.bind.annotation.PathVariable Long historyId) {
        return BaseResult.ok(brandCardClaimService.listAttemptHistory(historyId));
    }
}
