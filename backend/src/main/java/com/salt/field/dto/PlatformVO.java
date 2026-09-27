package com.salt.field.dto;

import java.math.BigDecimal;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 坨台视图对象（3D 立方体卡片用）
 */
@Getter
@Setter
public class PlatformVO {

    private Long id;
    private String platformNo;
    private String name;
    private String saltType;
    private String saltTypeName;
    private BigDecimal stockTons;
    private BigDecimal capacityTons;
    private BigDecimal moisture;
    private String pileShape;
    private String location;
    private BigDecimal outTons;
    /** 库存占比（0~1），立方体体积按它缩放 */
    private BigDecimal fillRatio;

    public static PlatformVO of(Long id,
                                String platformNo,
                                String name,
                                String saltType,
                                BigDecimal stockTons,
                                BigDecimal capacityTons,
                                BigDecimal moisture,
                                String pileShape,
                                String location,
                                BigDecimal outTons) {
        PlatformVO v = new PlatformVO();
        v.id = id;
        v.platformNo = platformNo;
        v.name = name;
        v.saltType = saltType;
        v.saltTypeName = FieldDict.saltTypeName(saltType);
        v.stockTons = stockTons;
        v.capacityTons = capacityTons;
        v.moisture = moisture;
        v.pileShape = pileShape;
        v.location = location;
        v.outTons = outTons == null ? BigDecimal.ZERO : outTons;
        BigDecimal cap = capacityTons == null || capacityTons.signum() <= 0 ? BigDecimal.ONE : capacityTons;
        BigDecimal stock = stockTons == null ? BigDecimal.ZERO : stockTons;
        v.fillRatio = stock.divide(cap, 4, java.math.RoundingMode.HALF_UP);
        return v;
    }
}
