# drama-studio — AI 短剧生成平台

ComfyUI 式可视化节点工作流，用于 AI 短剧/漫剧生产。只支持云端 API 节点（LLM/图像/视频），平台自身不跑 GPU。

## 特性

- 🎨 **可视化节点画布**：React Flow 拖拽连线，8 类短剧专用节点 + LLM 通用节点 + 素材上传节点
- 🎬 **短剧生产管线**：项目设定 → 大纲 → 角色卡 → 分集剧本 → 分镜脚本 → 角色参考图 → 分镜图 → 图生视频
- 📝 **提示词模板引擎**：鲸剧方法论内置默认值，用户可覆盖、可保存自定义模板
- 🚦 **门禁校验**：角色参考图未选定 → 分镜图禁跑；分镜图未选定 → 图生视频禁跑
- 🔐 **OAuth 登录**：走 ai-connect 第三方登录（授权码模式），不自建账号
- ☁️ **云端 API**：LLM/图像/视频全部走供应商网关（agnese / seedream / 可灵 / 即梦）

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | React 18 + TypeScript + Vite + React Flow + Ant Design 5 |
| 后端 | Spring Boot 3.2 + JDK 17 + Maven |
| 数据库 | SQLite（WAL 模式，单文件，个人使用） |
| 存储 | 阿里云 OSS（私有桶 + 预签名 URL） |
| 部署 | nginx 子目录反代（火山服务器 80 端口） |

## 快速开始

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端
cd web
npm install
npm run dev
```

## 配置

- 后端配置：`backend/src/main/resources/application.yml`（OAuth client_id/secret、OSS 凭证、供应商 Key）
- 数据库：SQLite 单文件 `backend/data/drama-studio.db`

## 文档

- `docs/PRD.md` — 产品需求文档
- `docs/技术设计.md` — 技术设计文档
