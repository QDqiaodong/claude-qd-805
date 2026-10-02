package com.salt.field.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.Pond;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface PondRepository extends JpaRepository<Pond, Long> {

    List<Pond> findByAreaIdOrderByGridRowAscGridColAsc(Long areaId);

    List<Pond> findByPondStatus(String pondStatus);

    /**
     * 手写 JPQL ②：按池区统计各状态池子数量（左菜单池区树的角标数据）
     */
    @Query("select new com.salt.field.dto.AreaBrineStat(a.id, a.name, a.areaType, count(p), avg(p.brineDegree), sum(p.areaMu)) "
            + "from Pond p join p.area a where p.pondStatus = :status "
            + "group by a.id, a.name, a.areaType "
            + "order by a.bandIndex asc")
    List<com.salt.field.dto.AreaBrineStat> statByAreaAndStatus(@Param("status") String status);

    @Query("select count(p) from Pond p where p.boardStatus = 'REPAIR'")
    long countRepairing();

    /** 串行化同一池的降雨登记、作业状态变更和复工确认。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from Pond p where p.id = :id")
    Optional<Pond> findByIdForUpdate(@Param("id") Long id);
}
