package com.salt.field.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.DispatchTaskVO;
import com.salt.field.dto.FieldLogCreateRequest;
import com.salt.field.dto.FieldLogVO;
import com.salt.field.dto.OverviewVO;
import com.salt.field.service.FieldLogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class FieldLogController {

    private final FieldLogService fieldLogService;

    public FieldLogController(FieldLogService fieldLogService) {
        this.fieldLogService = fieldLogService;
    }

    /** 作业记录：{@code @Any} 多态关联，同一批数据里 targetType 可能是 POND / PLATFORM / EQUIPMENT */
    @GetMapping("/field-logs")
    public ApiResponse<List<FieldLogVO>> logs(@RequestParam(required = false) String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return ApiResponse.success(fieldLogService.logs());
        }
        return ApiResponse.success(fieldLogService.logsByTargetType(targetType));
    }

    /** 多态目标对象候选（池子 / 坨台 / 设备） */
    @GetMapping("/field-logs/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.success(fieldLogService.targetOptions());
    }

    @PostMapping("/field-logs")
    public ApiResponse<FieldLogVO> createLog(@Valid @RequestBody FieldLogCreateRequest request) {
        return ApiResponse.success("作业记录已登记", fieldLogService.create(request));
    }

    /** 场务调度任务（第二个 @Any 落点） */
    @GetMapping("/dispatch")
    public ApiResponse<List<DispatchTaskVO>> dispatch() {
        return ApiResponse.success(fieldLogService.dispatchTasks());
    }

    @PostMapping("/dispatch")
    public ApiResponse<DispatchTaskVO> createDispatch(@RequestBody Map<String, Object> body) {
        String taskType = str(body.get("taskType"), "FIX");
        String targetType = str(body.get("targetType"), "POND");
        Long targetId = body.get("targetId") == null ? null : Long.valueOf(body.get("targetId").toString());
        String crew = str(body.get("crew"), "一班");
        LocalDate planDate = body.get("planDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("planDate").toString());
        String remark = str(body.get("remark"), "");
        return ApiResponse.success("调度任务已下达",
                fieldLogService.createDispatch(taskType, targetType, targetId, crew, planDate, remark));
    }

    @PutMapping("/dispatch/{id}/status")
    public ApiResponse<DispatchTaskVO> updateDispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success("调度任务状态已更新",
                fieldLogService.updateDispatchStatus(id, str(body.get("taskStatus"), "DONE")));
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        return ApiResponse.success(fieldLogService.overview());
    }

    @GetMapping("/equipments")
    public ApiResponse<List<com.salt.field.domain.Equipment>> equipments() {
        return ApiResponse.success(fieldLogService.equipments());
    }

    private String str(Object v, String def) {
        return v == null ? def : v.toString();
    }
}
