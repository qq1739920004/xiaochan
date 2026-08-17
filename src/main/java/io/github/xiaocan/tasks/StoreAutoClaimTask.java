package io.github.xiaocan.tasks;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.xiaocan.mapper.StoreAutoClaimScheduleMapper;
import io.github.xiaocan.model.StoreAutoClaimConfig;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreExtNotifyConfig;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.StoreKeywordExtNotifyConfig;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.entity.StoreAutoClaimScheduleEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.service.LocationService;
import io.github.xiaocan.service.MonitoryConfigService;
import io.github.xiaocan.service.StoreAutoClaimCandidateSelector;
import io.github.xiaocan.service.StoreAutoClaimService;
import io.github.xiaocan.service.XiaoChanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class StoreAutoClaimTask {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DISTANCE_METERS = 5000;

    private final MonitoryConfigService configService;
    private final LocationService locationService;
    private final XiaoChanService xiaoChanService;
    private final StoreAutoClaimService claimService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final StoreAutoClaimCandidateSelector candidateSelector = new StoreAutoClaimCandidateSelector();
    private final Set<String> handledKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> scheduledKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> scheduledConfigDays = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> lastSkipLogs = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StoreAutoClaimScheduleMapper scheduleMapper;

    public StoreAutoClaimTask(MonitoryConfigService configService,
                              LocationService locationService,
                              XiaoChanService xiaoChanService,
                              StoreAutoClaimService claimService,
                              ThreadPoolTaskScheduler taskScheduler) {
        this.configService = configService;
        this.locationService = locationService;
        this.xiaoChanService = xiaoChanService;
        this.claimService = claimService;
        this.taskScheduler = taskScheduler;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void poll() {
        pollAt(LocalDateTime.now(APP_ZONE));
    }

    void pollAt(LocalDateTime now) {
        String dateKey = now.toLocalDate().toString();
        handledKeys.removeIf(key -> !key.startsWith(dateKey + ":"));
        scheduledKeys.removeIf(key -> !key.startsWith(dateKey + ":"));
        scheduledConfigDays.removeIf(key -> !key.startsWith(dateKey + ":"));
        resumeDueSchedules(now);
        List<MonitorConfigEntity> configs = Stream.of(
                        MonitorTypeEnums.STORE_ACTIVITY, MonitorTypeEnums.STORE_KEYWORD)
                .flatMap(type -> safeList(configService.list(type, MonitorConfigStatusEnums.ENABLE)))
                .toList();
        for (MonitorConfigEntity config : configs) {
            try {
                pollConfig(config, now);
            } catch (Exception e) {
                log.warn("自动抢单监控轮询失败 configId={}", config.getId(), e);
            }
        }
    }

    private void pollConfig(MonitorConfigEntity config, LocalDateTime now) {
        if (!isMonitorWindowActive(config, now)) return;
        ClaimContext context = readClaimContext(config);
        if (context == null) return;
        LocationEntity location = locationService.getById(config.getLocationId());
        if (location == null) {
            log.warn("自动抢单监控位置不存在 configId={}, locationId={}", config.getId(), config.getLocationId());
            return;
        }
        List<StoreInfo> stores = xiaoChanService.searchList(
                context.keyword(), location.getCityCode(), location.getLongitude(), location.getLatitude());
        submitCandidate(config, location, stores, context, now);
    }

    /** 复用通知任务已经发现的活动，避免两个查询结果出现短暂差异。 */
    void claimDiscovered(MonitorConfigEntity config, LocationEntity location,
                         List<StoreInfo> stores, LocalDateTime now) {
        if (!isMonitorWindowActive(config, now) || location == null) return;
        ClaimContext context = readClaimContext(config);
        if (context == null) return;
        submitNotifiedCandidate(config, location, stores, context, now);
    }

    private void submitNotifiedCandidate(MonitorConfigEntity config, LocationEntity location,
                                         List<StoreInfo> stores, ClaimContext context, LocalDateTime now) {
        List<StoreInfo> notifiedStores = safeList(stores)
                .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                .toList();
        StoreInfo candidate = candidateSelector.select(notifiedStores).orElse(null);
        if (candidate == null) {
            logSkip(config, context.keyword(), "通知活动无库存或缺少返利信息", stores, now);
            return;
        }
        LocalDateTime scheduledAt = resolveClaimTime(candidate, now);
        if (scheduledAt == null) {
            logSkip(config, context.keyword(), "通知活动已结束或活动时间无法预约", notifiedStores, now);
            return;
        }
        String key = buildKey(config, candidate, scheduledAt.toLocalDate());
        if (handledKeys.contains(key)) return;
        scheduleClaim(config, location, context, candidate, scheduledAt, key, now);
    }

    private void submitCandidate(MonitorConfigEntity config, LocationEntity location,
                                 List<StoreInfo> stores, ClaimContext context, LocalDateTime now) {
        List<StoreInfo> matchedStores = matchStores(config, stores, context.keyword());
        if (matchedStores.isEmpty()) {
            logSkip(config, context.keyword(), "门店名称前缀或距离不满足", stores, now);
            return;
        }
        if (hasAmbiguousStoreIdentity(config, matchedStores)) {
            log.warn("关键词自动抢单跳过歧义门店 configId={}, keyword={}, matchedCount={}",
                    config.getId(), context.keyword(), matchedStores.size());
            return;
        }
        List<StoreInfo> stockedStores = matchedStores.stream()
                .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                .toList();
        StoreInfo candidate = candidateSelector.select(stockedStores).orElse(null);
        if (candidate == null) {
            logSkip(config, context.keyword(), "无库存或评价/返利条件不支持", matchedStores, now);
            return;
        }
        LocalDateTime scheduledAt = resolveClaimTime(candidate, now);
        if (scheduledAt == null) {
            logSkip(config, context.keyword(), "活动已结束或活动时间无法预约", matchedStores, now);
            return;
        }
        String key = buildKey(config, candidate, scheduledAt.toLocalDate());
        if (handledKeys.contains(key)) return;
        scheduleClaim(config, location, context, candidate, scheduledAt, key, now);
    }

    private LocalDateTime resolveClaimTime(StoreInfo candidate, LocalDateTime now) {
        if (isActiveAt(candidate, now.toLocalTime())) return now;
        if (!StringUtils.hasText(candidate.getStartTime())) return null;
        try {
            LocalTime start = LocalTime.parse(candidate.getStartTime(), TIME_FORMAT);
            LocalDateTime scheduled = now.toLocalDate().atTime(start);
            return scheduled.isAfter(now) ? scheduled : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void scheduleClaim(MonitorConfigEntity config, LocationEntity location,
                               ClaimContext context, StoreInfo candidate,
                               LocalDateTime scheduledAt, String key, LocalDateTime now) {
        if (!reserveSchedule(config, candidate, scheduledAt, key, now)) return;
        Runnable runnable = () -> executeScheduledClaim(config, location, context, candidate, key);
        try {
            if (scheduledAt.isAfter(now)) {
                taskScheduler.schedule(runnable,
                        Date.from(scheduledAt.atZone(APP_ZONE).toInstant()));
                log.info("自动抢单已预约 configId={}, promotionId={}, scheduledAt={}",
                        config.getId(), candidate.getPromotionId(), scheduledAt);
            } else {
                taskScheduler.execute(runnable);
            }
        } catch (RuntimeException e) {
            scheduledKeys.remove(key);
            markSchedule(key, "SKIPPED", false, "预约任务提交失败");
            throw e;
        }
    }

    private boolean reserveSchedule(MonitorConfigEntity config, StoreInfo candidate,
                                    LocalDateTime scheduledAt, String key, LocalDateTime now) {
        if (handledKeys.contains(key) || !scheduledKeys.add(key)) return false;
        LocalDate runDate = scheduledAt.toLocalDate();
        String configDayKey = runDate + ":" + config.getId();
        if (!scheduledConfigDays.add(configDayKey)) {
            scheduledKeys.remove(key);
            return false;
        }
        if (scheduleMapper == null) return true;
        LambdaQueryWrapper<StoreAutoClaimScheduleEntity> wrapper = new LambdaQueryWrapper<StoreAutoClaimScheduleEntity>()
                .eq(StoreAutoClaimScheduleEntity::getMonitorConfigId, config.getId())
                .eq(StoreAutoClaimScheduleEntity::getRunDate, runDate)
                .last("limit 1");
        StoreAutoClaimScheduleEntity existing = scheduleMapper.selectOne(wrapper);
        if (existing != null) {
            scheduledKeys.remove(key);
            return false;
        }
        StoreAutoClaimScheduleEntity entity = new StoreAutoClaimScheduleEntity();
        entity.setUserId(config.getUserId());
        entity.setMonitorConfigId(config.getId());
        entity.setAccountId(readAccountId(config));
        entity.setRunDate(runDate);
        entity.setStoreUniqId(candidate.getUniqId() == null ? "" : candidate.getUniqId());
        entity.setPromotionId(parseLong(candidate.getPromotionId()));
        entity.setRebateCondition(candidate.getRebateCondition());
        entity.setScheduledAt(scheduledAt);
        entity.setStatus("PENDING");
        entity.setDiscoveredAt(now);
        entity.setRequestSent(false);
        scheduleMapper.insert(entity);
        return true;
    }

    private void executeScheduledClaim(MonitorConfigEntity config, LocationEntity location,
                                       ClaimContext context, StoreInfo discovered,
                                       String key) {
        if (!markScheduleRunning(key, config, discovered)) return;
        try {
            if (!isMonitorWindowActive(config, LocalDateTime.now(APP_ZONE))) {
                finishWithoutRequest(key, "到点复查时监控不在运行时间");
                return;
            }
            List<StoreInfo> freshStores = xiaoChanService.searchList(
                    context.keyword(), location.getCityCode(), location.getLongitude(), location.getLatitude());
            List<StoreInfo> matched = freshStores.stream()
                    .filter(store -> isSameScheduledActivity(discovered, store))
                    .toList();
            StoreInfo candidate = candidateSelector.select(matched.stream()
                    .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                    .filter(store -> isActiveAt(store, LocalTime.now(APP_ZONE)))
                    .toList()).orElse(null);
            if (candidate == null) {
                finishWithoutRequest(key, "到点复查时无库存或不在活动时间");
                return;
            }
            executeClaim(config, location, candidate, key);
        } catch (Exception e) {
            finishWithoutRequest(key, "到点复查失败: " + safeMessage(e));
            log.warn("预约抢单到点复查失败 configId={}, promotionId={}", config.getId(), discovered.getPromotionId(), e);
        }
    }

    private boolean isSameScheduledActivity(StoreInfo discovered, StoreInfo current) {
        if (!Objects.equals(parseLong(discovered.getPromotionId()), parseLong(current.getPromotionId()))) {
            return false;
        }
        String discoveredIdentity = storeIdentity(discovered);
        return discoveredIdentity == null || Objects.equals(discoveredIdentity, storeIdentity(current));
    }

    private boolean markScheduleRunning(String key, MonitorConfigEntity config, StoreInfo candidate) {
        if (scheduleMapper == null) return inFlightKeys.add(key);
        StoreAutoClaimScheduleEntity existing = findSchedule(config, candidate, LocalDate.parse(key.substring(0, key.indexOf(':'))));
        if (existing == null || !Objects.equals(existing.getStatus(), "PENDING")) return false;
        StoreAutoClaimScheduleEntity update = new StoreAutoClaimScheduleEntity();
        update.setStatus("RUNNING");
        update.setExecutedAt(LocalDateTime.now(APP_ZONE));
        int updated = scheduleMapper.update(update, new LambdaUpdateWrapper<StoreAutoClaimScheduleEntity>()
                .eq(StoreAutoClaimScheduleEntity::getId, existing.getId())
                .eq(StoreAutoClaimScheduleEntity::getStatus, "PENDING"));
        return updated == 1 && inFlightKeys.add(key);
    }

    private StoreAutoClaimScheduleEntity findSchedule(MonitorConfigEntity config, StoreInfo candidate,
                                                      LocalDate runDate) {
        return scheduleMapper.selectOne(new LambdaQueryWrapper<StoreAutoClaimScheduleEntity>()
                .eq(StoreAutoClaimScheduleEntity::getMonitorConfigId, config.getId())
                .eq(StoreAutoClaimScheduleEntity::getRunDate, runDate)
                .eq(StoreAutoClaimScheduleEntity::getPromotionId, parseLong(candidate.getPromotionId()))
                .eq(StoreAutoClaimScheduleEntity::getRebateCondition, candidate.getRebateCondition())
                .orderByDesc(StoreAutoClaimScheduleEntity::getId));
    }

    private void executeClaim(MonitorConfigEntity config, LocationEntity location,
                              StoreInfo candidate, String key) {
        try {
            StoreAutoClaimResult result = claimService.execute(config, location, candidate);
            handledKeys.add(key);
            markSchedule(key, "DONE", result.attempts() > 0, result.resultMessage());
            log.info("自动抢单完成 configId={}, promotionId={}, attempts={}, success={}, reason={}",
                    config.getId(), candidate.getPromotionId(), result.attempts(), result.success(), result.stopReason());
        } catch (Exception e) {
            handledKeys.add(key);
            markSchedule(key, "DONE", false, safeMessage(e));
            log.error("自动抢单执行异常 configId={}, promotionId={}", config.getId(), candidate.getPromotionId(), e);
        } finally {
            inFlightKeys.remove(key);
        }
    }

    private void finishWithoutRequest(String key, String message) {
        handledKeys.add(key);
        markSchedule(key, "SKIPPED", false, message);
        inFlightKeys.remove(key);
    }

    private void markSchedule(String key, String status, boolean requestSent, String message) {
        if (scheduleMapper == null) return;
        String[] parts = key.split(":");
        if (parts.length < 4) return;
        StoreAutoClaimScheduleEntity update = new StoreAutoClaimScheduleEntity();
        update.setStatus(status);
        update.setRequestSent(requestSent);
        update.setExecutedAt(LocalDateTime.now(APP_ZONE));
        update.setResultMsg(message);
        scheduleMapper.update(update, new LambdaUpdateWrapper<StoreAutoClaimScheduleEntity>()
                .eq(StoreAutoClaimScheduleEntity::getMonitorConfigId, Integer.valueOf(parts[1]))
                .eq(StoreAutoClaimScheduleEntity::getRunDate, LocalDate.parse(parts[0]))
                .eq(StoreAutoClaimScheduleEntity::getPromotionId, Long.valueOf(parts[2]))
                .eq(StoreAutoClaimScheduleEntity::getRebateCondition, Integer.valueOf(parts[3])));
    }

    private ClaimContext readClaimContext(MonitorConfigEntity config) {
        String keyword;
        StoreAutoClaimConfig autoClaim;
        if (config.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreExtNotifyConfig.class);
            autoClaim = ext == null ? null : ext.getAutoClaimConfig();
            if (ext == null || ext.getStoreInfo() == null || !StringUtils.hasText(ext.getStoreInfo().getName())) return null;
            keyword = ext.getStoreInfo().getName().trim();
        } else if (config.getType() == MonitorTypeEnums.STORE_KEYWORD) {
            StoreKeywordExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreKeywordExtNotifyConfig.class);
            autoClaim = ext == null ? null : ext.getAutoClaimConfig();
            if (ext == null || !StringUtils.hasText(ext.getKeyword())) return null;
            keyword = ext.getKeyword().trim();
        } else {
            return null;
        }
        return autoClaim != null && Boolean.TRUE.equals(autoClaim.getEnabled()) ? new ClaimContext(keyword) : null;
    }

    private List<StoreInfo> matchStores(MonitorConfigEntity config, List<StoreInfo> stores, String keyword) {
        if (stores == null) return List.of();
        if (config.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreExtNotifyConfig.class);
            String uniqId = ext == null || ext.getStoreInfo() == null ? null : ext.getStoreInfo().getUniqId();
            return stores.stream()
                    .filter(store -> StringUtils.hasText(uniqId) && uniqId.equals(store.getUniqId()))
                    .filter(this::withinDistance)
                    .toList();
        }
        StoreKeywordExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreKeywordExtNotifyConfig.class);
        boolean limitDistance = ext == null || ext.getLimitDistance() == null || ext.getLimitDistance();
        return stores.stream().filter(store -> keywordMatchesForAutoClaim(keyword, store.getName()))
                .filter(store -> !limitDistance || withinDistance(store)).toList();
    }

    static boolean keywordMatchesForAutoClaim(String keyword, String storeName) {
        return StringUtils.hasText(keyword) && StringUtils.hasText(storeName)
                && storeName.trim().startsWith(keyword.trim());
    }

    private boolean hasAmbiguousStoreIdentity(MonitorConfigEntity config, List<StoreInfo> stores) {
        if (config.getType() != MonitorTypeEnums.STORE_KEYWORD) return false;
        Set<String> identities = stores.stream().map(this::storeIdentity).filter(Objects::nonNull).collect(Collectors.toSet());
        boolean hasUnknownIdentity = stores.stream().anyMatch(store -> storeIdentity(store) == null);
        return identities.size() > 1 || hasUnknownIdentity || (identities.isEmpty() && stores.size() > 1);
    }

    private String storeIdentity(StoreInfo store) {
        if (StringUtils.hasText(store.getUniqId())) return "uniq:" + store.getUniqId();
        return store.getStoreId() == null ? null : "store:" + store.getStoreId();
    }

    private boolean withinDistance(StoreInfo store) {
        return withinDistanceForTest(store);
    }

    static boolean withinDistanceForTest(StoreInfo store) {
        if (!StringUtils.hasText(store.getDistance())) return false;
        try {
            return Long.parseLong(store.getDistance()) <= MAX_DISTANCE_METERS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private <T> Stream<T> safeList(List<T> values) {
        return values == null ? Stream.empty() : values.stream();
    }

    private void logSkip(MonitorConfigEntity config, String keyword, String reason,
                         List<StoreInfo> stores, LocalDateTime now) {
        String key = config.getId() + ":" + reason;
        LocalDateTime previous = lastSkipLogs.putIfAbsent(key, now);
        if (previous != null && previous.plusMinutes(1).isAfter(now)) return;
        lastSkipLogs.put(key, now);
        String summary = stores == null ? "[]" : stores.stream().limit(10)
                .map(store -> String.format("%s{uniq=%s,distance=%s,left=%s,time=%s-%s,condition=%s,rebate=%s}",
                        store.getName(), store.getUniqId(), store.getDistance(), store.getLeftNumber(),
                        store.getStartTime(), store.getEndTime(), store.getRebateCondition(), store.getRebatePrice()))
                .collect(Collectors.joining(", ", "[", "]"));
        log.info("自动抢单未提交 configId={}, keyword={}, reason={}, stores={}", config.getId(), keyword, reason, summary);
    }

    private record ClaimContext(String keyword) { }

    static boolean isActiveAt(StoreInfo store, LocalTime now) {
        if (!StringUtils.hasText(store.getStartTime()) || !StringUtils.hasText(store.getEndTime())) return true;
        try {
            LocalTime start = parseActivityTime(store.getStartTime(), false);
            LocalTime end = parseActivityTime(store.getEndTime(), true);
            LocalTime currentMinute = now.withSecond(0).withNano(0);
            if (end.isBefore(start)) return !currentMinute.isBefore(start) || !currentMinute.isAfter(end);
            return !currentMinute.isBefore(start) && !currentMinute.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("活动时间格式无法解析，按不可预约处理 storeId={}, start={}, end={}",
                    store.getStoreId(), store.getStartTime(), store.getEndTime());
            return false;
        }
    }

    private static LocalTime parseActivityTime(String value, boolean allowEndOfDay) {
        if (allowEndOfDay && "24:00".equals(value)) return LocalTime.MAX;
        return LocalTime.parse(value, TIME_FORMAT);
    }

    private boolean isMonitorWindowActive(MonitorConfigEntity config, LocalDateTime now) {
        if (StringUtils.hasText(config.getCron())) return true;
        if (config.getStartHour() == null || config.getEndHour() == null || !StringUtils.hasText(config.getWeeks())) return false;
        boolean weekMatches = java.util.Arrays.stream(config.getWeeks().split(","))
                .map(String::trim).anyMatch(value -> value.equals(String.valueOf(now.getDayOfWeek().getValue())));
        if (!weekMatches) return false;
        int hour = now.getHour();
        if (config.getStartHour() <= config.getEndHour()) return hour >= config.getStartHour() && hour < config.getEndHour();
        return hour >= config.getStartHour() || hour < config.getEndHour();
    }

    private String buildKey(MonitorConfigEntity config, StoreInfo candidate, LocalDate runDate) {
        return runDate + ":" + config.getId() + ":" + parseLong(candidate.getPromotionId()) + ":" + candidate.getRebateCondition();
    }

    private Long parseLong(String value) {
        try { return value == null ? null : Long.valueOf(value); }
        catch (NumberFormatException e) { return 0L; }
    }

    private Integer readAccountId(MonitorConfigEntity config) {
        com.alibaba.fastjson2.JSONObject root = JSON.parseObject(config.getExtConfig());
        com.alibaba.fastjson2.JSONObject auto = root == null ? null : root.getJSONObject("autoClaimConfig");
        return auto == null ? null : auto.getInteger("accountId");
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? "执行失败" : e.getMessage().substring(0, Math.min(200, e.getMessage().length()));
    }

    private void resumeDueSchedules(LocalDateTime now) {
        if (scheduleMapper == null) return;
        List<StoreAutoClaimScheduleEntity> due = scheduleMapper.selectList(
                new LambdaQueryWrapper<StoreAutoClaimScheduleEntity>()
                        .eq(StoreAutoClaimScheduleEntity::getStatus, "PENDING")
                        .le(StoreAutoClaimScheduleEntity::getScheduledAt, now)
                        .orderByAsc(StoreAutoClaimScheduleEntity::getScheduledAt));
        for (StoreAutoClaimScheduleEntity schedule : due) {
            String key = schedule.getRunDate() + ":" + schedule.getMonitorConfigId() + ":"
                    + schedule.getPromotionId() + ":" + schedule.getRebateCondition();
            if (!scheduledKeys.add(key) || handledKeys.contains(key)) continue;
            MonitorConfigEntity config = configService.getById(schedule.getMonitorConfigId());
            LocationEntity location = config == null ? null : locationService.getById(config.getLocationId());
            ClaimContext context = config == null ? null : readClaimContext(config);
            if (config == null || location == null || context == null
                    || config.getStatus() != MonitorConfigStatusEnums.ENABLE) {
                finishWithoutRequest(key, "预约恢复时监控配置已不可用");
                continue;
            }
            StoreInfo discovered = new StoreInfo();
            discovered.setPromotionId(String.valueOf(schedule.getPromotionId()));
            discovered.setRebateCondition(schedule.getRebateCondition());
            discovered.setUniqId(schedule.getStoreUniqId());
            executeScheduledClaim(config, location, context, discovered, key);
        }
    }
}
