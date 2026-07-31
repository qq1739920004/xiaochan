package io.github.xiaocan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.xiaocan.config.BusinessException;
import io.github.xiaocan.http.XiaochanHttp;
import io.github.xiaocan.mapper.BrandCardClaimConfigMapper;
import io.github.xiaocan.mapper.BrandCardClaimHistoryMapper;
import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.entity.BrandCardClaimConfigEntity;
import io.github.xiaocan.model.entity.BrandCardClaimHistoryEntity;
import io.github.xiaocan.model.entity.UserEntity;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;
import io.github.xiaocan.service.BrandCardClaimExecutor;
import io.github.xiaocan.service.BrandCardClaimService;
import io.github.xiaocan.service.UserService;
import io.github.xiaocan.utils.PageConvertUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class BrandCardClaimServiceImpl extends ServiceImpl<BrandCardClaimConfigMapper, BrandCardClaimConfigEntity>
        implements BrandCardClaimService {
    private static final String DEFAULT_CRON = "58 29 9 * * ?";
    private static final int DEFAULT_MAX_ATTEMPTS = 12;
    private static final int DEFAULT_MIN_INTERVAL_MS = 100;
    private static final int DEFAULT_MAX_INTERVAL_MS = 400;

    @Resource
    private UserService userService;
    @Resource
    private BrandCardClaimHistoryMapper historyMapper;
    @Resource
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public BrandCardClaimConfigVO getConfig() {
        UserEntity user = userService.getByCurrentRequest();
        return toConfigVO(findByUserId(user.getId()));
    }

    @Override
    public void saveConfig(BrandCardClaimConfigDTO dto) {
        if (dto.getMinIntervalMs() > dto.getMaxIntervalMs()) {
            throw new BusinessException("最小请求间隔不能大于最大请求间隔");
        }
        UserEntity user = userService.getByCurrentRequest();
        BrandCardClaimConfigEntity config = findByUserId(user.getId());
        boolean creating = config == null;
        if (creating) {
            if (!StringUtils.hasText(dto.getXSivir())) {
                throw new BusinessException("首次保存必须填写 X-Sivir");
            }
            config = new BrandCardClaimConfigEntity();
            config.setUserId(user.getId());
            config.setCron(DEFAULT_CRON);
        }
        config.setSilkId(dto.getSilkId());
        config.setEnabled(dto.getEnabled());
        config.setMaxAttempts(dto.getMaxAttempts());
        config.setMinIntervalMs(dto.getMinIntervalMs());
        config.setMaxIntervalMs(dto.getMaxIntervalMs());
        if (StringUtils.hasText(dto.getXSivir())) {
            config.setXSivir(dto.getXSivir().trim());
        }
        if (creating) {
            save(config);
        } else {
            updateById(config);
        }
    }

    @Override
    public BrandCardClaimExecutionResult claimNow() {
        BrandCardClaimConfigEntity config = requireCurrentUserConfig();
        LocalDateTime startTime = LocalDateTime.now();
        BrandCardClaimAttemptResult attempt = XiaochanHttp.grabExtraBrandCard(config.getSilkId(), config.getXSivir());
        BrandCardClaimExecutionResult result = attempt.retryable()
                ? new BrandCardClaimExecutionResult(1, false, attempt.code(), attempt.message(), BrandCardClaimStopReason.TIME_WINDOW_EXPIRED)
                : BrandCardClaimExecutionResult.fromAttempt(1, attempt);
        saveHistory(config, startTime, result);
        return result;
    }

    @Override
    public Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto) {
        UserEntity user = userService.getByCurrentRequest();
        Page<BrandCardClaimHistoryEntity> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<BrandCardClaimHistoryEntity> result = historyMapper.selectPage(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimHistoryEntity>()
                        .eq(BrandCardClaimHistoryEntity::getUserId, user.getId())
                        .orderByDesc(BrandCardClaimHistoryEntity::getId));
        return PageConvertUtil.convert(result, BrandCardClaimHistoryVO.class);
    }

    @Override
    public void runScheduledClaims() {
        list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimConfigEntity>()
                .eq(BrandCardClaimConfigEntity::getEnabled, true))
                .forEach(config -> taskScheduler.execute(() -> runAutomaticClaim(config)));
    }

    private void runAutomaticClaim(BrandCardClaimConfigEntity config) {
        LocalDateTime startTime = LocalDateTime.now();
        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                XiaochanHttp::grabExtraBrandCard,
                Clock.systemDefaultZone(),
                duration -> Thread.sleep(duration.toMillis()),
                () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(
                        config.getMinIntervalMs(), config.getMaxIntervalMs() + 1L))
        );
        BrandCardClaimExecutionResult result = executor.executeAutomatic(
                config.getSilkId(),
                config.getXSivir(),
                config.getMaxAttempts(),
                Duration.ofMillis(config.getMinIntervalMs()),
                Duration.ofMillis(config.getMaxIntervalMs())
        );
        saveHistory(config, startTime, result);
        log.info("brand card claim completed: configId={}, attempts={}, reason={}",
                config.getId(), result.attempts(), result.stopReason());
    }

    private BrandCardClaimConfigEntity requireCurrentUserConfig() {
        BrandCardClaimConfigEntity config = findByUserId(userService.getByCurrentRequest().getId());
        if (config == null || !StringUtils.hasText(config.getXSivir())) {
            throw new BusinessException("请先保存 silk_id 与 X-Sivir 配置");
        }
        return config;
    }

    private BrandCardClaimConfigEntity findByUserId(Integer userId) {
        return lambdaQuery().eq(BrandCardClaimConfigEntity::getUserId, userId).one();
    }

    private BrandCardClaimConfigVO toConfigVO(BrandCardClaimConfigEntity config) {
        BrandCardClaimConfigVO vo = new BrandCardClaimConfigVO();
        if (config == null) {
            vo.setEnabled(false);
            vo.setCron(DEFAULT_CRON);
            vo.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
            vo.setMinIntervalMs(DEFAULT_MIN_INTERVAL_MS);
            vo.setMaxIntervalMs(DEFAULT_MAX_INTERVAL_MS);
            return vo;
        }
        vo.setSilkId(config.getSilkId());
        vo.setXSivirMasked(mask(config.getXSivir()));
        vo.setEnabled(config.getEnabled());
        vo.setCron(config.getCron());
        vo.setMaxAttempts(config.getMaxAttempts());
        vo.setMinIntervalMs(config.getMinIntervalMs());
        vo.setMaxIntervalMs(config.getMaxIntervalMs());
        return vo;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= 10) {
            return "********";
        }
        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }

    private void saveHistory(BrandCardClaimConfigEntity config, LocalDateTime startTime,
                             BrandCardClaimExecutionResult result) {
        BrandCardClaimHistoryEntity history = new BrandCardClaimHistoryEntity();
        history.setUserId(config.getUserId());
        history.setConfigId(config.getId());
        history.setStartTime(startTime);
        history.setEndTime(LocalDateTime.now());
        history.setRequestCount(result.attempts());
        history.setSuccess(result.success());
        history.setResultCode(result.resultCode());
        history.setResultMsg(result.resultMessage());
        history.setStopReason(result.stopReason().name());
        historyMapper.insert(history);
    }
}
