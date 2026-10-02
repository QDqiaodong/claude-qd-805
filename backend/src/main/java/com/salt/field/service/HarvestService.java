package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.HarvestRainStop;
import com.salt.field.domain.HarvestTask;
import com.salt.field.domain.Pond;
import com.salt.field.domain.SaltPlatform;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.HarvestCreateRequest;
import com.salt.field.dto.HarvestVO;
import com.salt.field.dto.TeamHarvestStat;
import com.salt.field.repository.HarvestRainStopRepository;
import com.salt.field.repository.HarvestTaskRepository;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.SaltPlatformRepository;
import com.salt.field.repository.WeatherLogRepository;

@Service
public class HarvestService {

    private final HarvestTaskRepository harvestRepository;
    private final HarvestRainStopRepository rainStopRepository;
    private final PondRepository pondRepository;
    private final SaltPlatformRepository platformRepository;
    private final WeatherLogRepository weatherRepository;
    private final FieldLogService fieldLogService;
    private final RainControlService rainControlService;

    public HarvestService(HarvestTaskRepository harvestRepository,
                          HarvestRainStopRepository rainStopRepository,
                          PondRepository pondRepository,
                          SaltPlatformRepository platformRepository,
                          WeatherLogRepository weatherRepository,
                          FieldLogService fieldLogService,
                          RainControlService rainControlService) {
        this.harvestRepository = harvestRepository;
        this.rainStopRepository = rainStopRepository;
        this.pondRepository = pondRepository;
        this.platformRepository = platformRepository;
        this.weatherRepository = weatherRepository;
        this.fieldLogService = fieldLogService;
        this.rainControlService = rainControlService;
    }

    /** 某日的作业班次条 + 班组汇总 + 进度环数据 */
    public Map<String, Object> board(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<HarvestTask> tasks = harvestRepository.findByDate(day);
        List<HarvestVO> rows = new ArrayList<>();
        for (HarvestTask t : tasks) {
            rows.add(toVO(t));
        }
        BigDecimal doneTons = harvestRepository.sumDoneTons(day);
        BigDecimal plannedTons = rows.stream()
                .filter(r -> !"CANCELED".equals(r.getTaskStatus()))
                .map(HarvestVO::getTons)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workDate", day);
        result.put("tasks", rows);
        result.put("teamStats", harvestRepository.statByTeam(day));
        result.put("doneTons", doneTons == null ? BigDecimal.ZERO : doneTons);
        result.put("planTons", FieldDict.DAILY_PLAN_TONS);
        result.put("scheduledTons", plannedTons);
        return result;
    }

    /**
     * 下达收盐作业。三条业务规则按顺序校验，命中即抛 BizException：
     * ① 池板状况为「需修」不允许排作业；② 卤度未达 25°Bé 不允许；③ 雨后 24 小时内不允许。
     */
    @Transactional
    public HarvestVO create(HarvestCreateRequest request) {
        Pond pond = pondRepository.findByIdForUpdate(request.getPondId())
                .orElseThrow(() -> new BizException("池子不存在：id=" + request.getPondId()));
        SaltPlatform platform = platformRepository.findById(request.getPlatformId())
                .orElseThrow(() -> new BizException("坨台不存在：id=" + request.getPlatformId()));

        assertCanHarvestNow(pond);

        BigDecimal tons = request.getTons();
        if (tons == null || tons.signum() <= 0) {
            throw new BizException("收盐吨数必须大于 0");
        }
        if (request.getDurationHours() == null || request.getDurationHours().signum() <= 0) {
            throw new BizException("作业时长必须大于 0 小时");
        }

        HarvestTask task = new HarvestTask();
        task.setTaskNo(nextTaskNo());
        task.setPondId(pond.getId());
        task.setPlatformId(platform.getId());
        task.setWorkDate(request.getWorkDate() == null ? LocalDate.now() : request.getWorkDate());
        task.setTeamName(request.getTeamName());
        task.setStartHour(request.getStartHour());
        task.setDurationHours(request.getDurationHours());
        task.setTons(tons);
        task.setWorkMode(request.getWorkMode() == null ? "MACHINE" : request.getWorkMode());
        task.setTaskStatus("PLANNED");
        task.setOperator(request.getOperator() == null ? "调度员" : request.getOperator());
        HarvestTask saved = harvestRepository.save(task);

        fieldLogService.write("HARVEST", pond,
                "下达收盐作业 " + saved.getTaskNo() + "，" + saved.getTeamName() + " 计划收盐 "
                        + tons.stripTrailingZeros().toPlainString() + " 吨 → 坨台 " + platform.getPlatformNo(),
                saved.getOperator());
        return toVO(saved);
    }

