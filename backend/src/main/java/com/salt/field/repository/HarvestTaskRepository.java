package com.salt.field.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.HarvestTask;
import com.salt.field.dto.TeamHarvestStat;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface HarvestTaskRepository extends JpaRepository<HarvestTask, Long> {

    @Query("select t from HarvestTask t where t.workDate = :date order by t.teamName asc, t.startHour asc")
    List<HarvestTask> findByDate(@Param("date") LocalDate date);

    /**
     * 手写 JPQL ⑤：按班组汇总收盐吨数与作业时长（班次条右侧统计）
     */
    @Query("select new com.salt.field.dto.TeamHarvestStat(t.teamName, sum(t.tons), sum(t.durationHours), count(t)) "
            + "from HarvestTask t where t.workDate = :date and t.taskStatus <> 'CANCELED' "
            + "group by t.teamName "
            + "order by sum(t.tons) desc")
    List<TeamHarvestStat> statByTeam(@Param("date") LocalDate date);

    @Query("select coalesce(sum(t.tons), 0) from HarvestTask t where t.workDate = :date and t.taskStatus = 'DONE'")
    java.math.BigDecimal sumDoneTons(@Param("date") LocalDate date);

    @Query("select t from HarvestTask t where t.pondId = :pondId order by t.workDate desc")
    List<HarvestTask> findByPond(@Param("pondId") Long pondId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from HarvestTask t where t.id = :id")
    Optional<HarvestTask> findByIdForUpdate(@Param("id") Long id);

    /** 雨后登记时只需影响仍未完成的作业；DONE/CANCELED 保持原状。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from HarvestTask t where t.pondId = :pondId and t.taskStatus in ('PLANNED','WORKING','RAIN_STOPPED') order by t.id asc")
    List<HarvestTask> findOpenByPondForUpdate(@Param("pondId") Long pondId);
}
