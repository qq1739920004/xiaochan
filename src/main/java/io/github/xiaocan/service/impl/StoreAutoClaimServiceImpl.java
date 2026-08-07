package io.github.xiaocan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.mapper.BrandCardClaimConfigMapper;
import io.github.xiaocan.mapper.StoreAutoClaimHistoryMapper;
import io.github.xiaocan.mapper.XiaochanAccountMapper;
import io.github.xiaocan.model.StoreAutoClaimAttempt;
import io.github.xiaocan.model.StoreAutoClaimConfig;
import io.github.xiaocan.model.StoreAutoClaimRequest;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.StoreExtNotifyConfig;
import io.github.xiaocan.model.dto.StoreAutoClaimHistoryQueryDTO;
import io.github.xiaocan.model.entity.BrandCardClaimConfigEntity;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.entity.StoreAutoClaimHistoryEntity;
import io.github.xiaocan.model.entity.UserEntity;
import io.github.xiaocan.model.entity.XiaochanAccountEntity;
import io.github.xiaocan.model.vo.StoreAutoClaimHistoryVO;
import io.github.xiaocan.service.StoreAutoClaimClient;
import io.github.xiaocan.service.StoreAutoClaimExecutor;
import io.github.xiaocan.service.StoreAutoClaimService;
import io.github.xiaocan.service.UserService;
import io.github.xiaocan.utils.PageConvertUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class StoreAutoClaimServiceImpl implements StoreAutoClaimService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private final BrandCardClaimConfigMapper brandCardClaimConfigMapper;
    private final StoreAutoClaimHistoryMapper historyMapper;
    private final StoreAutoClaimClient claimClient;

    @Resource
    private XiaochanAccountMapper accountMapper;

    @Resource
    private UserService userService;

    public StoreAutoClaimServiceImpl(BrandCardClaimConfigMapper brandCardClaimConfigMapper,
                                     StoreAutoClaimHistoryMapper historyMapper,
                                     StoreAutoClaimClient claimClient) {
        this.brandCardClaimConfigMapper = brandCardClaimConfigMapper;
        this.historyMapper = historyMapper;
        this.claimClient = claimClient;
    }

    @Override
    public StoreAutoClaimResult execute(MonitorConfigEntity monitorConfig,
                                        LocationEntity location, StoreInfo candidate) {
        LocalDateTime startTime = LocalDateTime.now(APP_ZONE);
        BrandCardClaimConfigEntity credentials = findCredentials(monitorConfig);
        if (credentials == null || credentials.getSilkId() == null || credentials.getXVayne() == null
                || !StringUtils.hasText(credentials.getXSivir())) {
            StoreAutoClaimResult result = new StoreAutoClaimResult(
                    0, false, null, "请先配置有效的 silk_id、X-Vayne 与 X-Sivir", null,
                    StoreAutoClaimStopReason.MISSING_CREDENTIALS);
            saveHistory(monitorConfig, credentials, candidate, startTime, result);
            return result;
        }

        StoreAutoClaimRequest request = buildRequest(credentials, location, candidate, null);
        StoreAutoClaimExecutor executor = new StoreAutoClaimExecutor(claimClient, duration -> { }, () -> java.time.Duration.ZERO);
        StoreAutoClaimResult result = executor.executeOnce(request);
        saveHistory(monitorConfig, credentials, candidate, startTime, result);
        return result;
    }

    @Override
    public Page<StoreAutoClaimHistoryVO> pageHistory(StoreAutoClaimHistoryQueryDTO query) {
        UserEntity user = userService.getByCurrentRequest();
        LambdaQueryWrapper<StoreAutoClaimHistoryEntity> wrapper = new LambdaQueryWrapper<StoreAutoClaimHistoryEntity>()
                .eq(StoreAutoClaimHistoryEntity::getUserId, user.getId())
                .orderByDesc(StoreAutoClaimHistoryEntity::getId);
        if (query.getMonitorConfigId() != null) {
            wrapper.eq(StoreAutoClaimHistoryEntity::getMonitorConfigId, query.getMonitorConfigId());
        }
        Page<StoreAutoClaimHistoryEntity> page = historyMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageConvertUtil.convert(page, StoreAutoClaimHistoryVO.class);
    }

    private BrandCardClaimConfigEntity findCredentials(MonitorConfigEntity monitorConfig) {
        Integer userId = monitorConfig.getUserId();
        Integer accountId = readAccountId(monitorConfig);
        if (accountMapper != null) {
            LambdaQueryWrapper<XiaochanAccountEntity> wrapper = new LambdaQueryWrapper<XiaochanAccountEntity>()
                    .eq(XiaochanAccountEntity::getUserId, userId)
                    .eq(XiaochanAccountEntity::getEnabled, true);
            if (accountId != null) {
                wrapper.eq(XiaochanAccountEntity::getId, accountId);
            }
            XiaochanAccountEntity account = accountMapper.selectOne(wrapper.orderByAsc(XiaochanAccountEntity::getId));
            if (account != null) {
                return toCredentials(account);
            }
            if (accountId != null) {
                return null;
            }
        }
        return brandCardClaimConfigMapper.selectOne(new LambdaQueryWrapper<BrandCardClaimConfigEntity>()
                .eq(BrandCardClaimConfigEntity::getUserId, userId));
    }

    private Integer readAccountId(MonitorConfigEntity monitorConfig) {
        if (monitorConfig.getExtConfig() == null) return null;
        com.alibaba.fastjson2.JSONObject root = JSON.parseObject(monitorConfig.getExtConfig());
        com.alibaba.fastjson2.JSONObject auto = root == null ? null : root.getJSONObject("autoClaimConfig");
        return auto == null ? null : auto.getInteger("accountId");
    }

    private BrandCardClaimConfigEntity toCredentials(XiaochanAccountEntity account) {
        BrandCardClaimConfigEntity credentials = new BrandCardClaimConfigEntity();
        credentials.setId(account.getId());
        credentials.setUserId(account.getUserId());
        credentials.setAccountId(account.getId());
        credentials.setSilkId(account.getSilkId());
        credentials.setXVayne(account.getXVayne());
        credentials.setXSivir(account.getXSivir());
        return credentials;
    }

    private StoreAutoClaimRequest buildRequest(BrandCardClaimConfigEntity credentials,
                                               LocationEntity location, StoreInfo candidate,
                                               Long redpackId) {
        return new StoreAutoClaimRequest(
                credentials.getSilkId(),
                credentials.getXSivir(),
                credentials.getXVayne(),
                location.getCityCode(),
                location.getLongitude(),
                location.getLatitude(),
                Long.valueOf(candidate.getPromotionId()),
                candidate.getType(),
                redpackId,
                candidate.getStoreId(),
                candidate.getPrice(),
                candidate.getRebatePrice()
        );
    }

    private void saveHistory(MonitorConfigEntity monitorConfig, BrandCardClaimConfigEntity credentials,
                             StoreInfo candidate, LocalDateTime startTime, StoreAutoClaimResult result) {
        StoreAutoClaimHistoryEntity history = new StoreAutoClaimHistoryEntity();
        history.setUserId(monitorConfig.getUserId());
        history.setMonitorConfigId(monitorConfig.getId());
        history.setBrandConfigId(credentials == null ? null : credentials.getId());
        history.setAccountId(credentials == null ? null : credentials.getAccountId());
        history.setStoreId(candidate.getStoreId());
        history.setStoreName(candidate.getName());
        history.setPromotionId(parseLong(candidate.getPromotionId()));
        history.setStorePlatform(candidate.getType());
        history.setRebateCondition(candidate.getRebateCondition());
        history.setRebatePrice(candidate.getRebatePrice());
        history.setActivityStartTime(candidate.getStartTime());
        history.setActivityEndTime(candidate.getEndTime());
        history.setStartTime(startTime);
        history.setEndTime(LocalDateTime.now(APP_ZONE));
        history.setRequestCount(result.attempts());
        history.setSuccess(result.success());
        history.setPromotionOrderId(result.promotionOrderId());
        history.setResultCode(result.resultCode());
        history.setResultMsg(result.resultMessage());
        history.setStopReason(result.stopReason().name());
        historyMapper.insert(history);
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
