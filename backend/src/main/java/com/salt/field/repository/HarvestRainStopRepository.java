package com.salt.field.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.HarvestRainStop;

public interface HarvestRainStopRepository extends JpaRepository<HarvestRainStop, Long> {

    @Query("select s from HarvestRainStop s where s.taskId = :taskId and s.resumedAt is null")
    Optional<HarvestRainStop> findActiveByTaskId(@Param("taskId") Long taskId);

    @Query("select s from HarvestRainStop s where s.pondId = :pondId and s.resumedAt is null order by s.stoppedAt asc")
    List<HarvestRainStop> findActiveByPondId(@Param("pondId") Long pondId);

    @Query("select s from HarvestRainStop s where s.taskId in :taskIds and s.resumedAt is null order by s.stoppedAt asc")
    List<HarvestRainStop> findActiveByTaskIds(@Param("taskIds") List<Long> taskIds);

    @Query("select count(s) from HarvestRainStop s where s.pondId = :pondId and s.resumedAt is null")
    long countActiveByPondId(@Param("pondId") Long pondId);
}
