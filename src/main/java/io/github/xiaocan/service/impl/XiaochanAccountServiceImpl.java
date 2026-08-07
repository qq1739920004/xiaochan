package io.github.xiaocan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaocan.config.BusinessException;
import io.github.xiaocan.http.XiaochanHttp;
import io.github.xiaocan.mapper.XiaochanAccountMapper;
import io.github.xiaocan.model.XiaochanAccountSnapshot;
import io.github.xiaocan.model.dto.XiaochanAccountDTO;
import io.github.xiaocan.model.entity.UserEntity;
import io.github.xiaocan.model.entity.XiaochanAccountEntity;
import io.github.xiaocan.model.vo.XiaochanAccountVO;
import io.github.xiaocan.service.UserService;
import io.github.xiaocan.service.XiaochanAccountService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class XiaochanAccountServiceImpl implements XiaochanAccountService {
    @Resource
    private XiaochanAccountMapper accountMapper;
    @Resource
    private UserService userService;

    @Override
    public List<XiaochanAccountVO> listAccounts(boolean refresh) {
        Integer userId = currentUser().getId();
        List<XiaochanAccountEntity> accounts = accountMapper.selectList(new LambdaQueryWrapper<XiaochanAccountEntity>()
                .eq(XiaochanAccountEntity::getUserId, userId)
                .orderByAsc(XiaochanAccountEntity::getId));
        if (refresh) {
            accounts = accounts.stream().map(account -> {
                if (Boolean.TRUE.equals(account.getEnabled())) {
                    return refreshEntity(account);
                }
                return account;
            }).toList();
        }
        return accounts.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public XiaochanAccountVO saveAccount(Integer id, XiaochanAccountDTO dto) {
        UserEntity user = currentUser();
        XiaochanAccountEntity account = id == null ? null : find(id, user.getId());
        boolean creating = account == null;
        if (!creating && !StringUtils.hasText(dto.getXSivir())) {
            dto.setXSivir(account.getXSivir());
        }
        if (!StringUtils.hasText(dto.getXSivir())) {
            throw new BusinessException("首次保存必须填写 X-Sivir");
        }
        if (creating) {
            account = new XiaochanAccountEntity();
            account.setUserId(user.getId());
            account.setCreateTime(LocalDateTime.now());
            account.setEnabled(true);
        }
        account.setAccountName(dto.getAccountName().trim());
        account.setSilkId(dto.getSilkId());
        account.setXVayne(dto.getXVayne());
        account.setXSivir(dto.getXSivir().trim());
        account.setEnabled(dto.getEnabled());
        account.setUpdateTime(LocalDateTime.now());
        if (creating) {
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }
        return toVO(account);
    }

    @Override
    @Transactional
    public XiaochanAccountVO refreshAccount(Integer id) {
        XiaochanAccountEntity account = find(id, currentUser().getId());
        return toVO(refreshEntity(account));
    }

    @Override
    @Transactional
    public void disableAccount(Integer id) {
        XiaochanAccountEntity account = find(id, currentUser().getId());
        account.setEnabled(false);
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
    }

    @Override
    public XiaochanAccountVO findOwnedAccount(Integer accountId, Integer userId) {
        XiaochanAccountEntity account = accountMapper.selectOne(new LambdaQueryWrapper<XiaochanAccountEntity>()
                .eq(XiaochanAccountEntity::getId, accountId)
                .eq(XiaochanAccountEntity::getUserId, userId)
                .eq(XiaochanAccountEntity::getEnabled, true));
        return account == null ? null : toVO(account);
    }

    public XiaochanAccountEntity findEntity(Integer accountId, Integer userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<XiaochanAccountEntity>()
                .eq(XiaochanAccountEntity::getId, accountId)
                .eq(XiaochanAccountEntity::getUserId, userId)
                .eq(XiaochanAccountEntity::getEnabled, true));
    }

    private XiaochanAccountEntity refreshEntity(XiaochanAccountEntity account) {
        try {
            XiaochanAccountSnapshot snapshot = XiaochanHttp.getAccountSnapshot(
                    account.getSilkId(), account.getXVayne(), account.getXSivir());
            account.setUpstreamUserId(snapshot.getUpstreamUserId());
            account.setNickname(snapshot.getNickname());
            account.setPhone(snapshot.getPhone());
            account.setVipLevel(snapshot.getVipLevel());
            account.setCardTotal(snapshot.getCardTotal());
            account.setCardActive(snapshot.getCardActive());
            account.setCardExpired(snapshot.getCardExpired());
            account.setRedpackTotal(snapshot.getRedpackTotal());
            account.setMeituanRedpackTotal(snapshot.getMeituanRedpackTotal());
            account.setElemeRedpackTotal(snapshot.getElemeRedpackTotal());
            account.setPlatformRedpackTotal(snapshot.getPlatformRedpackTotal());
            account.setRefreshStatus("OK");
            account.setLastRefreshError(null);
        } catch (Exception e) {
            account.setRefreshStatus("FAILED");
            account.setLastRefreshError(safeErrorMessage(e));
        }
        account.setLastRefreshTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return account;
    }

    private String safeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "账号信息刷新失败";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private XiaochanAccountEntity find(Integer id, Integer userId) {
        XiaochanAccountEntity account = accountMapper.selectOne(new LambdaQueryWrapper<XiaochanAccountEntity>()
                .eq(XiaochanAccountEntity::getId, id)
                .eq(XiaochanAccountEntity::getUserId, userId));
        if (account == null) {
            throw new BusinessException("小蚕账号不存在");
        }
        return account;
    }

    private UserEntity currentUser() {
        return userService.getByCurrentRequest();
    }

    private XiaochanAccountVO toVO(XiaochanAccountEntity account) {
        XiaochanAccountVO vo = new XiaochanAccountVO();
        vo.setId(account.getId());
        vo.setAccountName(account.getAccountName());
        vo.setSilkId(account.getSilkId());
        vo.setXVayne(account.getXVayne());
        vo.setXSivirMasked(mask(account.getXSivir()));
        vo.setEnabled(account.getEnabled());
        vo.setUpstreamUserId(account.getUpstreamUserId());
        vo.setNickname(account.getNickname());
        vo.setPhoneMasked(maskPhone(account.getPhone()));
        vo.setVipLevel(account.getVipLevel());
        vo.setCardTotal(account.getCardTotal());
        vo.setCardActive(account.getCardActive());
        vo.setCardExpired(account.getCardExpired());
        vo.setRedpackTotal(account.getRedpackTotal());
        vo.setMeituanRedpackTotal(account.getMeituanRedpackTotal());
        vo.setElemeRedpackTotal(account.getElemeRedpackTotal());
        vo.setPlatformRedpackTotal(account.getPlatformRedpackTotal());
        vo.setRefreshStatus(account.getRefreshStatus());
        vo.setLastRefreshError(account.getLastRefreshError());
        vo.setLastRefreshTime(account.getLastRefreshTime());
        return vo;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.length() <= 10 ? "********" : value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value)) return null;
        if (value.length() <= 7) return "****";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
