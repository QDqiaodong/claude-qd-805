package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.salt.field.domain.DispatchTask;

public interface DispatchTaskRepository extends JpaRepository<DispatchTask, Long> {

    @Query("select t from DispatchTask t order by t.planDate asc, t.id asc")
    List<DispatchTask> findAllOrdered();
}
