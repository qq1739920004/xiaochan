package io.github.xiaocan.tasks;

import com.alibaba.fastjson2.JSON;
import io.github.xiaocan.model.StoreExtNotifyConfig;
import io.github.xiaocan.model.StoreInfo;
import io.github.xiaocan.model.StoreKeywordExtNotifyConfig;
import io.github.xiaocan.model.entity.LocationEntity;
import io.github.xiaocan.model.entity.MonitorConfigEntity;
import io.github.xiaocan.model.entity.StorePushedHistoryEntity;
import io.github.xiaocan.model.entity.TaskExecHistoryEntity;
import io.github.xiaocan.model.enums.MonitorConfigStatusEnums;
import io.github.xiaocan.model.enums.MonitorTypeEnums;
import io.github.xiaocan.model.enums.NotifyFrequencyEnums;
import io.github.xiaocan.service.MonitoryConfigService;
import io.github.xiaocan.service.StorePushedHistoryService;
import io.github.xiaocan.service.XiaoChanService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author wangxiaodong
 * @date 2026/4/17
 */
@Component
@Slf4j
public class StoreTask extends BaseTask {


    @Resource
    private XiaoChanService xiaoChanService;
    @Resource
    private MonitoryConfigService monitoryConfigService;
    @Resource
    private StorePushedHistoryService storePushedHistoryService;
    @Resource
    private StoreAutoClaimTask storeAutoClaimTask;


    /**
     * 指定门店活动定时任务（静态兜底调度，仅处理未配置 cron 的配置）
     */
    @Scheduled(cron = "0 0/30 * * * ? ")
    public void start(){
        try {
            List<MonitorConfigEntity> all = monitoryConfigService.listWithoutCron(
                    List.of(MonitorTypeEnums.STORE_ACTIVITY, MonitorTypeEnums.STORE_KEYWORD), MonitorConfigStatusEnums.ENABLE);
            long storeActivityCount = all.stream().filter(c -> c.getType() == MonitorTypeEnums.STORE_ACTIVITY).count();
            long storeKeywordCount = all.stream().filter(c -> c.getType() == MonitorTypeEnums.STORE_KEYWORD).count();
            log.info("开始执行 门店活动定时任务 STORE_ACTIVITY:{}个，STORE_KEYWORD:{}个", storeActivityCount, storeKeywordCount);

            for (MonitorConfigEntity notifyConfig : all) {
                execute(notifyConfig, false);
            }
        }catch (Exception e){
            log.error("执行门店活动定时任务时发生异常", e);
        }
    }

