package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 坨台出仓记录：客户、吨数、车牌
 */
@Entity
@Table(name = "outbound_record")
@Getter
@Setter
public class OutboundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "customer", length = 64)
    private String customer;

    @Column(name = "tons", precision = 8, scale = 2)
    private BigDecimal tons;

    @Column(name = "plate_no", length = 32)
    private String plateNo;

    @Column(name = "out_date")
    private LocalDate outDate;

    @Column(name = "operator", length = 32)
    private String operator;
}
