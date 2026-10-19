# NeighborMarket 最终验收审计报告

> 审计日期：2026-10-19  
> 审计基准：`ce317b3`（NM-G-09 后的 `main`）  
> 本地结论：通过  
> 最终状态：等待本报告提交对应的 GitHub Actions 结果后，将 NM-G-10 从 DONE 升级为 VERIFIED

## 1. 审计范围与结论

本次审计复核安全、数据库迁移、事务与并发、前后端自动化、容器部署、重启持久性、文档一致性和 Git 状态。未修改开发/演示业务数据库，真实 MySQL 测试全部运行在 Testcontainers 或独立 Compose 卷中；未调用真实 AI 服务。

本地验收全部通过：后端 134 项、前端 19 项单元与 9 项组件、Playwright 核心 E2E 1 项均为零失败、零跳过；后端和前端构建成功；隔离 Compose 的 MySQL、后端和前端均 Healthy，重启后 E2E 创建的数据仍存在。隔离环境随后已定向删除容器、网络和卷。

当前没有未处理 P0。早期 Phase A 表格中的 DONE/IN_PROGRESS 是证据尚未建立时的状态，本次结合后续请求 ID、组件、真实部署、E2E 和全量回归证据统一回填为 VERIFIED。

## 2. 本次执行证据

| 层级 | 命令/环境 | 结果 |
| --- | --- | --- |
| 后端全量 | `cd backend; mvn "-Dmaven.repo.local=C:\Users\dell\.m2\repository" verify` | 134/134 通过；0 失败、0 错误、0 跳过；BUILD SUCCESS |
| 数据库 | Testcontainers MySQL 8.0 | Flyway V1→V8、升级/约束、并发、回滚、SQL 基线全部实际运行 |
| 后端产物 | Maven verify/repackage | 可执行 JAR 成功生成 |
| 覆盖率 | JaCoCo，82 个生产类 | 行 927/1122（82.6%）；分支 264/431（61.3%）；方法 390/456（85.5%） |
| 前端单元 | `npm test` 的 Node 层 | 19/19 通过；0 跳过 |
| 前端组件 | `npm test` 的 Vitest 层 | 9/9 通过；4 个测试文件通过 |
| 前端构建 | `npm run build` | 98 模块转换，生产构建成功 |
| Compose 配置/镜像 | 独立项目 `neighbormarket-g10-e2e` | 配置解析成功；前后端镜像构建成功 |
| 部署 smoke | `scripts/smoke-test.ps1` | 新环境 PASS；健康 UP；Session/CSRF/认证可用 |
| 浏览器 E2E | Playwright，双独立浏览器上下文 | 发布→申请→确认→完成 1/1 通过 |
| 重启持久性 | 三服务 restart 后再次 smoke | 三服务 Healthy；发布者仍有 1 件 E2E 物品 |
| 本机现有入口 | `http://127.0.0.1:8088/` 与 `/actuator/health` | 首页 200；健康 UP |
| 文档/工作树 | `git diff --check`、项目定位与内部链接检查 | 格式通过；未重新引入临时项目定位；链接存在 |
| 秘密初筛 | 跟踪文件名与私钥/API Key 特征检查 | 只发现预期的 `.env.example`；未发现私钥或形似真实 API Key |

第一次在受限环境中执行后端测试时，Docker 命名管道不可访问，构建虽成功但 30 项容器测试被跳过；该结果未用于验收。随后以宿主 Docker 权限重跑同一命令，得到上述 134 项零跳过结果。

## 3. 整体完成标准复核

| # | 完成标准 | 结论 | 证据 |
| --- | --- | --- | --- |
| 1 | 发布→申请→确认→完成→归档可运行 | 通过 | 本轮 Playwright E2E 与重启后 smoke |
| 2 | 无已知严重认证漏洞；演示凭据隔离 | 通过 | demo profile、Session fixation、CSRF、Cookie 配置及安全测试 |
| 3 | Flyway 空库和已有数据升级无损 | 通过 | MySQL V1→V8、V5→V6、V6→V7、V7→V8 集成测试 |
| 4 | 关键并发由真实 MySQL 验证 | 通过 | 确认、重复申请、取消/完成、编辑/申请竞争测试 |
| 5 | 重复请求不产生非法状态 | 通过 | 唯一约束、条件 UPDATE、影响行数与幂等测试 |
| 6 | SQL 有固定数据与执行计划证据 | 通过 | 性能基线、固定查询数与 `EXPLAIN ANALYZE` |
| 7 | 后端、前端、集成和 E2E 自动化通过 | 本地通过；远端待本提交 CI | 本报告第 2 节；Actions 结果需在推送后确认 |
| 8 | Compose 可部署且重启保留数据 | 通过 | 隔离 G10 环境构建、Healthy、重启和持久性 smoke |
| 9 | 健康、请求 ID 与安全日志可运维 | 通过 | Actuator、请求 ID 单元/安全/部署 smoke；敏感字段禁记规则 |
| 10 | README、图、迁移、API 与代码一致 | 通过 | G01～G09 文档审计、V8 ER/架构/状态图和本轮静态检查 |
| 11 | 可选技术按需求决策 | 通过 | Redis、缓存与架构边界决策；未为数量引入组件 |
| 12 | 无未处理 P0；残余风险透明 | 通过 | P0/P1 问题处置回填；残余边界见第 4 节 |

## 4. 残余边界与风险

这些项目不阻断当前本地交付，但不得被描述为已经具备正式生产能力：

1. 正式公网域名、TLS 终止、生产监控告警、容量规划和生产数据库自动备份/恢复演练由实际部署环境完成。
2. 当前为单后端实例，Session 与 AI 冷却保存在进程内；多实例前必须重新评估共享 Session 和严格跨实例配额。
3. AI 自动化只使用测试替身，本轮未读取真实密钥、未调用真实供应商，也不证明供应商实时可用或估价准确。
4. E2E 自动化覆盖最核心成功路径；权限、并发和失败路径主要由 Web、安全与真实 MySQL 集成测试承担。
5. 任意子串搜索仍会随数据线性扫描；达到性能报告中的数据量、p95 或数据库 CPU 阈值后，应先评估 MySQL ngram FULLTEXT。
6. JaCoCo 对第三方 JSqlParser 超大生成方法可能输出非阻断 instrumentation warning；项目报告和构建不受影响。
7. 本报告提交对应的远端 CI 尚需在私有仓库 Actions 页面确认；确认成功前 NM-G-10 保持 DONE。

## 5. 数据与环境保护

- Testcontainers 使用独立临时 MySQL，不读取开发数据库凭据。
- 浏览器 E2E 使用固定项目名 `neighbormarket-g10-e2e`、独立端口 18089 和独立卷。
- E2E 完成后只删除该项目的三个容器、网络、`mysql-data` 与 `image-data` 卷；`docker compose ps -a` 结果为空。
- 未运行 `scripts/demo/reset_demo_data.sql`，未修改现有业务数据库。
- 测试口令仅存在于子进程环境，未写入仓库或报告；未调用真实 AI。

## 6. 最终状态升级规则

本地审计完成后，NM-G-10 标记为 DONE。将本报告推送到 `main` 后，应在 GitHub Actions 确认该提交的后端、前端、容器镜像与核心 E2E Job 全部成功；确认后追加 CI 运行链接并将 NM-G-10 升级为 VERIFIED。若任一 Job 失败，必须按真实失败修复并重跑，不得用本地结果替代远端结论。
