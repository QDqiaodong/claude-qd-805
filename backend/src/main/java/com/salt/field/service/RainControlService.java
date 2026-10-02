package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.HarvestRainStop;
import com.salt.field.domain.HarvestTask;
import com.salt.field.domain.Pond;
import com.salt.field.domain.WeatherLog;
import com.salt.field.repository.HarvestRainStopRepository;
import com.salt.field.repository.HarvestTaskRepository;
import com.salt.field.repository.WeatherLogRepository;

/**
 * 雨后停工与复工规则。调用方必须先锁 pond 行，保证同池的降雨登记、
 * 作业开始/完成、取消和复工确认按同一顺序串行执行。
 */
@Service
public class RainControlService {

    private final HarvestTaskRepository harvestRepository;
    private final HarvestRainStopRepository stopRepository;
    private final WeatherLogRepository weatherRepository;
    private final FieldLogService fieldLogService;

    public RainControlService(HarvestTaskRepository harvestRepository,
                              HarvestRainStopRepository stopRepository,
                              WeatherLogRepository weatherRepository,
                              FieldLogService fieldLogService) {
        this.harvestRepository = harvestRepository;
        this.stopRepository = stopRepository;
        this.weatherRepository = weatherRepository;
        this.fieldLogService = fieldLogService;
    }

    /** 有效降雨登记后，立即停工同池所有未完成作业；停工中再次降雨则重置窗口。 */
    @Transactional
    public int applyRain(Pond pond, WeatherLog rain) {
        LocalDateTime rainAt = rain.getRecordedAt();
        LocalDateTime earliestResumeAt = rainAt.plusHours(FieldDict.RAIN_BLOCK_HOURS);
        List<HarvestTask> openTasks = harvestRepository.findOpenByPondForUpdate(pond.getId());
        if (!earliestResumeAt.isAfter(LocalDateTime.now())) {
            return 0;
        }
        int affected = 0;
        for (HarvestTask task : openTasks) {
            HarvestRainStop stop = stopRepository.findActiveByTaskId(task.getId()).orElse(null);
            if (stop == null) {
                stopTask(task, pond, rain, rainAt, earliestResumeAt);
            } else {
                resetStopWindow(task, pond, stop, rain, rainAt, earliestResumeAt);
            }
            affected++;
        }
        return affected;
    }

    /** 调度员确认复工；任一硬性条件不满足都返回完整拦截项，不做部分修改。 */
    @Transactional
    public HarvestTask resume(HarvestTask task, Pond pond, String operator) {
        if (!"RAIN_STOPPED".equals(task.getTaskStatus())) {
            throw new BizException("作业 " + task.getTaskNo() + " 当前不在雨情停工状态");
        }
        HarvestRainStop stop = stopRepository.findActiveByTaskId(task.getId())
                .orElseThrow(() -> new BizException("作业 " + task.getTaskNo() + " 缺少进行中的雨后停工记录"));
        WeatherLog rain = weatherRepository.findById(stop.getLatestWeatherId())
                .orElseThrow(() -> new BizException("停工降雨记录不存在：id=" + stop.getLatestWeatherId()));

        List<String> blockers = resumeBlockers(pond, rain, LocalDateTime.now());
        if (!blockers.isEmpty()) {
            throw new com.salt.field.common.BizException("暂不能确认复工：" + String.join("；", blockers));
        }

        LocalDateTime now = LocalDateTime.now();
        String resumeOperator = operator == null || operator.isBlank() ? "调度员" : operator;
        String targetStatus = stop.getPreviousStatus() == null ? "PLANNED" : stop.getPreviousStatus();

        stop.setResumedAt(now);
        stop.setResumeOperator(resumeOperator);
        stop.setEndReason("RESUMED");
        stopRepository.save(stop);

        task.setTaskStatus(targetStatus);
        task.setPreviousStatus(null);
        task.setResumedAt(now);
        task.setStoppedAt(null);
        task.setRainLogId(null);
        task.setRainWeatherId(null);
        task.setRainAmount(null);
        task.setRainResumeAt(null);
        harvestRepository.save(task);

        fieldLogService.write("RAIN_STOP", pond,
                "作业单 " + task.getTaskNo() + " 雨后复工确认，恢复为「"
                        + FieldDict.taskStatusName(targetStatus) + "」；最近降雨 "
                        + rainAmount(rain) + "mm（" + rain.getRecordedAt() + "）",
                resumeOperator);
        return task;
    }

    /** 取消停工作业时闭环进行中的停工记录；已完成/已取消作业不受新降雨影响。 */
    @Transactional
    public void closeByCancel(HarvestTask task, Pond pond, String operator) {
        stopRepository.findActiveByTaskId(task.getId()).ifPresent(stop -> {
            stop.setResumedAt(LocalDateTime.now());
            stop.setResumeOperator(operator);
            stop.setEndReason("CANCELED");
            stopRepository.save(stop);
            fieldLogService.write("RAIN_STOP", pond,
                    "作业单 " + task.getTaskNo() + " 已取消，关闭雨情停工记录", operator);
        });
        clearTaskRainState(task);
    }

