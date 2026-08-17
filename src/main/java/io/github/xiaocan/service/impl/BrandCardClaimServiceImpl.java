package io.github.xiaocan.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.xiaocan.config.BusinessException;
import io.github.xiaocan.http.XiaochanHttp;
import io.github.xiaocan.mapper.BrandCardClaimConfigMapper;
import io.github.xiaocan.mapper.BrandCardClaimAttemptHistoryMapper;
import io.github.xiaocan.mapper.BrandCardClaimHistoryMapper;
import io.github.xiaocan.model.BrandCardClaimAttemptEvent;
import io.github.xiaocan.mapper.XiaochanAccountMapper;
import io.github.xiaocan.model.BrandCardClaimAttemptResult;
import io.github.xiaocan.model.BrandCardClaimExecutionResult;
import io.github.xiaocan.model.BrandCardClaimStopReason;
import io.github.xiaocan.model.dto.BrandCardClaimConfigDTO;
import io.github.xiaocan.model.dto.BrandCardClaimHistoryQueryDTO;
import io.github.xiaocan.model.entity.BrandCardClaimConfigEntity;
import io.github.xiaocan.model.entity.BrandCardClaimAttemptHistoryEntity;
import io.github.xiaocan.model.entity.BrandCardClaimHistoryEntity;
import io.github.xiaocan.model.entity.UserEntity;
import io.github.xiaocan.model.entity.XiaochanAccountEntity;
import io.github.xiaocan.model.vo.BrandCardClaimConfigVO;
import io.github.xiaocan.model.vo.BrandCardClaimAttemptHistoryVO;
import io.github.xiaocan.model.vo.BrandCardClaimHistoryVO;
import io.github.xiaocan.service.BrandCardClaimExecutor;
import io.github.xiaocan.service.BrandCardClaimService;
import io.github.xiaocan.service.UserService;
import io.github.xiaocan.utils.PageConvertUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

