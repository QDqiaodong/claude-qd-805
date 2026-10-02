package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.Pond;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.DailyWeatherStat;
import com.salt.field.dto.WeatherCreateRequest;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.WeatherLogRepository;

@Service
public class WeatherService {

    private final WeatherLogRepository weatherRepository;
    private final PondRepository pondRepository;
    private final RainControlService rainControlService;

    public WeatherService(WeatherLogRepository weatherRepository,
                          PondRepository pondRepository,
                          RainControlService rainControlService) {
        this.weatherRepository = weatherRepository;
        this.pondRepository = pondRepository;
        this.rainControlService = rainControlService;
    }

    /** 近 N 天的日汇总（蒸发量 / 降雨量 / 平均卤度 / 是否可收盐） */
    public List<Map<String, Object>> dailySeries(int days) {
        int n = days <= 0 ? 14 : days;
        LocalDate from = LocalDate.now().minusDays(n - 1L);
        List<DailyWeatherStat> stats = weatherRepository.statDaily(from);
        Map<LocalDate, DailyWeatherStat> byDate = new LinkedHashMap<>();
        stats.forEach(s -> byDate.put(s.recordDate(), s));

        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DailyWeatherStat s = byDate.get(date);
            BigDecimal evap = s == null || s.evaporationTotal() == null ? BigDecimal.ZERO : s.evaporationTotal();
            BigDecimal rain = s == null || s.rainfallTotal() == null ? BigDecimal.ZERO : s.rainfallTotal();
            Double temp = s == null ? null : s.avgTemperature();
            Double brine = s == null ? null : s.avgBrine();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordDate", date);
            row.put("evaporation", evap);
            row.put("rainfall", rain);
            row.put("temperature", temp);
            row.put("avgBrine", brine);
            row.put("recordCount", s == null ? 0L : s.recordCount());
            row.put("rainy", rain.compareTo(BigDecimal.ZERO) > 0);
            row.put("harvestable", rain.compareTo(BigDecimal.ZERO) == 0
                    && brine != null && brine >= FieldDict.HARVEST_MIN_BRINE.doubleValue());
            series.add(row);
        }
        return series;
    }

    public List<WeatherLog> byPond(Long pondId) {
        return weatherRepository.findByPondIdOrderByRecordDateDesc(pondId);
    }

    public List<WeatherLog> byDate(LocalDate date) {
        return weatherRepository.findByDate(date == null ? LocalDate.now() : date);
    }

    /** 雨后 24 小时内的降雨记录（收盐规则判据） */
    public List<WeatherLog> recentRain(Long pondId) {
        return weatherRepository.findRecentRain(pondId, LocalDateTime.now().minusHours(FieldDict.RAIN_BLOCK_HOURS));
    }

    public WeatherLog latestRain(Long pondId) {
        List<WeatherLog> rains = recentRain(pondId);
        return rains.isEmpty() ? null : rains.get(0);
    }

    @Transactional
    public WeatherLog create(WeatherCreateRequest request) {
        Pond pond = pondRepository.findByIdForUpdate(request.getPondId())
                .orElseThrow(() -> new BizException("池子不存在：id=" + request.getPondId()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recordedAt = request.getRecordedAt() == null ? now : request.getRecordedAt();
        WeatherLog log = new WeatherLog();
        log.setPondId(pond.getId());
        log.setRecordDate(request.getRecordDate() == null ? recordedAt.toLocalDate() : request.getRecordDate());
        log.setRecordedAt(recordedAt);
        log.setBrineDegree(nz(request.getBrineDegree()));
        log.setEvaporation(nz(request.getEvaporation()));
        log.setRainfall(nz(request.getRainfall()));
        log.setTemperature(request.getTemperature());
        log.setWindLevel(request.getWindLevel() == null ? 0 : request.getWindLevel());
        log.setRecorder(request.getRecorder() == null || request.getRecorder().isBlank()
                ? "气象员" : request.getRecorder());
        if (log.getRainfall().signum() < 0 || log.getEvaporation().signum() < 0) {
            throw new BizException("降雨量和蒸发量不能为负数");
        }
        if (log.getBrineDegree().signum() > 0) {
            pond.setBrineDegree(log.getBrineDegree());
            pondRepository.save(pond);
        }

        // 先落库再停工，保证停工/重置记录引用的降雨行可追溯。
        WeatherLog saved = weatherRepository.saveAndFlush(log);
        if (saved.getRainfall().signum() > 0) {
            rainControlService.applyRain(pond, saved);
        }
        return saved;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
