package com.salt.field.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.domain.OutboundRecord;
import com.salt.field.dto.OutboundRequest;
import com.salt.field.dto.PlatformStockStat;
import com.salt.field.dto.PlatformVO;
import com.salt.field.service.PlatformService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    /** 坨台卡片（3D 立方体） */
    @GetMapping
    public ApiResponse<List<PlatformVO>> list() {
        return ApiResponse.success(platformService.list());
    }

    /** 坨台库存与发运汇总（JPQL 聚合） */
    @GetMapping("/stock-stats")
    public ApiResponse<List<PlatformStockStat>> stockStats() {
        return ApiResponse.success(platformService.stockStats());
    }

    @GetMapping("/{id}/outbounds")
    public ApiResponse<List<OutboundRecord>> outbounds(@PathVariable Long id) {
        return ApiResponse.success(platformService.outbounds(id));
    }

    /** 登记出仓（出仓量不得超过库存） */
    @PostMapping("/{id}/outbound")
    public ApiResponse<PlatformVO> outbound(@PathVariable Long id,
                                            @Valid @RequestBody OutboundRequest request) {
        return ApiResponse.success("出仓已登记", platformService.outbound(id, request));
    }
}
