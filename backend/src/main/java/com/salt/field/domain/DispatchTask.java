package com.salt.field.domain;

import java.time.LocalDate;

import org.hibernate.annotations.Any;
import org.hibernate.annotations.AnyDiscriminator;
import org.hibernate.annotations.AnyDiscriminatorValue;
import org.hibernate.annotations.AnyDiscriminatorValues;
import org.hibernate.annotations.AnyKeyJavaClass;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 场务调度任务（清淤 / 修池 / 转卤 / 设备保养）。
 * 第二个 {@code @Any} 落点：任务对象同样可以是池子、坨台、设备中的任意一种。
 */
@Entity
@Table(name = "dispatch_task")
@Getter
@Setter
public class DispatchTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", unique = true, length = 32)
    private String taskNo;

    /** 任务类型：DREDGE 清淤 / FIX 修池 / TRANSFER 转卤 / UPKEEP 设备保养 */
    @Column(name = "task_type", length = 16)
    private String taskType;

    @Column(name = "crew", length = 32)
    private String crew;

    @Column(name = "plan_date")
    private LocalDate planDate;

    /** 状态：TODO 待派 / DOING 进行中 / DONE 已完成 */
    @Column(name = "task_status", length = 16)
    private String taskStatus;

    @Column(name = "remark", length = 128)
    private String remark;

    @Any
    @AnyDiscriminator(DiscriminatorType.STRING)
    @AnyDiscriminatorValues({
            @AnyDiscriminatorValue(discriminator = "POND", entity = Pond.class),
            @AnyDiscriminatorValue(discriminator = "PLATFORM", entity = SaltPlatform.class),
            @AnyDiscriminatorValue(discriminator = "EQUIPMENT", entity = Equipment.class)
    })
    @AnyKeyJavaClass(Long.class)
    @Column(name = "target_type", length = 16)
    @JoinColumn(name = "target_id")
    private SaltTarget target;
}
