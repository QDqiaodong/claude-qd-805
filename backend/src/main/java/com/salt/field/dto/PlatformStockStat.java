package com.salt.field.dto;

import java.math.BigDecimal;

/**
 * 坨台库存与发运汇总（JPQL 聚合投影）
 */
public record PlatformStockStat(Long platformId,
                                String platformNo,
                                String platformName,
                                String saltType,
                                BigDecimal stockTons,
                                BigDecimal capacityTons,
                                BigDecimal outTons,
                                Long outCount) {
}
