package com.salt.field.dto;

import java.time.LocalDate;

import com.salt.field.common.FieldDict;
import com.salt.field.domain.DispatchTask;
import com.salt.field.domain.SaltTarget;

import lombok.Getter;
import lombok.Setter;

/**
 * 场务调度任务视图对象（第二个 {@code @Any} 落点的回显）
 */
@Getter
@Setter
public class DispatchTaskVO {

    private Long id;
    private String taskNo;
    private String taskType;
    private String taskTypeName;
    private String crew;
    private LocalDate planDate;
    private String taskStatus;
    private String taskStatusName;
    private String remark;

    private String targetType;
    private String targetTypeName;
    private Long targetId;
    private String targetName;

    public static DispatchTaskVO of(DispatchTask t) {
        DispatchTaskVO v = new DispatchTaskVO();
        v.id = t.getId();
        v.taskNo = t.getTaskNo();
        v.taskType = t.getTaskType();
        v.taskTypeName = FieldDict.dispatchTypeName(t.getTaskType());
        v.crew = t.getCrew();
        v.planDate = t.getPlanDate();
        v.taskStatus = t.getTaskStatus();
        v.taskStatusName = FieldDict.dispatchStatusName(t.getTaskStatus());
        v.remark = t.getRemark();
        SaltTarget target = t.getTarget();
        if (target != null) {
            v.targetType = target.targetType();
            v.targetTypeName = FieldDict.targetTypeName(v.targetType);
            v.targetId = target.getId();
            v.targetName = target.getName();
        } else {
            v.targetTypeName = "未关联";
        }
        return v;
    }
}
