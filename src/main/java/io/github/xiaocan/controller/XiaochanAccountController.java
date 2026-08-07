package io.github.xiaocan.controller;

import io.github.xiaocan.model.BaseResult;
import io.github.xiaocan.model.dto.XiaochanAccountDTO;
import io.github.xiaocan.model.vo.XiaochanAccountVO;
import io.github.xiaocan.service.XiaochanAccountService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/xiaochan/accounts")
public class XiaochanAccountController {
    @Resource
    private XiaochanAccountService accountService;

    @GetMapping
    public BaseResult<List<XiaochanAccountVO>> list(@RequestParam(defaultValue = "true") boolean refresh) {
        return BaseResult.ok(accountService.listAccounts(refresh));
    }

    @PostMapping
    public BaseResult<XiaochanAccountVO> create(@Valid @RequestBody XiaochanAccountDTO dto) {
        return BaseResult.ok(accountService.saveAccount(null, dto));
    }

    @PutMapping("/{id}")
    public BaseResult<XiaochanAccountVO> update(@PathVariable Integer id,
                                                 @Valid @RequestBody XiaochanAccountDTO dto) {
        return BaseResult.ok(accountService.saveAccount(id, dto));
    }

    @PostMapping("/{id}/refresh")
    public BaseResult<XiaochanAccountVO> refresh(@PathVariable Integer id) {
        return BaseResult.ok(accountService.refreshAccount(id));
    }

    @DeleteMapping("/{id}")
    public BaseResult<Void> disable(@PathVariable Integer id) {
        accountService.disableAccount(id);
        return BaseResult.ok();
    }
}
