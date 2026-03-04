# Chat CUI Demo 启动与部署指导（Phase 8 对齐）

文档版本：`1.0.0`  
更新时间：`2026-03-04`

## 1. 适用范围

本指南覆盖两类演示：

1. 基础业务 Demo：`web-demo + skill-service + MySQL`（可交互页面）
2. Phase 8 分布式链路 Demo：通过 gateway/skill-service 测试集验证跨实例精确投递、fence、去重、恢复与可观测

说明：当前仓库没有完整的 gateway 独立运行进程（gateway 模块以库与测试为主），Phase 8 的分布式行为通过自动化测试场景复现。

## 2. 环境准备

## 2.1 必需软件

| 组件 | 推荐版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+（或使用仓库自带 `mvnw`） |
| Node.js | 20+ |
| npm | 10+ |
| MySQL | 5.7+ |

## 2.2 可选软件

| 组件 | 用途 |
|---|---|
| Redis 7+ | 本地联调 Redis route/stream 行为（测试并非强依赖） |
| Nginx | 托管 `web-demo` 静态产物 |

## 3. 首次初始化

在仓库根目录执行：

```powershell
# 1) Java 依赖预热
.\mvnw.cmd -q -DskipTests install

# 2) Web 依赖安装
cd web-demo
npm install
cd ..

# 3) Plugin 依赖安装（可选）
cd pc-agent-plugin
npm install
cd ..
```

## 4. MySQL 初始化

## 4.1 创建数据库与账号（示例）

```sql
CREATE DATABASE IF NOT EXISTS skill_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'skill_user'@'%' IDENTIFIED BY 'skill_pass';
GRANT ALL PRIVILEGES ON skill_service.* TO 'skill_user'@'%';
FLUSH PRIVILEGES;
```

## 4.2 执行迁移脚本

按顺序执行以下 SQL 文件：

1. `skill-service/src/main/resources/db/migration/V1__skill_turn_tables.sql`
2. `skill-service/src/main/resources/db/migration/V2__skill_sendback_record.sql`
3. `skill-service/src/main/resources/db/migration/V3__sendback_idempotency_guard.sql`

## 5. 启动基础 Demo

## 5.1 启动 skill-service（终端 A）

```powershell
$env:SKILL_DB_URL="jdbc:mysql://127.0.0.1:3306/skill_service?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SKILL_DB_USERNAME="skill_user"
$env:SKILL_DB_PASSWORD="skill_pass"
.\mvnw.cmd -pl skill-service spring-boot:run
```

默认监听端口：`8080`

## 5.2 启动 web-demo（终端 B）

```powershell
cd web-demo
$env:VITE_SKILL_API_BASE="http://localhost:8080"
npm run dev
```

默认访问地址：`http://localhost:5174`

## 5.3 基础冒烟验证

1. 打开页面后输入 `/`，选择 `Local OpenCode`。
2. 提交问题，看到状态卡进入 `running`。
3. 展开会话历史，确认消息逐步刷新。
4. 选择片段执行 sendback，确认成功提示与消息 ID。

## 6. Phase 8 分布式链路验证（推荐）

在仓库根目录执行以下命令：

```powershell
# 路由与 CAS
.\mvnw.cmd -pl gateway "-Dtest=RouteKeyFactoryTest,RedisRouteOwnershipStoreTest" test

# 跨实例 relay 与去重
.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=BridgePersistencePublisherTest,CrossInstanceRelayIntegrationTest" test

# ACK、恢复、fence
.\mvnw.cmd -pl "gateway,skill-service" "-Dtest=DeliveryAckStateMachineTest,DeliveryRetryQueueTest,ResumeCoordinatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

通过标准：

- 所有测试 `BUILD SUCCESS`
- 覆盖 `OWNER_FENCED`、`ROUTE_REPLAY_WINDOW_EXPIRED`、`gateway_owner_accepted`、`client_delivered`、`client_delivery_timeout` 分支

## 7. 打包与部署（Demo 环境）

## 7.1 skill-service 打包运行

```powershell
.\mvnw.cmd -pl skill-service clean package -DskipTests
java -jar skill-service/target/skill-service-0.1.0-SNAPSHOT.jar
```

部署环境变量：

- `SKILL_DB_URL`
- `SKILL_DB_USERNAME`
- `SKILL_DB_PASSWORD`
- `SKILL_SENDBACK_IDEMPOTENCY_HASH_ALGORITHM`（可选，默认 `SHA-256`）

## 7.2 web-demo 打包发布

```powershell
cd web-demo
$env:VITE_SKILL_API_BASE="https://your-skill-service.example.com"
npm run build
```

产物目录：`web-demo/dist`

可通过 Nginx 托管静态资源，并反向代理到 skill-service。

## 8. 生产化最小配置建议

1. skill-service 进程配置：`-Xms512m -Xmx1024m` 起步。
2. MySQL 连接池与慢查询日志开启。
3. 指标采集接入 Prometheus/Grafana。
4. 日志集中化并保留 `trace_id` 检索能力。

## 9. 常见问题与排查

| 现象 | 可能原因 | 处理方式 |
|---|---|---|
| web-demo 调用 404/500 | `VITE_SKILL_API_BASE` 未设置或后端未启动 | 校验 `VITE_SKILL_API_BASE` 与 8080 服务可达 |
| skill-service 启动失败 | MySQL 连接或表缺失 | 检查 `SKILL_DB_*` 环境变量与迁移脚本执行情况 |
| sendback 重复失败 | 幂等键冲突或 trace 参数异常 | 检查请求中的 `turn_id/trace_id/selected_text` |
| Phase 8 测试失败 | 本地代码与依赖状态不一致 | 先执行 `.\mvnw.cmd -q -DskipTests install` 后重跑 |

## 10. 回滚策略（Demo）

1. 若 Phase 8 测试回归失败，先回滚到上一通过 commit。
2. skill-service 可通过旧 jar 快速回滚。
3. web-demo 静态资源可通过上一版本 dist 快速切换。

