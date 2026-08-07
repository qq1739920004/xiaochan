package io.github.xiaocan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.model.BaseResult;
import io.github.xiaocan.model.dto.StoreAutoClaimHistoryQueryDTO;
import io.github.xiaocan.model.vo.StoreAutoClaimHistoryVO;
import io.github.xiaocan.service.StoreAutoClaimService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store-auto-claim")
public class StoreAutoClaimController {
    @Resource
    private StoreAutoClaimService storeAutoClaimService;

    @PostMapping("/history/page")
    public BaseResult<Page<StoreAutoClaimHistoryVO>> pageHistory(
            @RequestBody(required = false) StoreAutoClaimHistoryQueryDTO query) {
        return BaseResult.ok(storeAutoClaimService.pageHistory(
                query == null ? new StoreAutoClaimHistoryQueryDTO() : query));
    }
}
