package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 收盐作业雨后停工闭环。一条记录表示一次尚未结束的或已经结束的雨情停工。
 * 同一作业同时只允许存在一条 resumedAt 为空的进行中记录（由数据库唯一约束兜底）。
 */
@Entity
@Table(name = "harvest_rain_stop")
@Getter
@Setter
public class HarvestRainStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "pond_id", nullable = false)
    private Long pondId;

    @Column(name = "weather_log_id", nullable = false)
    private Long weatherLogId;

    @Column(name = "latest_weather_id", nullable = false)
    private Long latestWeatherId;

    @Column(name = "rainfall", nullable = false, precision = 6, scale = 2)
    private BigDecimal rainfall;

    @Column(name = "previous_status", nullable = false, length = 16)
    private String previousStatus;

    @Column(name = "stop_reason", nullable = false, length = 255)
    private String stopReason;

    @Column(name = "stopped_at", nullable = false)
    private LocalDateTime stoppedAt;

    @Column(name = "rain_at", nullable = false)
    private LocalDateTime rainAt;

    @Column(name = "earliest_resume_at", nullable = false)
    private LocalDateTime earliestResumeAt;

    @Column(name = "resumed_at")
    private LocalDateTime resumedAt;

    @Column(name = "resume_operator", length = 32)
    private String resumeOperator;

    /** RESUMED 人工复工 / CANCELED 作业取消 */
    @Column(name = "end_reason", length = 32)
    private String endReason;
}