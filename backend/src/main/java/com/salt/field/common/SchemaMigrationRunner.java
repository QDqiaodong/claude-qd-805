package com.salt.field.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 兼容已有数据库：项目使用 ddl-auto=none，新增雨后停工字段时不能只改 schema.sql。
 * 启动时按 INFORMATION_SCHEMA 幂等补齐列，再创建停工闭环表。
 */
@Component
@Order(0)
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addHarvestColumnIfMissing("previous_status",
                "ALTER TABLE harvest_task ADD COLUMN previous_status VARCHAR(16) COMMENT '雨情停工前状态 PLANNED/WORKING'");
        addHarvestColumnIfMissing("stopped_at",
                "ALTER TABLE harvest_task ADD COLUMN stopped_at DATETIME COMMENT '本次雨情停工起算时刻'");
        addHarvestColumnIfMissing("rain_log_id",
                "ALTER TABLE harvest_task ADD COLUMN rain_log_id BIGINT COMMENT '当前雨情停工/窗口对应的最新降雨记录'");
        addHarvestColumnIfMissing("rain_weather_id",
                "ALTER TABLE harvest_task ADD COLUMN rain_weather_id BIGINT COMMENT '最近一次重置24小时窗口的降雨记录'");
        addHarvestColumnIfMissing("rain_amount",
                "ALTER TABLE harvest_task ADD COLUMN rain_amount DECIMAL(6,2) COMMENT '触发停工/重置窗口的降雨量 mm'");
        addHarvestColumnIfMissing("rain_resume_at",
                "ALTER TABLE harvest_task ADD COLUMN rain_resume_at DATETIME COMMENT '当前雨后窗口届满、最早可复工时刻'");
        addHarvestColumnIfMissing("resumed_at",
                "ALTER TABLE harvest_task ADD COLUMN resumed_at DATETIME COMMENT '人工确认复工时刻'");
        addIndexIfMissing("harvest_task", "idx_harvest_pond_status",
                "CREATE INDEX idx_harvest_pond_status ON harvest_task(pond_id, task_status)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS harvest_rain_stop (
                  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
                  task_id           BIGINT NOT NULL COMMENT '收盐作业单',
                  pond_id           BIGINT NOT NULL COMMENT '结晶池',
                  weather_log_id    BIGINT NOT NULL COMMENT '首次触发停工的降雨记录',
                  latest_weather_id BIGINT NOT NULL COMMENT '最新一次有效降雨记录（窗口起算点）',
                  rainfall          DECIMAL(6,2) NOT NULL COMMENT '最新一次有效降雨量 mm',
                  previous_status   VARCHAR(16) NOT NULL COMMENT '停工前作业状态',
                  stop_reason       VARCHAR(255) NOT NULL COMMENT '停工原因',
                  stopped_at        DATETIME NOT NULL COMMENT '停工起算时刻',
                  rain_at           DATETIME NOT NULL COMMENT '最新降雨时刻/窗口起算时刻',
                  earliest_resume_at DATETIME NOT NULL COMMENT '最早可复工时刻',
                  resumed_at        DATETIME COMMENT '人工确认复工时刻',
                  resume_operator   VARCHAR(32) COMMENT '复工确认人',
                  end_reason        VARCHAR(32) COMMENT '结束原因 RESUMED人工复工/CANCELED取消作业',
                  open_marker       TINYINT GENERATED ALWAYS AS (CASE WHEN resumed_at IS NULL THEN 1 ELSE NULL END) STORED COMMENT '进行中停工标记',
                  UNIQUE KEY uk_rain_stop_open_task (task_id, open_marker),
                  KEY idx_rain_stop_pond_open (pond_id, resumed_at),
                  KEY idx_rain_stop_rain (latest_weather_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收盐作业雨后停工/复工闭环记录'
                """);
    }

    private void addHarvestColumnIfMissing(String column, String ddl) {
        if (!columnExists("harvest_task", column)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addIndexIfMissing(String table, String index, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, table, index);
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
