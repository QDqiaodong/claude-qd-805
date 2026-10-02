package com.salt.field.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.DailyWeatherStat;

public interface WeatherLogRepository extends JpaRepository<WeatherLog, Long> {

    /**
     * 手写 JPQL ③：按日期汇总蒸发量 / 降雨量 / 平均卤度（气象折线页数据源）
     */
    @Query("select new com.salt.field.dto.DailyWeatherStat(w.recordDate, sum(w.evaporation), sum(w.rainfall), "
            + "avg(w.temperature), avg(w.brineDegree), count(w)) "
            + "from WeatherLog w "
            + "where w.recordDate >= :from "
            + "group by w.recordDate "
            + "order by w.recordDate asc")
    List<DailyWeatherStat> statDaily(@Param("from") LocalDate from);

    List<WeatherLog> findByPondIdOrderByRecordDateDesc(Long pondId);

    /**
     * 手写 JPQL ④：查某池子在窗口期内的降雨记录（雨后禁止收盐判据）
     */
    @Query("select w from WeatherLog w "
            + "where w.pondId = :pondId and w.rainfall > 0 and w.recordedAt >= :from "
            + "order by w.recordedAt desc")
    List<WeatherLog> findRecentRain(@Param("pondId") Long pondId, @Param("from") LocalDateTime from);

    @Query("select w from WeatherLog w where w.pondId = :pondId and w.rainfall > 0 "
            + "order by w.recordedAt desc, w.id desc")
    List<WeatherLog> findRainByPond(@Param("pondId") Long pondId);

    default Optional<WeatherLog> findLatestRain(Long pondId) {
        List<WeatherLog> rains = findRainByPond(pondId);
        return rains.isEmpty() ? Optional.empty() : Optional.of(rains.get(0));
    }

    @Query("select w from WeatherLog w where w.recordDate = :date order by w.pondId asc")
    List<WeatherLog> findByDate(@Param("date") LocalDate date);
}
