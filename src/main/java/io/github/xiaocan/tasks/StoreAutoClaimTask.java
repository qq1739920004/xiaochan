package io.github.xiaocan.tasks;

import com.alibaba.fastjson2.JSON;
import io.github.xiaocan.model.StoreAutoClaimConfig;
import io.github.xiaocan.model.StoreAutoClaimResult;
import io.github.xiaocan.model.StoreExtNotifyConfig;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.StoreKeywordExtNotifyConfig;
import io.github.xiaocan.model.StoreAutoClaimStopReason;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.service.LocationService;
import io.github.xiaocan.service.MonitoryConfigService;
import io.github.xiaocan.service.StoreAutoClaimCandidateSelector;
import io.github.xiaocan.service.StoreAutoClaimService;
import io.github.xiaocan.service.XiaoChanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class StoreAutoClaimTask {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MonitoryConfigService configService;
    private final LocationService locationService;
    private final XiaoChanService xiaoChanService;
    private final StoreAutoClaimService claimService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final StoreAutoClaimCandidateSelector candidateSelector = new StoreAutoClaimCandidateSelector();
    private final Set<String> handledKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();

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
        pollAt(LocalDateTime.now());
    }

    void pollAt(LocalDateTime now) {
        String dateKey = now.toLocalDate().toString();
        handledKeys.removeIf(key -> !key.startsWith(dateKey + ":"));
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
        if (!isMonitorWindowActive(config, now)) {
            return;
        }
        String keyword;
        List<StoreInfo> matchedStores;
        StoreAutoClaimConfig autoClaim;
        if (config.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreExtNotifyConfig.class);
            autoClaim = ext == null ? null : ext.getAutoClaimConfig();
            if (ext == null || ext.getStoreInfo() == null || !StringUtils.hasText(ext.getStoreInfo().getName())) {
                return;
            }
            keyword = ext.getStoreInfo().getName();
        } else if (config.getType() == MonitorTypeEnums.STORE_KEYWORD) {
            StoreKeywordExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreKeywordExtNotifyConfig.class);
            autoClaim = ext == null ? null : ext.getAutoClaimConfig();
            if (ext == null || !StringUtils.hasText(ext.getKeyword())) {
                return;
            }
            keyword = ext.getKeyword().trim();
        } else {
            return;
        }
        if (autoClaim == null || !Boolean.TRUE.equals(autoClaim.getEnabled())) {
            return;
        }
        LocationEntity location = locationService.getById(config.getLocationId());
        if (location == null) {
            log.warn("自动抢单监控位置不存在 configId={}, locationId={}", config.getId(), config.getLocationId());
            return;
        }
        List<StoreInfo> stores = xiaoChanService.searchList(
                keyword, location.getCityCode(), location.getLongitude(), location.getLatitude());
        matchedStores = matchStores(config, stores, keyword);
        if (matchedStores.isEmpty()) {
            return;
        }
        if (hasAmbiguousStoreIdentity(config, matchedStores)) {
            log.warn("关键词自动抢单跳过歧义门店 configId={}, keyword={}, matchedCount={}",
                    config.getId(), keyword, matchedStores.size());
            return;
        }
        List<StoreInfo> activeStores = matchedStores.stream()
                .filter(store -> store.getLeftNumber() != null && store.getLeftNumber() > 0)
                .filter(store -> isActiveAt(store, now.toLocalTime()))
                .toList();
        StoreInfo candidate = candidateSelector.select(activeStores).orElse(null);
        if (candidate == null) return;
        String key = now.toLocalDate() + ":" + config.getId() + ":"
                + candidate.getPromotionId() + ":" + candidate.getRebateCondition();
        if (handledKeys.contains(key) || !inFlightKeys.add(key)) {
            return;
        }
        try {
            taskScheduler.execute(() -> executeClaim(config, location, candidate, key));
        } catch (RuntimeException e) {
            inFlightKeys.remove(key);
            throw e;
        }
    }

    private void executeClaim(MonitorConfigEntity config, LocationEntity location,
                              StoreInfo candidate, String key) {
        try {
            StoreAutoClaimResult result = claimService.execute(config, location, candidate);
            if (result.stopReason() != StoreAutoClaimStopReason.MAX_ATTEMPTS_REACHED) {
                handledKeys.add(key);
            }
            log.info("自动抢单完成 configId={}, promotionId={}, attempts={}, success={}, reason={}",
                    config.getId(), candidate.getPromotionId(), result.attempts(), result.success(), result.stopReason());
        } catch (Exception e) {
            log.error("自动抢单执行异常 configId={}, promotionId={}",
                    config.getId(), candidate.getPromotionId(), e);
        } finally {
            inFlightKeys.remove(key);
        }
    }

    private List<StoreInfo> matchStores(MonitorConfigEntity config, List<StoreInfo> stores, String keyword) {
        if (stores == null) return List.of();
        if (config.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreExtNotifyConfig.class);
            String uniqId = ext == null || ext.getStoreInfo() == null ? null : ext.getStoreInfo().getUniqId();
            return stores.stream()
                    .filter(store -> StringUtils.hasText(uniqId) && uniqId.equals(store.getUniqId()))
                    .toList();
        }
        StoreKeywordExtNotifyConfig ext = JSON.parseObject(config.getExtConfig(), StoreKeywordExtNotifyConfig.class);
        boolean limitDistance = ext == null || ext.getLimitDistance() == null || ext.getLimitDistance();
        return stores.stream()
                .filter(store -> keyword.equals(store.getName()))
                .filter(store -> !limitDistance || withinDistance(store))
                .toList();
    }

    private boolean hasAmbiguousStoreIdentity(MonitorConfigEntity config, List<StoreInfo> stores) {
        if (config.getType() != MonitorTypeEnums.STORE_KEYWORD) return false;
        Set<String> identities = stores.stream()
                .map(this::storeIdentity)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        boolean hasUnknownIdentity = stores.stream().anyMatch(store -> storeIdentity(store) == null);
        return identities.size() > 1 || hasUnknownIdentity || (identities.isEmpty() && stores.size() > 1);
    }

    private String storeIdentity(StoreInfo store) {
        if (StringUtils.hasText(store.getUniqId())) return "uniq:" + store.getUniqId();
        return store.getStoreId() == null ? null : "store:" + store.getStoreId();
    }

    private boolean withinDistance(StoreInfo store) {
        if (!StringUtils.hasText(store.getDistance())) return false;
        try {
            return Long.parseLong(store.getDistance()) <= 3500;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private <T> Stream<T> safeList(List<T> values) {
        return values == null ? Stream.empty() : values.stream();
    }

    static boolean isActiveAt(StoreInfo store, LocalTime now) {
        if (!StringUtils.hasText(store.getStartTime()) || !StringUtils.hasText(store.getEndTime())) {
            return true;
        }
        try {
            LocalTime start = LocalTime.parse(store.getStartTime(), TIME_FORMAT);
            LocalTime end = LocalTime.parse(store.getEndTime(), TIME_FORMAT);
            if (end.isBefore(start)) {
                return !now.isBefore(start) || !now.isAfter(end);
            }
            return !now.isBefore(start) && now.isBefore(end.plusMinutes(1));
        } catch (DateTimeParseException e) {
            log.warn("活动时间格式无法解析，按可抢处理 storeId={}, start={}, end={}",
                    store.getStoreId(), store.getStartTime(), store.getEndTime());
            return true;
        }
    }

    private boolean isMonitorWindowActive(MonitorConfigEntity config, LocalDateTime now) {
        if (StringUtils.hasText(config.getCron())) {
            return true;
        }
        if (config.getStartHour() == null || config.getEndHour() == null
                || !StringUtils.hasText(config.getWeeks())) {
            return false;
        }
        boolean weekMatches = java.util.Arrays.stream(config.getWeeks().split(","))
                .map(String::trim)
                .anyMatch(value -> value.equals(String.valueOf(now.getDayOfWeek().getValue())));
        if (!weekMatches) {
            return false;
        }
        int hour = now.getHour();
        if (config.getStartHour() <= config.getEndHour()) {
            return hour >= config.getStartHour() && hour < config.getEndHour();
        }
        return hour >= config.getStartHour() || hour < config.getEndHour();
    }
}