    public List<String> resumeBlockers(Pond pond, WeatherLog latestRain, LocalDateTime now) {
        List<String> blockers = new java.util.ArrayList<>();
        if (latestRain == null) {
            blockers.add("缺少有效降雨记录，无法核验雨后窗口");
        } else {
            LocalDateTime earliest = latestRain.getRecordedAt().plusHours(FieldDict.RAIN_BLOCK_HOURS);
            if (earliest.isAfter(now)) {
                blockers.add("雨后尚未满 " + FieldDict.RAIN_BLOCK_HOURS + " 小时："
                        + latestRain.getRecordedAt() + " 起算，最早 " + earliest + " 复工");
            }
        }
        BigDecimal brine = pond.getBrineDegree() == null ? BigDecimal.ZERO : pond.getBrineDegree;
        if (brine.compareTo(FieldDict.HARVEST_MIN_BRINE) < 0) {
            blockers.add("最新卤度仍低于 " + FieldDict.HARVEST_MIN_BRINE + "°Bé：当前 "
                    + brine.stripTrailingZeros().toPlainString() + "°Bé");
        }
        if ("REPAIR".equals(pond.getBoardStatus())) {
            blockers.add("池板状况为「需修」，修复前不得复工");
        }
        return blockers;
    }

    private void stopTask(HarvestTask task, Pond pond, WeatherLog rain,
                          LocalDateTime rainAt, LocalDateTime earliestResumeAt) {
        String reason = stopReason(rain);
        HarvestRainStop stop = new HarvestRainStop();
        stop.setTaskId(task.getId());
        stop.setPondId(pond.getId());
        stop.setWeatherLogId(rain.getId());
        stop.setLatestWeatherId(rain.getId());
        stop.setRainfall(rainAmount(rain));
        stop.setPreviousStatus(task.getTaskStatus());
        stop.setStopReason(reason);
        stop.setStoppedAt(rainAt);
        stop.setRainAt(rainAt);
        stop.setEarliestResumeAt(earliestResumeAt);
        stopRepository.save(stop);

        task.setPreviousStatus(task.getTaskStatus());
        task.setStoppedAt(rainAt);
        applyTaskRain(task, rain, rainAt, earliestResumeAt);
        task.setTaskStatus("RAIN_STOPPED");
        harvestRepository.save(task);

        fieldLogService.write("RAIN_STOP", pond,
                "作业单 " + task.getTaskNo() + " 因" + reason + "自动停工；原状态「"
                        + FieldDict.taskStatusName(stop.getPreviousStatus()) + "」，最早可复工 "
                        + earliestResumeAt,
                rain.getRecorder());
    }

    private void resetStopWindow(HarvestTask task, Pond pond, HarvestRainStop stop,
                                 WeatherLog rain, LocalDateTime rainAt, LocalDateTime earliestResumeAt) {
        stop.setLatestWeatherId(rain.getId());
        stop.setRainfall(rainAmount(rain));
        stop.setRainAt(rainAt);
        stop.setEarliestResumeAt(earliestResumeAt);
        stop.setStopReason(stopReason(rain));
        stopRepository.save(stop);

        applyTaskRain(task, rain, rainAt, earliestResumeAt);
        task.setTaskStatus("RAIN_STOPPED");
        harvestRepository.save(task);

        fieldLogService.write("RAIN_STOP", pond,
                "作业单 " + task.getTaskNo() + " 停工期间再遇有效降雨 " + rainAmount(rain)
                        + "mm，复工窗口自 " + rainAt + " 重新起算，最早 " + earliestResumeAt,
                rain.getRecorder());
    }

    private void applyTaskRain(HarvestTask task, WeatherLog rain,
                               LocalDateTime rainAt, LocalDateTime earliestResumeAt) {
        task.setRainLogId(rain.getId());
        task.setRainWeatherId(rain.getId());
        task.setRainAmount(rainAmount(rain));
        task.setRainResumeAt(earliestResumeAt);
    }

    private void clearTaskRainState(HarvestTask task) {
        task.setPreviousStatus(null);
        task.setStoppedAt(null);
        task.setRainLogId(null);
        task.setRainWeatherId(null);
        task.setRainAmount(null);
        task.setRainResumeAt(null);
        task.setResumedAt(null);
    }

    private String stopReason(WeatherLog rain) {
        return "有效降雨 " + rainAmount(rain).stripTrailingZeros().toPlainString()
                + "mm，雨后 " + FieldDict.RAIN_BLOCK_HOURS + " 小时内禁止收盐";
    }

    private BigDecimal rainAmount(WeatherLog rain) {
        return rain.getRainfall() == null ? BigDecimal.ZERO : rain.getRainfall();
    }
}