@Slf4j
@Service
public class BrandCardClaimServiceImpl extends ServiceImpl<BrandCardClaimConfigMapper, BrandCardClaimConfigEntity>
        implements BrandCardClaimService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String LEGACY_DEFAULT_CRON = "58 29 9 * * ?";
    private static final String TEST_DEFAULT_CRON = "27 29 9 * * ?";
    private static final String DEFAULT_CRON = "55 29 9 * * ?";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_MIN_INTERVAL_MS = 100;
    private static final int DEFAULT_MAX_INTERVAL_MS = 300;
    private static final int CONTINUOUS_MAX_ATTEMPTS = 100;
    private static final Duration CONTINUOUS_WINDOW = Duration.ofSeconds(4);
    private final Map<Integer, LocalDateTime> lastScheduledRuns = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;
    @Resource
    private BrandCardClaimHistoryMapper historyMapper;
    @Resource
    private BrandCardClaimAttemptHistoryMapper attemptHistoryMapper;
    @Resource
    private XiaochanAccountMapper accountMapper;
    @Resource
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public BrandCardClaimConfigVO getConfig() {
        UserEntity user = userService.getByCurrentRequest();
        return toConfigVO(findByUserId(user.getId()));
    }

    @Override
    public BrandCardClaimConfigVO getConfig(Integer accountId) {
        UserEntity user = userService.getByCurrentRequest();
        return toConfigVO(findByAccountId(user.getId(), accountId));
    }

    @Override
    public List<BrandCardClaimConfigVO> listConfigs() {
        UserEntity user = userService.getByCurrentRequest();
        return list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimConfigEntity>()
                .eq(BrandCardClaimConfigEntity::getUserId, user.getId())
                .orderByAsc(BrandCardClaimConfigEntity::getId))
                .stream().map(this::toConfigVO).toList();
    }

    @Override
    public void saveConfig(BrandCardClaimConfigDTO dto) {
        saveConfig(dto.getAccountId(), dto);
    }

    @Override
    public void saveConfig(Integer accountId, BrandCardClaimConfigDTO dto) {
        if (dto.getMinIntervalMs() > dto.getMaxIntervalMs()) {
            throw new BusinessException("最小请求间隔不能大于最大请求间隔");
        }
        String cron = normalizeCron(dto.getCron());
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException("大牌券准备 cron 格式不正确，应为 6 位含秒表达式");
        }
        UserEntity user = userService.getByCurrentRequest();
        if (accountId != null && accountMapper != null) {
            XiaochanAccountEntity account = accountMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XiaochanAccountEntity>()
                    .eq(XiaochanAccountEntity::getId, accountId)
                    .eq(XiaochanAccountEntity::getUserId, user.getId()));
            if (account == null) {
                throw new BusinessException("小蚕账号不存在");
            }
            if (dto.getSilkId() == null) dto.setSilkId(account.getSilkId());
            if (dto.getXVayne() == null) dto.setXVayne(account.getXVayne());
            if (!StringUtils.hasText(dto.getXSivir())) dto.setXSivir(account.getXSivir());
        }
        if (accountId != null && accountMapper != null
                && accountMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XiaochanAccountEntity>()
                .eq(XiaochanAccountEntity::getId, accountId)
                .eq(XiaochanAccountEntity::getUserId, user.getId())) == null) {
            throw new BusinessException("小蚕账号不存在");
        }
        BrandCardClaimConfigEntity config = accountId == null
                ? findByUserId(user.getId()) : findByAccountId(user.getId(), accountId);
        boolean creating = config == null;
        if (creating) {
            if (!StringUtils.hasText(dto.getXSivir()) || dto.getXVayne() == null) {
                throw new BusinessException("首次保存必须填写 X-Sivir 与 X-Vayne");
            }
            config = new BrandCardClaimConfigEntity();
            config.setUserId(user.getId());
            config.setAccountId(accountId);
        }
        config.setSilkId(dto.getSilkId());
        config.setAccountId(accountId == null ? config.getAccountId() : accountId);
        config.setXVayne(dto.getXVayne());
        config.setEnabled(dto.getEnabled());
        config.setCron(cron);
        config.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        config.setMinIntervalMs(DEFAULT_MIN_INTERVAL_MS);
        config.setMaxIntervalMs(DEFAULT_MAX_INTERVAL_MS);
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
        return claimNow(null);
    }

    @Override
    public BrandCardClaimExecutionResult claimNow(Integer accountId) {
        BrandCardClaimConfigEntity config = requireCurrentUserConfig(accountId);
        Instant firstAttemptAt = Instant.now();
        BrandCardClaimHistoryEntity history = createRunningHistory(config,
                LocalDateTime.ofInstant(firstAttemptAt, APP_ZONE));
        BrandCardClaimAttemptResult attempt = XiaochanHttp.grabExtraBrandCard(
                config.getSilkId(), config.getXSivir(), config.getXVayne());
        Instant responseAt = Instant.now();
        BrandCardClaimExecutionResult result = attempt.retryable()
                ? new BrandCardClaimExecutionResult(1, false, attempt.code(), attempt.message(),
                BrandCardClaimStopReason.TIME_WINDOW_EXPIRED, firstAttemptAt)
                : BrandCardClaimExecutionResult.fromAttempt(1, attempt, firstAttemptAt);
        saveAttempt(history, config, new BrandCardClaimAttemptEvent(1, firstAttemptAt, responseAt, attempt));
        finishHistory(history, result);
        return result;
    }

    @Override
    public Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto) {
        return pageHistory(dto, null);
    }

    @Override
    public Page<BrandCardClaimHistoryVO> pageHistory(BrandCardClaimHistoryQueryDTO dto, Integer accountId) {
        UserEntity user = userService.getByCurrentRequest();
        Page<BrandCardClaimHistoryEntity> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<BrandCardClaimHistoryEntity> result = historyMapper.selectPage(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimHistoryEntity>()
                        .eq(BrandCardClaimHistoryEntity::getUserId, user.getId())
                        .orderByDesc(BrandCardClaimHistoryEntity::getId));
        if (accountId != null) {
            BrandCardClaimConfigEntity config = findByAccountId(user.getId(), accountId);
            if (config == null) {
                return PageConvertUtil.convert(new Page<>(), BrandCardClaimHistoryVO.class);
            }
            result = historyMapper.selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimHistoryEntity>()
                    .eq(BrandCardClaimHistoryEntity::getUserId, user.getId())
                    .eq(BrandCardClaimHistoryEntity::getConfigId, config.getId())
                    .orderByDesc(BrandCardClaimHistoryEntity::getId));
        }
        return PageConvertUtil.convert(result, BrandCardClaimHistoryVO.class);
    }

    @Override
    public List<BrandCardClaimAttemptHistoryVO> listAttemptHistory(Long historyId) {
        UserEntity user = userService.getByCurrentRequest();
        BrandCardClaimHistoryEntity history = historyMapper.selectById(historyId);
        if (history == null || !user.getId().equals(history.getUserId())) {
            return List.of();
        }
        return attemptHistoryMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimAttemptHistoryEntity>()
                                .eq(BrandCardClaimAttemptHistoryEntity::getHistoryId, historyId)
                                .orderByAsc(BrandCardClaimAttemptHistoryEntity::getSequence))
                .stream().map(this::toAttemptHistoryVO).toList();
    }

    @Override
    public void runScheduledClaims() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE).withNano(0);
        lastScheduledRuns.entrySet().removeIf(entry -> entry.getValue().toLocalDate().isBefore(now.toLocalDate()));
        list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandCardClaimConfigEntity>()
                .eq(BrandCardClaimConfigEntity::getEnabled, true))
                .stream()
                .filter(config -> config.getSilkId() != null && config.getXVayne() != null
                        && StringUtils.hasText(config.getXSivir()))
                .filter(config -> isPreparationDue(config, now))
                .forEach(config -> {
                    LocalDateTime previous = lastScheduledRuns.put(config.getId(), now);
                    if (previous == null || !previous.equals(now)) {
                        Instant target = preparationTarget(config, now);
                        log.info("大牌券连续窗口已准备：配置={}, 账号={}, 预备时间={}, 开始时间={}, 结束时间={}, 安全上限={}, 间隔={}至{}毫秒",
                                config.getId(), config.getAccountId(), now, target, target.plus(CONTINUOUS_WINDOW),
                                CONTINUOUS_MAX_ATTEMPTS, DEFAULT_MIN_INTERVAL_MS, DEFAULT_MAX_INTERVAL_MS);
                        taskScheduler.execute(() -> runAutomaticClaim(config, target));
                    }
                });
    }

    private boolean isPreparationDue(BrandCardClaimConfigEntity config, LocalDateTime now) {
        String cron = normalizeCron(config.getCron());
        if (!CronExpression.isValidExpression(cron)) return false;
        LocalDateTime next = CronExpression.parse(cron).next(now.minusSeconds(1));
        return next != null && next.withNano(0).equals(now);
    }

    private Instant preparationTarget(BrandCardClaimConfigEntity config, LocalDateTime preparationTime) {
        String cron = normalizeCron(config.getCron());
        LocalDateTime scheduled = CronExpression.parse(cron).next(preparationTime.minusSeconds(1));
        return (scheduled == null ? preparationTime.plusSeconds(2) : scheduled.plusSeconds(2))
                .atZone(APP_ZONE).toInstant();
    }

    private void runAutomaticClaim(BrandCardClaimConfigEntity config, Instant target) {
        Instant preparedAt = Instant.now();
        AtomicInteger requestSequence = new AtomicInteger();
        log.info("大牌券连续窗口已启动：配置={}, 账号={}, 开始时间={}, 结束时间={}, 等待={}毫秒",
                config.getId(), config.getAccountId(), target, target.plus(CONTINUOUS_WINDOW),
                Math.max(0, Duration.between(preparedAt, target).toMillis()));
        taskScheduler.execute(XiaochanHttp::warmBrandCardEndpoint);
        BrandCardClaimHistoryEntity history = createRunningHistory(config,
                LocalDateTime.ofInstant(target, APP_ZONE));
        BrandCardClaimExecutor executor = new BrandCardClaimExecutor(
                (silkId, xSivir) -> {
                    int attempt = requestSequence.incrementAndGet();
                    Instant requestStartedAt = Instant.now();
                    log.info("大牌券请求已发送：配置={}, 次数={}, 相对目标时间偏差={}毫秒",
                            config.getId(), attempt, Duration.between(target, requestStartedAt).toMillis());
                    BrandCardClaimAttemptResult response = XiaochanHttp.grabExtraBrandCard(silkId, xSivir,
                            config.getXVayne());
                    log.info("大牌券响应已收到：配置={}, 次数={}, 耗时={}毫秒, 响应码={}, 原因={}, 可重试={}, 消息={}",
                            config.getId(), attempt, Duration.between(requestStartedAt, Instant.now()).toMillis(),
                            response.code(), response.stopReason(), response.retryable(), safeLogMessage(response.message()));
                    return response;
                },
                Clock.system(APP_ZONE),
                duration -> TimeUnit.NANOSECONDS.sleep(duration.toNanos()),
                () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(
                        DEFAULT_MIN_INTERVAL_MS, DEFAULT_MAX_INTERVAL_MS + 1L))
        );
        BrandCardClaimExecutionResult result = executor.executeContinuous(
                config.getSilkId(),
                config.getXSivir(),
                config.getXVayne(),
                CONTINUOUS_MAX_ATTEMPTS,
                Duration.ofMillis(DEFAULT_MIN_INTERVAL_MS),
                Duration.ofMillis(DEFAULT_MAX_INTERVAL_MS),
                target,
                target.plus(CONTINUOUS_WINDOW),
                event -> saveAttempt(history, config, event)
        );
        finishHistory(history, result);
        long firstRequestOffsetMs = result.firstAttemptAt() == null ? 0
                : Duration.between(target, result.firstAttemptAt()).toMillis();
        log.info("大牌券连续窗口已结束：配置={}, 账号={}, 首次请求={}, 首次偏差={}毫秒, 请求次数={}, 成功={}, 响应码={}, 结束原因={}, 总耗时={}毫秒, 消息={}",
                config.getId(), config.getAccountId(), result.firstAttemptAt(), firstRequestOffsetMs,
                result.attempts(), result.success(), result.resultCode(), result.stopReason(),
                Duration.between(preparedAt, Instant.now()).toMillis(), safeLogMessage(result.resultMessage()));
    }

    private BrandCardClaimConfigEntity requireCurrentUserConfig(Integer accountId) {
        Integer userId = userService.getByCurrentRequest().getId();
        BrandCardClaimConfigEntity config = accountId == null ? findByUserId(userId) : findByAccountId(userId, accountId);
        if (config == null || config.getXVayne() == null || !StringUtils.hasText(config.getXSivir())) {
            throw new BusinessException("请先保存 silk_id、X-Vayne 与 X-Sivir 配置");
        }
        return config;
    }

    private BrandCardClaimConfigEntity findByUserId(Integer userId) {
        return lambdaQuery().eq(BrandCardClaimConfigEntity::getUserId, userId)
                .orderByAsc(BrandCardClaimConfigEntity::getId).last("limit 1").one();
    }

    private BrandCardClaimConfigEntity findByAccountId(Integer userId, Integer accountId) {
        return lambdaQuery().eq(BrandCardClaimConfigEntity::getUserId, userId)
                .eq(BrandCardClaimConfigEntity::getAccountId, accountId).one();
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
        vo.setAccountId(config.getAccountId());
        vo.setSilkId(config.getSilkId());
        vo.setXVayne(config.getXVayne());
        vo.setXSivirMasked(mask(config.getXSivir()));
        vo.setEnabled(config.getEnabled());
        vo.setCron(normalizeCron(config.getCron()));
        vo.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        vo.setMinIntervalMs(DEFAULT_MIN_INTERVAL_MS);
        vo.setMaxIntervalMs(DEFAULT_MAX_INTERVAL_MS);
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

    private String normalizeCron(String cron) {
        if (!StringUtils.hasText(cron) || LEGACY_DEFAULT_CRON.equals(cron.trim())
                || TEST_DEFAULT_CRON.equals(cron.trim())) {
            return DEFAULT_CRON;
        }
        return cron.trim();
    }

    private String safeLogMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String normalized = message.replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    private BrandCardClaimHistoryEntity createRunningHistory(BrandCardClaimConfigEntity config,
                                                             LocalDateTime initialTime) {
        BrandCardClaimHistoryEntity history = new BrandCardClaimHistoryEntity();
        history.setUserId(config.getUserId());
        history.setConfigId(config.getId());
        history.setAccountId(config.getAccountId());
        history.setStartTime(initialTime);
        history.setEndTime(initialTime);
        history.setRequestCount(0);
        history.setSuccess(false);
        history.setStopReason("RUNNING");
        historyMapper.insert(history);
        return history;
    }

    private void finishHistory(BrandCardClaimHistoryEntity history,
                               BrandCardClaimExecutionResult result) {
        if (result.firstAttemptAt() != null) {
            history.setStartTime(LocalDateTime.ofInstant(result.firstAttemptAt(), APP_ZONE));
        }
        history.setEndTime(LocalDateTime.now(APP_ZONE));
        history.setRequestCount(result.attempts());
        history.setSuccess(result.success());
        history.setResultCode(result.resultCode());
        history.setResultMsg(result.resultMessage());
        history.setStopReason(result.stopReason().name());
        historyMapper.updateById(history);
    }

    private void saveAttempt(BrandCardClaimHistoryEntity history, BrandCardClaimConfigEntity config,
                             BrandCardClaimAttemptEvent event) {
        BrandCardClaimAttemptHistoryEntity attempt = new BrandCardClaimAttemptHistoryEntity();
        attempt.setHistoryId(history.getId());
        attempt.setUserId(config.getUserId());
        attempt.setConfigId(config.getId());
        attempt.setAccountId(config.getAccountId());
        attempt.setSequence(event.sequence());
        attempt.setRequestTime(LocalDateTime.ofInstant(event.requestTime(), APP_ZONE));
        attempt.setResponseTime(LocalDateTime.ofInstant(event.responseTime(), APP_ZONE));
        attempt.setDurationMs(Math.max(0, Duration.between(event.requestTime(), event.responseTime()).toMillis()));
        attempt.setResultCode(event.result().code());
        attempt.setResultMsg(event.result().message());
        attempt.setRetryable(event.result().retryable());
        attempt.setStopReason(event.result().stopReason() == null ? null : event.result().stopReason().name());
        attempt.setSuccess(event.result().stopReason() == BrandCardClaimStopReason.SUCCESS);
        attemptHistoryMapper.insert(attempt);
    }

    private BrandCardClaimAttemptHistoryVO toAttemptHistoryVO(BrandCardClaimAttemptHistoryEntity attempt) {
        BrandCardClaimAttemptHistoryVO vo = new BrandCardClaimAttemptHistoryVO();
        vo.setId(attempt.getId());
        vo.setHistoryId(attempt.getHistoryId());
        vo.setSequence(attempt.getSequence());
        vo.setRequestTime(attempt.getRequestTime());
        vo.setResponseTime(attempt.getResponseTime());
        vo.setDurationMs(attempt.getDurationMs());
        vo.setResultCode(attempt.getResultCode());
        vo.setResultMsg(attempt.getResultMsg());
        vo.setRetryable(attempt.getRetryable());
        vo.setStopReason(attempt.getStopReason());
        vo.setSuccess(attempt.getSuccess());
        return vo;
    }
}
