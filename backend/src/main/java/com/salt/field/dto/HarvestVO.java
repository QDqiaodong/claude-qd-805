package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 收盐作业单视图对象（班次条用）
 */
@Getter
@Setter
public class HarvestVO {

    private Long id;
    private String taskNo;
    private Long pondId;
    private String pondNo;
    private String pondName;
    private Long platformId;
    private String platformNo;
    private LocalDate workDate;
    private String teamName;
    private BigDecimal startHour;
    private BigDecimal durationHours;
    private BigDecimal tons;
    private String workMode;
    private String workModeName;
    private String taskStatus;
    private String taskStatusName;
    private String previousStatus;
    private String previousStatusName;
    private Boolean rainStopped;
    private String stopReason;
    private LocalDateTime stoppedAt;
    private LocalDateTime rainAt;
    private BigDecimal rainAmount;
    private LocalDateTime earliestResumeAt;
    private LocalDateTime resumedAt;
    private Boolean canResume;
    private List<String> resumeBlockers = new ArrayList<>();
    private String operator;

    public static HarvestVO of(Long id,
                               String taskNo,
                               Long pondId,
                               String pondNo,
                               String pondName,
                               Long platformId,
                               String platformNo,
                               LocalDate workDate,
                               String teamName,
                               BigDecimal startHour,
                               BigDecimal durationHours,
                               BigDecimal tons,
                               String workMode,
                               String taskStatus,
                               String operator) {
        HarvestVO v = new HarvestVO();
        v.id = id;
        v.taskNo = taskNo;
        v.pondId = pondId;
        v.pondNo = pondNo;
        v.pondName = pondName;
        v.platformId = platformId;
        v.platformNo = platformNo;
        v.workDate = workDate;
        v.teamName = teamName;
        v.startHour = startHour;
        v.durationHours = durationHours;
        v.tons = tons;
        v.workMode = workMode;
        v.workModeName = FieldDict.workModeName(workMode);
        v.taskStatus = taskStatus;
        v.taskStatusName = FieldDict.taskStatusName(taskStatus);
        v.rainStopped = "RAIN_STOPPED".equals(taskStatus);
        v.canResume = false;
        v.operator = operator;
        return v;
    }
}
