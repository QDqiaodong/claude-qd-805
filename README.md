# 海盐盐场 · 结晶池与收盐作业管理

以「结晶池 + 卤度 + 收盐作业」为主线的盐场生产管理系统。

## 端口

| 服务 | 端口 |
| --- | --- |
| 前端 Vue 3 | 8285 |
| 后端 Spring Boot | 8385 |
| MySQL 8 | 3585 |
| Redis 7 | 6585 |

## 启动

```bash
./start.sh          # 秒起；首次或改动后 ./start.sh --build
./stop.sh           # 暂停
./resume.sh         # 恢复
./clean.sh          # 收摊（down -v，连 mysql 匿名卷一起删）
```

## 技术栈

- 后端：Spring Boot 3.3.0 / Java 17 / Spring Data JPA（Hibernate 6.5）/ MySQL 8 / Redis
- 前端：Vue 3 / Element Plus / Vite（主题色 `#1b5e20`）
- 建表：`ddl-auto: none`，新库表与种子数据来自 `mysql/schema.sql`；已有库由启动时的幂等迁移补齐雨后停工字段

## 数据访问的两条标识

1. **Hibernate 6 `@Any` 多态关联**
   - `field_log`（作业记录）与 `dispatch_task`（场务调度任务）两张表都用
     `target_type` + `target_id` 指向 `pond` / `salt_platform` / `equipment` 三种不同实体中的任意一种，
     由 `@Any + @AnyDiscriminator(STRING) + @AnyDiscriminatorValues + @AnyKeyJavaClass(Long.class)` 映射。
   - `GET /api/field-logs` 直接返回解析后的 `targetType`、`targetTypeName`、`targetId`、`targetName`，
     同一批数据里会同时出现「池子 / 坨台 / 设备」三类目标。
   - `GET /api/dispatch` 是第二个落点。
2. **手写 JPQL `@Query`**
   - `PondAreaRepository#statBrineByArea`：按池区统计卤度均值、池数、面积
   - `WeatherLogRepository#statDaily`：按日期汇总蒸发量 / 降雨量 / 平均卤度
   - `WeatherLogRepository#findRecentRain`：窗口期降雨记录（雨后禁收判据）
   - `SaltPlatformRepository#statStockAndOutbound`：坨台库存与发运汇总
   - `HarvestTaskRepository#statByTeam`：按班组汇总收盐吨数与工时

## 业务规则（service 层抛 `BizException`）

| 规则 | 触发点 | 提示 |
| --- | --- | --- |
| 卤度未达 25°Bé 不允许收盐 | `HarvestService#create/start/done` | 给出当前值与门槛 |
| 雨后 24 小时内不允许收盐 | 降雨登记、作业开始、完成、复工 | 给出降雨时刻、最早可作业/复工时刻 |
| 有效降雨立即停工同池未完作业 | `RainControlService#applyRain` | 自动写停工记录；停工中再降雨重置窗口 |
| 复工必须满 24h、卤度≥25°Bé、池板非需修 | `POST /api/harvest/{id}/resume` | 未满足时逐项列出拦截项 |
| 池板状况「需修」不允许排作业/复工 | `HarvestService#create` / `RainControlService#resume` | 给出池号 |
| 出仓量不得超过库存 | `PlatformService#outbound` | 给出库存与本次吨数 |

## 模块

1. 盐田与池区：池区类型（蒸发 / 调节 / 结晶）、面积、纳潮口、责任人
2. 池子台账：池号、面积亩、池板状况、当前卤度、卤水深度、作业状态
3. 卤度与气象记录：每日每池卤度、蒸发量、降雨量、气温、风力
4. 收盐作业与班次：作业单、班组、时长、吨数、机械/人工、坨台去向、状态
5. 坨台与盐坨库存：盐种、库存吨、垛形、含水率、出仓与客户发运

## 前端四个页面

| 路径 | 页面 | 操作范式 |
| --- | --- | --- |
| `/ponds` | 盐田平面 SVG + 卤度热力 | 平面多边形按卤度着色 + 色带图例 + 右侧抽屉登记 |
| `/harvest` | 收盐作业班次条 | 横条按时长比例绘制 + 右侧累计进度环 + 雨后停工/复工确认 + 点条改状态 |
| `/weather` | 气象蒸发量折线 | 主/次双折线 + 雨日蓝色竖标 + 点数据点看明细 |
| `/platforms` | 盐坨库存立方体卡 | CSS 3D 立方体按库存缩放 + 登记出仓 |

导航形态：左侧常驻池区树（池区 → 池号）+ 顶部可横向滚动的池号 chips 条。
