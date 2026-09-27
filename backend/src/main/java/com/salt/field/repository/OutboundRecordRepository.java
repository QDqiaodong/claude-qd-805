package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salt.field.domain.OutboundRecord;

public interface OutboundRecordRepository extends JpaRepository<OutboundRecord, Long> {

    List<OutboundRecord> findByPlatformIdOrderByOutDateDesc(Long platformId);

    List<OutboundRecord> findTop10ByOrderByOutDateDesc();
}
