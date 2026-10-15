# Phase D 工程化、测试与部署验收报告

## 结论

Phase D 已完成实现与验收：可通过 Docker Compose 启动 MySQL、Spring Boot 和 Vue/Nginx；后端、前端、组件、真实 MySQL 集成、浏览器 E2E 及部署 smoke 均有自动化证据。GitHub Actions 远端运行 `38764242106`（核心 E2E）和 `38767299683`（部署 smoke/重启持久化）均成功，因此 Phase D 可标记为 VERIFIED。

## 任务验收

| 任务 | 结果 | 主要证据 |
| --- | --- | --- |
| NM-D-01 后端多阶段镜像 | VERIFIED | 运行用户 `app:app`；健康检查通过；本地镜像 335 MB |
| NM-D-02 前端生产镜像 | VERIFIED | Nginx 非 root UID 101；SPA fallback/API 反代通过；本地镜像 77.4 MB |
| NM-D-03 Docker Compose | VERIFIED | MySQL/后端/前端健康依赖启动；数据库不暴露宿主端口；独立卷持久化通过 |
| NM-D-04 环境与密钥管理 | VERIFIED | prod/demo/test 边界明确；必需生产变量缺失时快速失败；仓库无真实密钥 |
| NM-D-05 GitHub Actions CI | VERIFIED | 工作流包含后端、前端、镜像和 E2E Job；含 smoke/重启验证的远端运行 `38767299683` 成功 |
| NM-D-06 Actuator | VERIFIED | 匿名 health/info 最小暴露；DB 健康反映到整体健康；敏感端点未暴露 |
| NM-D-07 请求 ID 与日志 | VERIFIED | `X-Request-ID` 贯穿响应、MDC 和错误体；关键状态转换有结构化字段 |
| NM-D-08 后端覆盖率 | VERIFIED | 107 项通过；JaCoCo 行 83.7%（680/812）、分支 59.9%（175/292） |
| NM-D-09 前端组件测试 | VERIFIED | 13 项 Node 单元测试 + 6 项 Vue 组件测试全部通过 |
| NM-D-10 核心 E2E | VERIFIED | Playwright 双 Session 完成登录→发布→申请→确认→完成；最终数据状态一致 |
| NM-D-11 部署 smoke | VERIFIED | 新环境、重启后 smoke 均 PASS；7 项 Flyway 迁移和已完成记录在重启后保留 |
| NM-D-12 总验收 | VERIFIED | 本地与远端 CI 证据均已归档，Phase D 完成 |

## 2026-10-16 最终回归

| 命令/检查 | 结果 |
| --- | --- |
| `cd backend; mvn --batch-mode --no-transfer-progress verify` | BUILD SUCCESS；107 测试，0 失败、0 错误、0 跳过；4 分 45 秒 |
| `cd frontend; npm test` | 13 单元 + 6 组件测试通过 |
| `cd frontend; npm run build` | Vite 生产构建成功；JS gzip 68.25 kB |
| `cd frontend; npm audit --omit=dev` | 0 个已知漏洞 |
| `npm run test:e2e` | 1 项核心浏览器闭环通过 |
| `scripts/smoke-test.ps1` | 新部署和重启后均 PASS |
| Docker 状态 | `neighbormarket-mysql-1`、`backend-1`、`frontend-1` 均 Healthy |

独立 E2E 环境在本地已验证可重复创建和定向销毁。本机热镜像下，全新数据卷启动约 61 秒，整套容器重启并等待 Healthy 约 20 秒。这些时间受 Docker Desktop 资源与本机性能影响，不作为硬性性能门禁。

## 已知边界

- GitHub Actions 已通过 API 核对：功能提交 `ace3e35` 对应的运行 `38767299683` 结论为 `success`。后续仍应保持 `main` 分支保护，防止未通过 CI 的改动合并。
- JaCoCo 在 SQL 解析时会对第三方 `JSqlParser` 的超大方法输出 instrumentation 警告，不影响项目类报告或构建成功；后续可通过 JaCoCo excludes 降低日志噪声。
- 生产 HTTPS/TLS 终止、数据库自动备份和恢复演练不在本阶段的本地完成范围内，不应声称已具备生产灾备能力。
- E2E 仅覆盖一条最核心成功路径；权限、并发和失败路径主要由 Web/安全和 MySQL 集成测试承担。

## 下一阶段入口

Phase E 应从 NM-E-01“物品编辑规则设计”开始，先固化各状态可编辑字段与并发冲突语义，再实现后端和前端。图片上传必须在存储方案和威胁模型确定后开发，不应与物品编辑第一批混合上线。
