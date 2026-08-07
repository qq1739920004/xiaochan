package io.github.xiaocan.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaocan.mapper.BrandCardClaimConfigMapper;
import io.github.xiaocan.mapper.StoreAutoClaimHistoryMapper;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class StoreAutoClaimServiceImpl implements StoreAutoClaimService {
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_MIN_INTERVAL_MS = 150;
    private static final int DEFAULT_MAX_INTERVAL_MS = 350;

    private final BrandCardClaimConfigMapper brandCardClaimConfigMapper;
    private final StoreAutoClaimHistoryMapper historyMapper;
    private final StoreAutoClaimClient claimClient;

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
        LocalDateTime startTime = LocalDateTime.now();
        BrandCardClaimConfigEntity credentials = findCredentials(monitorConfig.getUserId());
        if (credentials == null || credentials.getSilkId() == null || credentials.getXVayne() == null
                || !StringUtils.hasText(credentials.getXSivir())) {
            StoreAutoClaimResult result = new StoreAutoClaimResult(
                    0, false, null, "请先配置有效的 silk_id、X-Vayne 与 X-Sivir", null,
                    StoreAutoClaimStopReason.MISSING_CREDENTIALS);
            saveHistory(monitorConfig, credentials, candidate, startTime, result);
            return result;
        }

        StoreExtNotifyConfig ext = JSON.parseObject(monitorConfig.getExtConfig(), StoreExtNotifyConfig.class);
        StoreAutoClaimConfig autoConfig = ext == null || ext.getAutoClaimConfig() == null
                ? new StoreAutoClaimConfig() : ext.getAutoClaimConfig();
        StoreAutoClaimRequest request = buildRequest(credentials, location, candidate, null);
        Long redpackId = null;
        try {
            redpackId = claimClient.findAvailableRedpackId(request);
        } catch (Exception e) {
            // 红包是可选增强字段，预取失败不能阻断主抢单流程。
            log.warn("预取红包失败，继续尝试主抢单 configId={}, promotionId={}",
                    monitorConfig.getId(), candidate.getPromotionId(), e);
        }
        request = buildRequest(credentials, location, candidate, redpackId);

        int maxAttempts = positiveOrDefault(autoConfig.getMaxAttempts(), DEFAULT_MAX_ATTEMPTS);
        int minInterval = clampInterval(autoConfig.getMinIntervalMs(), DEFAULT_MIN_INTERVAL_MS);
        int maxInterval = clampInterval(autoConfig.getMaxIntervalMs(), DEFAULT_MAX_INTERVAL_MS);
        if (minInterval > maxInterval) {
            minInterval = DEFAULT_MIN_INTERVAL_MS;
            maxInterval = DEFAULT_MAX_INTERVAL_MS;
        }
        int finalMinInterval = minInterval;
        int finalMaxInterval = maxInterval;
        StoreAutoClaimExecutor executor = new StoreAutoClaimExecutor(
                claimClient,
                duration -> Thread.sleep(duration.toMillis()),
                () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(
                        finalMinInterval, finalMaxInterval + 1L))
        );
        StoreAutoClaimResult result = executor.execute(request, maxAttempts,
                Duration.ofMillis(finalMinInterval), Duration.ofMillis(finalMaxInterval));
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

    private BrandCardClaimConfigEntity findCredentials(Integer userId) {
        return brandCardClaimConfigMapper.selectOne(new LambdaQueryWrapper<BrandCardClaimConfigEntity>()
                .eq(BrandCardClaimConfigEntity::getUserId, userId));
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
        history.setStoreId(candidate.getStoreId());
        history.setStoreName(candidate.getName());
        history.setPromotionId(parseLong(candidate.getPromotionId()));
        history.setStorePlatform(candidate.getType());
        history.setRebateCondition(candidate.getRebateCondition());
        history.setRebatePrice(candidate.getRebatePrice());
        history.setActivityStartTime(candidate.getStartTime());
        history.setActivityEndTime(candidate.getEndTime());
        history.setStartTime(startTime);
        history.setEndTime(LocalDateTime.now());
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

    private int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : Math.min(value, 30);
    }

    private int clampInterval(Integer value, int fallback) {
        return value == null ? fallback : Math.max(100, Math.min(value, 400));
    }
}