    /**
     * 按配置执行任务入口
     *
     * @param cronDriven true 表示由 cron 动态调度器触发，跳过时间窗口和静默期检查
     */
    public void execute(MonitorConfigEntity notifyConfig, boolean cronDriven) {
        if (notifyConfig.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig storeExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreExtNotifyConfig.class);
            NotifyFrequencyEnums remindFrequency = storeExtNotifyConfig.getRemindFrequency();
            if (remindFrequency == null) {
                remindFrequency = NotifyFrequencyEnums.ONCE;
            }
            if (remindFrequency == NotifyFrequencyEnums.DAILY && hasPushedToday(notifyConfig.getId())) {
                log.info("configId: {} 今日已提醒，跳过本次执行", notifyConfig.getId());
                return;
            }
        }
        runSingle(notifyConfig, cronDriven);
    }

    private boolean hasPushedToday(Integer notifyConfigId) {
        return storePushedHistoryService.lambdaQuery()
                .eq(StorePushedHistoryEntity::getNotifyConfigId, notifyConfigId)
                .ge(StorePushedHistoryEntity::getCreateTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))
                .last("limit 1")
                .oneOpt().isPresent();
    }

    @Override
    protected void handleAvailableStores(MonitorConfigEntity notifyConfig, List<StoreInfo> availableStores,
                                         LocationEntity location) {
        if (notifyConfig.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig storeExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreExtNotifyConfig.class);
            NotifyFrequencyEnums remindFrequency = storeExtNotifyConfig.getRemindFrequency();
            if (remindFrequency == null) {
                remindFrequency = NotifyFrequencyEnums.ONCE;
            }
            if (remindFrequency == NotifyFrequencyEnums.NONE) {
                afterSuccess(notifyConfig, availableStores);
                log.info("configId: {} 配置为不提醒", notifyConfig.getId());
                return;
            }
        }
        savePushedHistory(notifyConfig, availableStores);
        afterSuccess(notifyConfig, availableStores);
        storeAutoClaimTask.claimDiscovered(notifyConfig, location, availableStores, LocalDateTime.now());
        sendMessage(notifyConfig, availableStores, location);
    }

    /**
     * 获取门店活动信息
     */
    @Override
    protected List<StoreInfo> fetchStoreInfos(MonitorConfigEntity notifyConfig, TaskExecHistoryEntity execHistory, LocationEntity location) {
        String keyword;
        if (notifyConfig.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            execHistory.setNotifyType(MonitorTypeEnums.STORE_ACTIVITY);
            StoreExtNotifyConfig storeExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreExtNotifyConfig.class);
            keyword = storeExtNotifyConfig.getStoreInfo().getName();
        } else {
            execHistory.setNotifyType(MonitorTypeEnums.STORE_KEYWORD);
            StoreKeywordExtNotifyConfig storeKeywordExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreKeywordExtNotifyConfig.class);
            keyword = storeKeywordExtNotifyConfig.getKeyword();
        }
        try {
            return xiaoChanService.searchList(keyword, location.getCityCode(), location.getLongitude(), location.getLatitude());
        } catch (Exception firstError) {
            if (!isReadTimeout(firstError)) {
                throw firstError;
            }
            log.warn("门店查询失败，200毫秒后重试一次 configId={}, keyword={}, message={}",
                    notifyConfig.getId(), keyword, firstError.getMessage());
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("门店查询重试被中断", interrupted);
            }
            return xiaoChanService.searchList(keyword, location.getCityCode(), location.getLongitude(), location.getLatitude());
        }
    }

    private boolean isReadTimeout(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 过滤指定门店活动
     */
    @Override
    protected List<StoreInfo> filterStoreInfos(MonitorConfigEntity notifyConfig, List<StoreInfo> storeInfos) {
        if (notifyConfig.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig storeExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreExtNotifyConfig.class);
            return storeInfos
                    .stream()
                    //同一个门店（按 uniqId 匹配，兼容美团赏金无 storeId 的情况）
                    .filter(storeInfo -> storeExtNotifyConfig.getStoreInfo().getUniqId().equals(storeInfo.getUniqId()))
                    .filter(this::withinDistanceLimit)
                    .filter(storeInfo -> storeInfo.getLeftNumber() > 0)
                    .toList();
        } else {
            // STORE_KEYWORD：过滤有库存 + 排除已通知过的门店（按配置ID + 门店ID）
            StoreKeywordExtNotifyConfig storeKeywordExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreKeywordExtNotifyConfig.class);
            return storeInfos.stream()
                    .filter(storeInfo -> storeInfo.getLeftNumber() > 0)
                    .filter(storeInfo -> storeKeywordExtNotifyConfig.getLimitDistance() == null
                            || !storeKeywordExtNotifyConfig.getLimitDistance()
                            || withinDistanceLimit(storeInfo))
                    .filter(storeInfo -> !hasPushedActivity(notifyConfig, storeInfo))
                    .toList();
        }
    }

    private boolean withinDistanceLimit(StoreInfo storeInfo) {
        if (storeInfo == null || storeInfo.getDistance() == null) return false;
        try {
            return Long.parseLong(storeInfo.getDistance()) <= 5000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean hasPushedActivity(MonitorConfigEntity notifyConfig, StoreInfo storeInfo) {
        Integer promotionId = parseInteger(storeInfo.getPromotionId());
        if (promotionId == null) {
            return storePushedHistoryService
                    .findByNotifyIdAndStoreIdAll(notifyConfig.getId(), storeInfo.getStoreId()) != null;
        }
        return storePushedHistoryService.findByNotifyIdAndActivity(
                notifyConfig.getId(), storeInfo.getStoreId(), promotionId,
                storeInfo.getType(), storeInfo.getRebateCondition()) != null;
    }

    private Integer parseInteger(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected void afterSuccess(MonitorConfigEntity notifyConfig, List<StoreInfo> availableStores) {
        super.afterSuccess(notifyConfig, availableStores);
        // 仅 STORE_ACTIVITY 且提醒频率为提醒一次时，通知后停用
        if (!availableStores.isEmpty() && notifyConfig.getType() == MonitorTypeEnums.STORE_ACTIVITY) {
            StoreExtNotifyConfig storeExtNotifyConfig = JSON.parseObject(notifyConfig.getExtConfig(), StoreExtNotifyConfig.class);
            NotifyFrequencyEnums remindFrequency = storeExtNotifyConfig.getRemindFrequency();
            if ((remindFrequency == null || remindFrequency == NotifyFrequencyEnums.ONCE)
                    && !isAutoClaimEnabled(storeExtNotifyConfig)) {
                monitoryConfigService.toggleStatus(notifyConfig.getId(), MonitorConfigStatusEnums.DISABLE);
            }
        }
    }

    private boolean isAutoClaimEnabled(StoreExtNotifyConfig config) {
        return config != null && config.getAutoClaimConfig() != null
                && Boolean.TRUE.equals(config.getAutoClaimConfig().getEnabled());
    }
}
