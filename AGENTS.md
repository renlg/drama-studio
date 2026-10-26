# AGENTS.md — drama-studio 实现规范

AI 短剧生成平台：ComfyUI 式可视化节点工作流，只支持云端 API 节点（LLM/图像/视频），不跑本地 GPU。一期聚焦"短剧专用节点 + 预置模板"。

## Project Layout

```
drama-studio/
├── backend/               # Spring Boot 3 后端
│   ├── pom.xml
│   └── src/main/java/com/dramastudio/
│       ├── DramaStudioApplication.java
│       ├── config/        # Security/OAuth/OSS/Flyway 配置
│       ├── controller/    # REST API
│       ├── service/       # 业务逻辑
│       ├── repository/    # Spring Data JPA
│       ├── entity/        # 实体
│       ├── dto/           # 请求/响应
│       ├── engine/        # 工作流执行引擎
│       ├── gateway/       # 供应商 API 网关（LLM/Image/Video Adapter）
│       ├── template/      # 提示词模板引擎
│       └── storage/       # OSS 存储服务
├── web/                   # React 18 前端
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── pages/         # 项目列表/画布/资产库/任务中心/模板库
│       ├── components/    # 节点面板/参数面板/资产网格
│       ├── api/           # Axios 客户端
│       └── store/         # Zustand 状态
├── docs/                  # PRD.md + 技术设计.md（只读参考）
├── AGENTS.md
└── README.md
```

## Core Requirements（编号列表，codex 按此实现）

### R1. 技术栈
- 后端：Spring Boot 3.2 + JDK 17 + Maven，Spring Data JPA + Flyway
- 数据库：**SQLite**（单文件，个人使用，WAL 模式）——不用 MySQL
- 前端：React 18 + TypeScript + Vite + React Flow 11 + Ant Design 5 + Zustand + Axios
- 构建验证：`cd backend && mvn test`；`cd web && npm run build`

### R2. 登录（ai-connect OAuth2 授权码）
- 不自建账号体系。用户点登录 → 跳转 ai-connect 授权页（`https://ai-connect.top/api/oauth/authorize?client_id=drama-studio&redirect_uri=<回调>&response_type=code&state=<随机>`）
- 回调收 code+state → POST `https://ai-connect.top/api/oauth/token`（client_id/client_secret/code/grant_type=authorization_code）换 access_token → GET `https://ai-connect.top/api/oauth/userinfo`（Bearer）拿 username
- 本地 users 表按 ai_connect_username 建映射（不存在则创建），签发本地 JWT
- 不存密码、不存 ai-connect 明文 token

### R3. 数据模型（SQLite）
- 核心表：users / projects / workflows / workflow_versions / nodes / edges / workflow_runs / node_runs / assets / asset_relations / templates / template_versions
- 支撑表：node_run_logs / run_events / provider_configs / model_configs / usage_records / audit_logs / refresh_tokens
- 字段定义见 docs/技术设计.md 第 3 章（表结构全量）

### R4. 工作流引擎
- 画布 JSON 规范化到 nodes/edges 表；发布生成 workflow_versions.snapshot_json（不可变）
- 执行：校验 DAG/门禁 → 拓扑排序 → 创建 workflow_run/node_run → 队列执行 → 进度推送（SSE）
- 节点状态：PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED/BLOCKED/STALE
- 门禁矩阵（见 PRD F-4.3）：角色参考图未 selected → 分镜图禁跑；分镜图未 selected → 图生视频禁跑

### R5. 提示词模板引擎
- 模板 JSON：schemaVersion/nodeType/sections[]/negativePrompt/modelParams/outputSchema
- 求值：`effectiveValue = userOverride ?? upstreamValue ?? defaultValue`
- 内置鲸剧模板（短剧专用节点 8 类 + LLM 通用 + 素材上传）

### R6. API 网关（供应商适配）
- LlmProviderAdapter / ImageProviderAdapter / VideoProviderAdapter，统一 validateConfig/estimateCost/submit/query/cancel/normalizeResult
- 业务层不直接依赖供应商 SDK
- 标准错误：PROVIDER_RATE_LIMITED / PROVIDER_AUTH_FAILED / CONTENT_REJECTED / PROVIDER_TIMEOUT / QUOTA_INSUFFICIENT

### R7. 存储（阿里云 OSS）
- 私有桶，对象键 `/{tenantId}/{projectId}/{assetType}/{logicalKey}/v{versionNo}/{uuid}.{ext}`
- 预签名 URL 访问（预览 30min / 视频 30min~2h / 供应商拉图 24h / 上传 5min）
- 不存大文件进 SQLite，只存 storage_key

### R8. 前端页面
- 项目列表页 / 工作流画布页（React Flow 节点+连线+参数面板）/ 资产库页 / 任务中心页 / 模板库页
- 节点状态色：等待灰/运行蓝/成功绿/失败红/被拦截黄

## Constraints
- **不用 MySQL**，SQLite 单文件（个人使用）
- 不做数据备份方案（个人使用）
- 不注册、不存密码，登录完全委托 ai-connect
- 平台不跑 GPU，所有 AI 调用走云端 API
- 提示词内置且可改：用户覆盖 > 上游注入 > 模板默认
- 每次运行冻结工作流/模板/参数/供应商快照（可复现）
- 对外协议/API 参考业界标准（OpenAI Chat Completions 风格），不自创协议
- 分逻辑提交（git commit），不 push（无 origin remote，由 Hermes 推送）
