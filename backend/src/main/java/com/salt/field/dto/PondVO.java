package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 池子视图对象（前端平面 SVG 与 chips 条直接用它）
 */
@Getter
@Setter
public class PondVO {

    private Long id;
    private String pondNo;
    private String name;
    private Long areaId;
    private String areaName;
    private String areaType;
    private String areaTypeName;
    private BigDecimal areaMu;
    private String boardStatus;
    private String boardStatusName;
    private BigDecimal brineDegree;
    private BigDecimal brineDepth;
    private String pondStatus;
    private String pondStatusName;
    private Integer gridRow;
    private Integer gridCol;
    private Boolean harvestable;
    private Boolean rainStopped;
    private String effectiveStatus;
    private String effectiveStatusName;
    private String stopReason;
    private LocalDateTime rainAt;
    private BigDecimal rainAmount;
    private LocalDateTime stoppedAt;
    private LocalDateTime earliestResumeAt;
    private Long stoppedTaskCount;
    private List<Long> stoppedTaskIds = new ArrayList<>();

    public static PondVO of(Long id,
                            String pondNo,
                            String name,
                            Long areaId,
                            String areaName,
                            String areaType,
                            BigDecimal areaMu,
                            String boardStatus,
                            BigDecimal brineDegree,
                            BigDecimal brineDepth,
                            String pondStatus,
                            Integer gridRow,
                            Integer gridCol) {
        PondVO v = new PondVO();
        v.id = id;
        v.pondNo = pondNo;
        v.name = name;
        v.areaId = areaId;
        v.areaName = areaName;
        v.areaType = areaType;
        v.areaTypeName = FieldDict.areaTypeName(areaType);
        v.areaMu = areaMu;
        v.boardStatus = boardStatus;
        v.boardStatusName = FieldDict.boardStatusName(boardStatus);
        v.brineDegree = brineDegree;
        v.brineDepth = brineDepth;
        v.pondStatus = pondStatus;
        v.pondStatusName = FieldDict.pondStatusName(pondStatus);
        v.gridRow = gridRow;
        v.gridCol = gridCol;
        v.rainStopped = false;
        v.effectiveStatus = pondStatus;
        v.stoppedTaskCount = 0L;
        v.harvestable = !"REPAIR".equals(boardStatus)
                && brineDegree != null
                && brineDegree.compareTo(FieldDict.HARVEST_MIN_BRINE) >= 0;
        v.effectiveStatusName = FieldDict.pondStatusName(v.effectiveStatus);
        return v;
    }
}