    /**
     * 改作业状态。
     * 已停工的班次必须走复工确认；开始或完成时都重新在同池锁内核验雨后窗口，
     * 防止降雨登记与状态操作并发时绕过 24 小时限制入账。
     */
    @Transactional
    public HarvestVO updateStatus(Long taskId, String status) {
        if (!FieldDict.isValidTaskStatus(status)) {
            throw new BizException("作业状态取值有误：" + status
                    + "（应为 PLANNED/WORKING/RAIN_STOPPED/DONE/CANCELED）");
        }
        HarvestTask task = harvestRepository.findById(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));
        Pond pond = pondRepository.findByIdForUpdate(task.getPondId())
                .orElseThrow(() -> new BizException("池子不存在：id=" + task.getPondId()));
        task = harvestRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));

        String old = task.getTaskStatus();
        if ("DONE".equals(old) || "CANCELED".equals(old)) {
            throw new BizException("作业单已" + FieldDict.taskStatusName(old) + "，终态作业保持原状");
        }
        if ("RAIN_STOPPED".equals(old)) {
            throw new BizException("作业处于雨情停工，必须在满足雨后窗口、卤度和池板条件后由调度员确认复工");
        }
        if ("RAIN_STOPPED".equals(status)) {
            throw new BizException("雨情停工只能由有效降雨登记自动触发，不能手工设置");
        }

        if ("WORKING".equals(status) || "DONE".equals(status)) {
            assertCanHarvestNow(pond);
        }

        if ("CANCELED".equals(status) && !"CANCELED".equals(old)) {
            rainControlService.closeByCancel(task, pond, task.getOperator());
        }
        task.setTaskStatus(status);

        if ("DONE".equals(status) && !"DONE".equals(old)) {
            completeIntoPlatform(task);
        } else if ("WORKING".equals(status) && !"WORKING".equals(old)) {
            fieldLogService.write("HARVEST", pond,
                    "作业单 " + task.getTaskNo() + " 开始作业，班组：" + task.getTeamName(),
                    task.getOperator());
        } else if ("CANCELED".equals(status) && !"CANCELED".equals(old)) {
            fieldLogService.write("HARVEST", pond,
                    "作业单 " + task.getTaskNo() + " 已取消", task.getOperator());
        }
        HarvestTask saved = harvestRepository.save(task);
        return toVO(saved);
    }

    /** 调度员人工确认雨后复工。 */
    @Transactional
    public HarvestVO resume(Long taskId, String operator) {
        HarvestTask task = harvestRepository.findById(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));
        Pond pond = pondRepository.findByIdForUpdate(task.getPondId())
                .orElseThrow(() -> new BizException("池子不存在：id=" + task.getPondId()));
        task = harvestRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));
        HarvestTask resumed = rainControlService.resume(task, pond, operator);
        return toVO(resumed);
    }

    private void assertCanHarvestNow(Pond pond) {
        if ("REPAIR".equals(pond.getBoardStatus())) {
            throw new BizException("池板状况为「需修」，不允许收盐作业：池号 " + pond.getPondNo());
        }

        BigDecimal brine = pond.getBrineDegree() == null ? BigDecimal.ZERO : pond.getBrineDegree();
        if (brine.compareTo(FieldDict.HARVEST_MIN_BRINE) < 0) {
            throw new BizException("卤度未达标，不允许收盐：池号 " + pond.getPondNo()
                    + " 当前 " + brine.stripTrailingZeros().toPlainString() + "°Bé，门槛 "
                    + FieldDict.HARVEST_MIN_BRINE + "°Bé");
        }

        WeatherLog rain = weatherRepository.findLatestRain(pond.getId()).orElse(null);
        if (rain != null) {
            LocalDateTime earliest = rain.getRecordedAt().plusHours(FieldDict.RAIN_BLOCK_HOURS);
            if (earliest.isAfter(LocalDateTime.now())) {
                throw new BizException("雨后 " + FieldDict.RAIN_BLOCK_HOURS + " 小时窗口内不允许收盐：池号 "
                        + pond.getPondNo() + " 于 " + rain.getRecordedAt() + " 记录降雨 "
                        + rain.getRainfall().stripTrailingZeros().toPlainString()
                        + "mm，最早可作业 " + earliest);
            }
        }
    }

    private void completeIntoPlatform(HarvestTask task) {
        BigDecimal tons = task.getTons() == null ? BigDecimal.ZERO : task.getTons();
        SaltPlatform platform = task.getPlatformId() == null ? null
                : platformRepository.findById(task.getPlatformId()).orElse(null);
        if (platform == null) {
            throw new BizException("作业单 " + task.getTaskNo() + " 的坨台去向不存在，无法完成入账");
        }
        BigDecimal stock = platform.getStockTons() == null ? BigDecimal.ZERO : platform.getStockTons();
        if (platform.getCapacityTons() != null
                && stock.add(tons).compareTo(platform.getCapacityTons()) > 0) {
            throw new BizException("坨台 " + platform.getPlatformNo() + " 容量不足：现有 "
                    + stock.stripTrailingZeros().toPlainString() + " 吨，容量 "
                    + platform.getCapacityTons().stripTrailingZeros().toPlainString() + " 吨");
        }
        platform.setStockTons(stock.add(tons));
        platformRepository.save(platform);
        // @Any 多态关联指向「坨台」
        fieldLogService.write("HARVEST", platform,
                "作业单 " + task.getTaskNo() + " 完成，入库 "
                        + tons.stripTrailingZeros().toPlainString() + " 吨",
                task.getOperator());
    }

    private String nextTaskNo() {
        return "HS" + LocalDate.now().toString().replace("-", "")
                + String.format("%03d", harvestRepository.count() + 1);
    }

    public HarvestVO toVO(HarvestTask t) {
        Pond pond = pondRepository.findById(t.getPondId()).orElse(null);
        SaltPlatform platform = t.getPlatformId() == null ? null
                : platformRepository.findById(t.getPlatformId()).orElse(null);
        HarvestVO vo = HarvestVO.of(t.getId(), t.getTaskNo(), t.getPondId(),
                pond == null ? null : pond.getPondNo(),
                pond == null ? null : pond.getName(),
                t.getPlatformId(),
                platform == null ? null : platform.getPlatformNo(),
                t.getWorkDate(), t.getTeamName(), t.getStartHour(),
                t.getDurationHours(), t.getTons(), t.getWorkMode(), t.getTaskStatus(), t.getOperator());
        vo.setPreviousStatus(t.getPreviousStatus());
        vo.setPreviousStatusName(FieldDict.taskStatusName(t.getPreviousStatus()));
        vo.setStoppedAt(t.getStoppedAt());
        vo.setRainAmount(t.getRainAmount());
        vo.setEarliestResumeAt(t.getRainResumeAt());
        vo.setResumedAt(t.getResumedAt());

        Optional<HarvestRainStop> stop = rainStopRepository.findActiveByTaskId(t.getId());
        if (stop.isPresent()) {
            WeatherLog rain = weatherRepository.findById(stop.get().getLatestWeatherId()).orElse(null);
            vo.setRainAt(stop.get().getRainAt());
            vo.setStopReason(stop.get().getStopReason());
            vo.setRainStopped(true);
            if (pond != null) {
                List<String> blockers = rainControlService.resumeBlockers(
                        pond, rain, LocalDateTime.now());
                vo.setResumeBlockers(blockers);
                vo.setCanResume(blockers.isEmpty());
            }
        }
        return vo;
    }
}
