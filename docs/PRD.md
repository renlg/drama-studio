# AI 短剧生成平台 PRD

> 文档版本：v1.0  
> 文档状态：研发评审稿  
> 更新日期：2026-10-26  
> 产品代号：Drama Studio  
> 技术基线：React + React Flow + Spring Boot 3 / JDK 17；全部生成能力调用云端 API，平台不运行本地 GPU

## 0. 文档约定与范围

### 0.1 一期范围

一期交付“从项目设定到单镜头视频”的可运行闭环：可视化编排、8 种短剧节点、2 种通用节点、鲸剧模板、人工阶段确认、多版本选择、异步执行、资产管理和基础用户权限。完整成片的自动剪辑、配音、字幕混流、模板交易市场和团队协作不纳入一期。

### 0.2 术语

| 术语 | 定义 |
|---|---|
| 工作流（Workflow） | 用户编辑的节点与边的持久化定义，不代表某次具体执行 |
| 工作流版本 | 每次发布/运行时冻结的工作流快照；历史运行永远引用快照 |
| 节点运行（Node Run） | 某次工作流运行中某个节点的一次执行实例 |
| 资产（Asset） | 文本、JSON、图片、视频、上传素材等可版本化产物 |
| 逻辑资产 | 如“角色-林晚”或“镜头 EP01-S003”，可拥有多个资产版本 |
| selected | 多版本中被用户选为下游唯一输入的版本 |
| approved | 内容已通过人工审核，可用于后续文本生产 |
| locked | 已锁定；不得直接覆盖，只能派生新版本 |
| 模板 | 节点提示词结构、变量、默认参数与输出 Schema 的版本化定义 |

### 0.3 产品硬约束

1. 所有 LLM、图像、视频生成均通过云端 API；不得依赖用户本地 ComfyUI 或本地模型。
2. 鲸剧方法论的关键阶段默认启用人工门禁，不允许静默跳过。
3. 角色参考图未 `selected`，分镜图不得运行；分镜图未 `selected`，图生视频不得运行。
4. 用户编辑值的注入优先级固定为：**用户覆盖值 > 上游注入值 > 模板默认值**。
5. 每次运行冻结工作流、模板版本、参数、输入资产版本和供应商配置快照，以支持审计与复跑。

---

## 1. 产品概述

### 1.1 产品定位

Drama Studio 是面向 AI 短剧创作者的云端生产线。用户通过 React Flow 画布像搭积木一样编排“项目设定 → 大纲 → 角色 → 剧本 → 分镜 → 图片 → 视频”，并在每个关键节点审阅、修改、选择和锁定结果。平台将鲸剧方法论固化为可编辑提示词模板，把隐性的创作经验变成可复用、可追踪、可批量执行的生产流程。

### 1.2 目标用户

| 用户 | 典型目标 | 主要痛点 |
|---|---|---|
| 普通创作者 | 从创意快速得到可看的短剧镜头 | 不懂提示词、流程复杂、人物易漂移 |
| 专业创作者/工作室 | 稳定批量生产、复用成熟管线 | 工具割裂、版本混乱、人工交接成本高 |
| 平台管理员 | 控制供应商、成本、模板和风险 | API 差异、配额与内容安全不可控 |

### 1.3 核心价值主张

- **方法论产品化**：鲸剧 10 阶段固化为语义化节点、阶段门禁和输出契约。
- **可控而非黑盒**：提示词逐段可改，输出逐阶段确认，生成版本可选择和锁定。
- **人物一致性**：角色锁定词和选定参考图自动注入所有下游图像/视频节点。
- **供应商可替换**：统一 API 网关屏蔽模型、鉴权、轮询、错误码和计费差异。
- **生产可恢复**：DAG 异步执行、失败重试、断点续跑，长耗时任务不阻塞浏览器。

### 1.4 与 LiblibAI / ComfyUI 的差异

| 维度 | Drama Studio | LiblibAI | ComfyUI |
|---|---|---|---|
| 核心对象 | 短剧项目、角色、集、镜头、版本 | 模型/图片创作 | 通用生成图工作流 |
| 节点语义 | 短剧专用业务节点 | 平台预设能力为主 | 底层模型/算子节点 |
| 方法论 | 内置鲸剧模板并允许逐段编辑 | 非核心 | 依赖用户自行搭建 |
| 阶段门禁 | approved / selected / locked 强约束 | 弱 | 无短剧业务门禁 |
| 算力 | 全云端 API | 云端 | 常见为本地或自管 GPU |
| 多模态链路 | 剧本到视频的统一资产血缘 | 偏生成内容平台 | 偏底层执行图 |
| 目标用户门槛 | 普通创作者可用，专业用户可深调 | 中低 | 较高 |

### 1.5 商业模式

| 层级 | 建议权益 |
|---|---|
| 免费版 | 3 个项目、有限文本额度、图片/视频试用额度、内置模板、带水印预览 |
| 会员版 | 更多项目与存储、较高并发、无水印、我的模板、高清输出、优先队列 |
| 算力包 | 按供应商实际消耗折算统一点数；图像/视频按次或按秒计费 |
| API 输出 | 面向工作室提供工作流运行 API、Webhook 和资产导出；按调用量计费 |

一期只记录 `usage_amount`、`estimated_cost` 与配额扣减流水，不实现支付闭环；后台可人工充值额度。

---

## 2. 用户角色与核心使用流程

### 2.1 角色权限

| 能力 | 普通创作者 | 专业创作者 | 管理员 |
|---|---:|---:|---:|
| 创建/编辑自己的项目与工作流 | ✓ | ✓ | ✓ |
| 使用内置模板 | ✓ | ✓ | ✓ |
| 保存“我的模板” | 限量 | ✓ | ✓ |
| 批量运行/更高并发 | — | ✓ | ✓ |
| 查看自己的用量与任务 | ✓ | ✓ | ✓ |
| 管理共享模板 | — | 申请发布（二期） | ✓ |
| 管理供应商、系统额度、用户 | — | — | ✓ |
| 查看他人项目 | — | — | 仅审计授权下可见 |

### 2.2 主用户故事

1. 作为创作者，我创建项目，填写故事环境、类型、剧型、画风、集数和单集时长。
2. 我选择“标准短剧管线”，系统一键创建 8 类节点和合法连线。
3. 我打开项目设定节点，看到鲸剧默认提示词，可修改字段或模板段落后运行。
4. 系统生成结构化结果并标为 `draft`；我预览并选择“通过”“修改”或“重写”。
5. 通过后，下游大纲节点解除拦截；大纲、角色卡、分集剧本、分镜脚本依次生成并确认。
6. 每个角色卡生成多个参考图版本；我选中一个版本，必要时锁定。
7. 分镜图节点自动引用角色锁定词、选定参考图和分镜卡，生成多个版本；我逐镜选择。
8. 图生视频节点只接受已选分镜图，生成视频版本；我选择成片镜头并在资产库下载。
9. 任一阶段修改后，系统标记受影响的下游结果为 `stale`，我可从变更节点断点重跑。

### 2.3 标准流程与异常分支

```text
创建项目 → 套用工作流 → 补齐参数 → 校验 DAG → 运行节点
  → draft 结果 → 人工通过 approved
  → 图片/视频多版本 → select → 可选 lock
  → 下游运行 → 资产库预览/下载

失败：自动重试 → 仍失败则 failed → 用户从失败节点重跑
修改已通过上游：生成新版本 → 下游标 stale → 用户确认影响范围 → 断点续跑
```

---

## 3. 功能需求明细

## F-1 工作流画布

### F-1.1 画布能力

| 编号 | 需求 | 规则/验收 |
|---|---|---|
| F-1.1 | 节点拖拽 | 从左侧节点库拖入画布；生成 UUID；默认参数来自指定模板版本 |
| F-1.2 | 端口连线 | 仅允许输出到输入；实时校验数据类型、基数和是否成环；非法连接不落库 |
| F-1.3 | 缩放/平移 | 10%–200%，支持触控板、滚轮、适配视图、小地图 |
| F-1.4 | 多选与复制 | 框选、Shift 多选、复制粘贴；复制节点生成新 ID，内部边同步复制 |
| F-1.5 | 撤销/重做 | 至少保存最近 100 个前端编辑命令；保存成功后仍可在当前会话撤销 |
| F-1.6 | 自动布局 | 按 DAG 层级从左到右布局；同层按创建顺序；不改变业务连接 |
| F-1.7 | 节点搜索 | 按名称、类型、能力标签搜索；支持快捷键 `/` 打开 |
| F-1.8 | 子流程分组 | 框选节点创建 Group；可折叠；一期分组仅影响展示和批量操作，不改变 DAG 语义 |
| F-1.9 | 保存 | 自动保存防抖 2 秒，显式保存立即提交；使用 `revision` 乐观锁冲突提示 |
| F-1.10 | 校验 | 运行前检查环、必填端口、参数、模板、门禁、供应商和额度，返回定位到节点的错误列表 |

### F-1.2 节点展示规范

节点卡片显示：图标、名称、类型、当前资产状态、最近运行状态、进度、供应商/模型摘要、输入输出端口。状态颜色：等待灰、运行蓝、成功绿、失败红、被拦截黄、已过期橙。点击选中打开右侧参数面板；双击打开结果详情。

## F-2 节点系统

### F-2.1 通用节点协议

所有节点包含：`id`、`type`、`name`、`templateVersionId`、`config`、`position`、`inputPorts[]`、`outputPorts[]`。端口数据类型如下：

| 类型 | 含义 |
|---|---|
| `PROJECT_STATE` | 项目设定 JSON |
| `OUTLINE` | 剧本大纲 JSON |
| `CHARACTER_CARD_LIST` | 角色卡数组及锁定词 |
| `EPISODE_SCRIPT` | 单集剧本 Markdown + 结构化元数据 |
| `SHOT_CARD_LIST` | 分镜卡数组 |
| `IMAGE_ASSET_LIST` | 图片资产版本列表 |
| `VIDEO_ASSET_LIST` | 视频资产版本列表 |
| `TEXT` / `JSON` | 通用文本/JSON |
| `ASSET_REF_LIST` | 上传或资产库引用列表 |

端口支持 `required`、`multiple`、`acceptedTypes`。同一输入端口若 `multiple=false` 只能有一条入边；多角色、多参考图端口必须显式为数组。

### F-2.2 通用参数

以下字段存在于全部 AI 节点，业务节点表不重复列出：

| 字段 | 类型 | 默认值 | 可编辑 | 来源 | 说明 |
|---|---|---:|---:|---|---|
| `provider` | enum | `AUTO` | 是 | 模板默认/用户自定义 | AUTO 按路由策略选择 |
| `model` | string | 模板指定 | 是 | 模板默认/用户自定义 | 仅展示该能力可用模型 |
| `template_version_id` | UUID | 最新稳定版 | 是 | 模板默认/用户自定义 | 运行时冻结 |
| `temperature` | decimal | `0.7`（LLM） | 是 | 模板默认/用户自定义 | 图像/视频节点不显示 |
| `max_tokens` | int | `8192`（LLM） | 是 | 模板默认/用户自定义 | 供应商上限内校验 |
| `timeout_sec` | int | LLM 120/图像 300/视频 1800 | 是 | 模板默认/用户自定义 | 30–3600 |
| `retry_count` | int | `3` | 是 | 模板默认/用户自定义 | 0–5 |
| `output_count` | int | 文本 1/图片 2/视频 1 | 是 | 模板默认/用户自定义 | 受套餐与供应商限制 |

### F-2.3 项目设定节点 `PROJECT_SETUP`

**定义**：把用户创意整理成标准 `project-state`。输入 `idea:TEXT`（可选）；输出 `project_state:PROJECT_STATE`（单值）。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `title` | string | 空 | 是 | 用户自定义 |
| `story_environment` | textarea | 空 | 是 | 上游注入/用户自定义 |
| `genre` | string[] | `[]` | 是 | 上游注入/用户自定义 |
| `drama_type` | enum | `动态漫` | 是 | 模板默认/用户自定义 |
| `art_style` | string | `半写实国漫` | 是 | 模板默认/用户自定义 |
| `panel_style` | string | `电影横屏` | 是 | 模板默认/用户自定义 |
| `voice_mode` | enum | `对白+旁白` | 是 | 模板默认/用户自定义 |
| `episode_count` | int | `10` | 是 | 模板默认/用户自定义 |
| `episode_duration_sec` | int | `90` | 是 | 模板默认/用户自定义 |
| `target_audience` | string | `18-35岁短剧用户` | 是 | 模板默认/用户自定义 |
| `core_payoff` | textarea | 空 | 是 | 上游注入/用户自定义 |
| `emotional_tone` | string | 空 | 是 | 上游注入/用户自定义 |
| `idea` | textarea | 空 | 是 | 上游注入/用户自定义 |

### F-2.4 剧本大纲节点 `STORY_OUTLINE`

**定义**：生成世界观、人物关系、三幕结构、分集大纲、爽点、钩子和出场时间线。输入 `project_state:PROJECT_STATE`（必填，approved）；输出 `outline:OUTLINE`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `project_state` | assetRef | 空 | 否 | 上游注入 |
| `structure` | enum | `三幕结构` | 是 | 模板默认/用户自定义 |
| `episode_count` | int | 项目集数 | 是 | 上游注入/用户自定义 |
| `payoff_density` | enum | `每集1-2个` | 是 | 模板默认/用户自定义 |
| `hook_style` | string | `强悬念/反转` | 是 | 模板默认/用户自定义 |
| `character_limit` | int | `8` | 是 | 模板默认/用户自定义 |
| `extra_constraints` | textarea | 空 | 是 | 用户自定义 |

### F-2.5 角色卡节点 `CHARACTER_CARD`

**定义**：为主要角色生成可复用角色卡、锁定词和参考图提示词。输入 `project_state:PROJECT_STATE`、`outline:OUTLINE`（均必填且 approved）；输出 `characters:CHARACTER_CARD_LIST`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `project_state` | assetRef | 空 | 否 | 上游注入 |
| `outline` | assetRef | 空 | 否 | 上游注入 |
| `character_scope` | enum | `主要角色` | 是 | 模板默认/用户自定义 |
| `character_names` | string[] | 大纲角色 | 是 | 上游注入/用户自定义 |
| `art_style` | string | 项目画风 | 是 | 上游注入/用户自定义 |
| `reference_background` | string | `透明或纯白背景` | 是 | 模板默认/用户自定义 |
| `reference_views` | string[] | `正面特写,正面全身,侧面,背面` | 是 | 模板默认/用户自定义 |
| `prop_policy` | enum | `禁止道具` | 是 | 模板默认/用户自定义 |
| `forbidden_changes` | string[] | `脸型,发型/发色,服饰细节,体型,年龄感,画风` | 是 | 模板默认/用户自定义 |
| `extra_constraints` | textarea | 空 | 是 | 用户自定义 |

### F-2.6 分集剧本节点 `EPISODE_SCRIPT`

**定义**：按指定集生成剧本正文。输入 `outline:OUTLINE`、`characters:CHARACTER_CARD_LIST`（approved）；输出 `episode_script:EPISODE_SCRIPT`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `outline` | assetRef | 空 | 否 | 上游注入 |
| `characters` | assetRef[] | 空 | 否 | 上游注入 |
| `episode_number` | int | `1` | 是 | 用户自定义 |
| `episode_title` | string | 大纲对应标题 | 是 | 上游注入/用户自定义 |
| `drama_type` | enum | 项目剧型 | 是 | 上游注入/用户自定义 |
| `target_duration_sec` | int | 项目单集时长 | 是 | 上游注入/用户自定义 |
| `dialogue_ratio` | decimal | `0.55` | 是 | 模板默认/用户自定义 |
| `target_payoff` | textarea | 大纲本集爽点 | 是 | 上游注入/用户自定义 |
| `ending_hook` | textarea | 大纲本集钩子 | 是 | 上游注入/用户自定义 |
| `extra_constraints` | textarea | 空 | 是 | 用户自定义 |

### F-2.7 分镜脚本节点 `STORYBOARD_SCRIPT`

**定义**：把单集剧本拆为镜头卡。输入 `episode_script:EPISODE_SCRIPT`、`characters:CHARACTER_CARD_LIST`（approved）；输出 `shots:SHOT_CARD_LIST`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `episode_script` | assetRef | 空 | 否 | 上游注入 |
| `characters` | assetRef[] | 空 | 否 | 上游注入 |
| `episode_id` | string | 上游集 ID | 否 | 上游注入 |
| `target_shot_duration_sec` | range | `3-6` | 是 | 模板默认/用户自定义 |
| `shot_count_hint` | int | 按总时长/4.5 计算 | 是 | 上游注入/用户自定义 |
| `camera_language` | enum | 按剧型自动 | 是 | 模板默认/用户自定义 |
| `include_dialogue_caption` | boolean | `true` | 是 | 模板默认/用户自定义 |
| `include_comic_effects` | boolean | 动态漫为 `true` | 是 | 上游注入/用户自定义 |
| `continuity_constraints` | textarea | 空 | 是 | 用户自定义 |

### F-2.8 角色参考图节点 `CHARACTER_REFERENCE_IMAGE`

**定义**：依据角色锁定词生成干净、多视角角色设定图。输入 `character:CHARACTER_CARD_LIST` 中单角色（approved）；输出 `images:IMAGE_ASSET_LIST`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `character_id` | string | 空 | 是 | 上游注入/用户自定义 |
| `lock_prompt` | textarea | 角色卡锁定词 | 是 | 上游注入/用户自定义 |
| `background` | string | `纯白背景，干净统一底色` | 是 | 模板默认/用户自定义 |
| `subject_rule` | string | `单人，不拿道具，无复杂场景` | 是 | 模板默认/用户自定义 |
| `views` | string[] | `正面特写,正面全身,侧面,背面` | 是 | 模板默认/用户自定义 |
| `art_style` | string | 项目画风 | 是 | 上游注入/用户自定义 |
| `negative_prompt` | textarea | `different face...extra people` | 是 | 模板默认/用户自定义 |
| `width` | int | `1536` | 是 | 模板默认/用户自定义 |
| `height` | int | `1024` | 是 | 模板默认/用户自定义 |
| `seed` | long/null | `null`（随机） | 是 | 模板默认/用户自定义 |
| `output_count` | int | `2` | 是 | 模板默认/用户自定义 |

### F-2.9 分镜图节点 `STORYBOARD_IMAGE`

**定义**：用 7 段式提示词和角色参考图生成镜头图片。输入 `shots:SHOT_CARD_LIST`（approved）、`character_refs:IMAGE_ASSET_LIST`（selected）、`scene_assets:ASSET_REF_LIST`（可选）；输出 `images:IMAGE_ASSET_LIST`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `shot_id` | string | 空 | 是 | 上游注入/用户自定义 |
| `character_lock` | textarea | 角色卡锁定词 | 是 | 上游注入/用户自定义 |
| `reference_images` | assetRef[] | 已选角色图 | 是 | 上游注入/用户自定义 |
| `scene_description` | textarea | 分镜场景 | 是 | 上游注入/用户自定义 |
| `composition` | textarea | 分镜景别+构图 | 是 | 上游注入/用户自定义 |
| `emotion` | string | 分镜情绪/表情 | 是 | 上游注入/用户自定义 |
| `action` | textarea | 分镜动作 | 是 | 上游注入/用户自定义 |
| `art_style` | string | 项目画风 | 是 | 上游注入/用户自定义 |
| `quality_prompt` | string | `high quality illustration` | 是 | 模板默认/用户自定义 |
| `negative_prompt` | textarea | `different face...unwanted props` | 是 | 模板默认/用户自定义 |
| `denoise` | decimal | `0.35` | 是 | 模板默认/用户自定义 |
| `width` | int | `1024` | 是 | 模板默认/用户自定义 |
| `height` | int | `1536` | 是 | 模板默认/用户自定义 |
| `seed` | long/null | `null` | 是 | 模板默认/用户自定义 |
| `output_count` | int | `2` | 是 | 模板默认/用户自定义 |

### F-2.10 图生视频节点 `IMAGE_TO_VIDEO`

**定义**：基于已选分镜图生成镜头视频。输入 `source_image:IMAGE_ASSET_LIST` 中单个 selected 版本、`shot:SHOT_CARD_LIST`；输出 `videos:VIDEO_ASSET_LIST`。

| 参数字段 | 类型 | 默认值 | 可编辑 | 来源 |
|---|---|---|---:|---|
| `shot_id` | string | 上游镜头 ID | 否 | 上游注入 |
| `source_image` | assetRef | 已选分镜图 | 是 | 上游注入/用户自定义 |
| `duration_sec` | decimal | 分镜时长 | 是 | 上游注入/用户自定义 |
| `start_frame_description` | textarea | 分镜画面说明 | 是 | 上游注入/用户自定义 |
| `motion_description` | textarea | 分镜动作 | 是 | 上游注入/用户自定义 |
| `camera_movement` | string | `轻微推进` | 是 | 模板默认/用户自定义 |
| `expression_change` | string | `保持自然且连续` | 是 | 模板默认/用户自定义 |
| `environment_motion` | string | `轻微环境动态` | 是 | 模板默认/用户自定义 |
| `pace` | enum | `中等` | 是 | 模板默认/用户自定义 |
| `audio_hint` | textarea | 空 | 是 | 上游注入/用户自定义 |
| `consistency_constraints` | textarea | `保持脸部、发型、服饰、体型和画风一致` | 是 | 模板默认/用户自定义 |
| `negative_prompt` | textarea | `changed face...unstable identity` | 是 | 模板默认/用户自定义 |
| `resolution` | enum | `480P` | 是 | 模板默认/用户自定义 |
| `output_count` | int | `1` | 是 | 模板默认/用户自定义 |

### F-2.11 LLM 通用节点 `GENERIC_LLM`

输入 `text:TEXT|JSON|任意业务类型`（可多选）；输出 `result:TEXT|JSON`。参数：`system_prompt`(textarea, 空, 可编辑, 用户自定义)、`user_prompt`(textarea, 空, 可编辑, 用户自定义)、`variables`(key-value, `{}`, 可编辑, 上游注入/用户自定义)、`output_format`(enum, `TEXT`, 可编辑, 模板默认)、`json_schema`(JSON, 空, 可编辑, 用户自定义)，以及 LLM 通用参数。默认不参与鲸剧业务门禁；若连接至强类型端口，必须配置 JSON Schema 且通过类型适配校验。输出 `GENERIC_TEXT/JSON`，`draft → approved`。

### F-2.12 素材上传节点 `ASSET_INPUT`

无输入；输出 `assets:ASSET_REF_LIST`。参数：`asset_category`(enum, `OTHER`, 可编辑, 用户自定义)、`files`(assetRef[], 空, 可编辑, 用户自定义)、`required_status`(enum, `selected`, 可编辑, 模板默认)、`description`(textarea, 空, 可编辑, 用户自定义)。支持图片/视频/文本，单文件上限由系统配置；上传完成后病毒扫描、提取元数据并生成缩略图。该节点不调用 AI。

## F-3 提示词模板引擎

### F-3.3 模板管理

- 内置模板：管理员发布，普通用户只读；升级不改变旧工作流绑定版本。
- 我的模板：用户从内置模板派生或新建；仅本人可见，可复制、归档、恢复默认。
- 共享模板：数据结构一期预留，市场与审核流程二期开放。
- 版本：语义化版本号；已发布版本不可原地修改，只能创建新版本；草稿可修改。
- 工作流可选择“固定版本”或“跟随最新稳定版”；真正执行时一律解析并冻结具体版本。

## F-4 状态机与运行拦截

### F-4.1 内容状态

完整状态集合为 `draft / revision / approved / rejected / selected / locked`。其中 `selected` 仅用于有多版本选择的逻辑资产；文本资产通常走 `draft → approved → locked`。

| 当前状态 | 操作 | 下一状态 | 约束 |
|---|---|---|---|
| draft | approve | approved | 有审批权限 |
| draft | request_revision | revision | 必填修改意见 |
| draft/revision | reject | rejected | 不供下游使用 |
| revision | regenerate/save | draft | 产生新版本，不覆盖原版本 |
| approved | revise | revision | 下游标记 stale |
| draft/approved | select | selected | 同一逻辑资产仅一个 selected；事务切换 |
| selected/approved | lock | locked | 锁定具体版本 |
| locked | unlock | selected/approved | 一期仅资产所有者；记录审计；下游标 stale |

### F-4.3 门禁矩阵

| 下游节点 | 前置条件 |
|---|---|
| 大纲 | 项目设定 approved/locked |
| 角色卡 | 项目设定和大纲 approved/locked |
| 分集剧本 | 大纲、所引用角色卡 approved/locked |
| 分镜脚本 | 分集剧本和角色卡 approved/locked |
| 角色参考图 | 角色卡 approved/locked |
| 分镜图 | 分镜脚本 approved/locked；所需角色参考图 selected/locked |
| 图生视频提示词审批 | 分镜图 selected/locked |
| 图生视频生成 | 源图 selected/locked；提示词 approved/locked |

通用 LLM 和素材上传不强制业务门禁，但仍执行端口与权限校验。拦截结果返回 `code`、`nodeId`、`missingRequirements[]`、`actionUrl`。

### F-4.4 多版本选择与失效传播

`select` 在数据库事务内取消原 selected 版本并选中新版本，随后沿工作流边计算影响范围，把引用旧版本的下游成功结果标记 `STALE`，但不删除资产。用户可选择：保持旧结果、从当前节点重跑、重跑所有受影响节点。锁定资产禁止覆盖或删除。

## F-5 资产管理

### F-5.1 分类与能力

分类包括：角色卡、角色参考图、场景、道具、分集剧本、分镜卡、分镜图、镜头视频、成片、上传素材。支持按项目/集/镜头/角色/类型/状态/时间筛选，网格与列表视图，预览、下载、复制引用、版本对比、查看血缘和删除。

### F-5.2 版本历史

同一 `logical_key` 下按 `version_no` 递增。每版记录父版本、输入资产、节点运行、模板版本、提示词、供应商、模型、seed、尺寸、时长、哈希和成本。删除采用软删除；被运行引用、selected 或 locked 的资产不可直接删除。

## F-6 项目与模板工作流

项目支持创建、编辑、归档、复制、删除、按更新时间搜索。创建必填：名称、故事环境、类型；可选：剧型、画风、集数、时长。

预置“标准短剧管线”包含：项目设定 → 大纲 → 角色卡；角色卡分支到角色参考图；大纲+角色卡 → 分集剧本 → 分镜脚本；分镜脚本+角色参考图 → 分镜图 → 图生视频。实例化时创建独立工作流草稿并绑定稳定模板版本。

## F-7 用户与权限

**一期登录走 ai-connect 的 OAuth2 授权码（Authorization Code）流程**，drama-studio 作为 OAuth 客户端。不自建账号体系、不存密码、不保存 ai-connect 明文 token。

### F-7.1 前置条件（ai-connect 侧注册 OAuth Client）

在 ai-connect 注册 drama-studio 为 OAuth 客户端（参照现有 `taiwei` 客户端，由 `OAuthClientInitializer` 启动时初始化）：

| 参数 | 值 |
|---|---|
| `client_id` | `drama-studio` |
| `client_secret` | 随机生成（`openssl rand -hex 32`），环境变量注入 |
| `redirect_uri` | drama-studio 的回调地址（如 `https://drama-studio.top/api/auth/callback`） |

### F-7.2 登录流程（授权码模式）

```text
1. 用户点击「登录」→ drama-studio 前端跳转 ai-connect 授权页：
   GET https://ai-connect.top/api/oauth/authorize
      ?client_id=drama-studio&redirect_uri=<回调>&response_type=code&state=<随机>

2. 用户在 ai-connect 登录（若已登录 ai-connect 则免登）并授权
   → ai-connect 302 回调到 drama-studio 回调地址，带 ?code=<授权码>&state=<随机>

3. drama-studio 后端校验 state 后，用 code 换 token：
   POST https://ai-connect.top/api/oauth/token
   Content-Type: application/x-www-form-urlencoded
   client_id=drama-studio&client_secret=<secret>&code=<授权码>&grant_type=authorization_code

   响应：{ "access_token": "...", "token_type": "bearer", "expires_in": N, "username": "..." }

4. drama-studio 用 access_token 调 userinfo 换取用户身份：
   GET https://ai-connect.top/api/oauth/userinfo
   Authorization: Bearer <access_token>
   响应：{ "username": "..." }

5. drama-studio 本地 users 表按 username（ai-connect 用户）建映射（不存在则创建）
   → 自己签发 JWT 作为本地会话凭证，ai-connect access_token 短期内存缓存，过期即弃
```

### F-7.3 接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/auth/login` | 302 跳转 ai-connect 授权页（带 state，防 CSRF） |
| GET | `/auth/callback` | 接收 code + state → 换 token → 换身份 → 建本地用户 → 发本地 JWT → 重定向前端 |
| POST | `/auth/logout` | 注销本地会话 |
| GET | `/me` | 返回当前用户信息（来自 ai-connect 身份 + 本地角色） |
| GET/PATCH | `/admin/users/{id}` | 管理员查看/调整本地用户（额度、角色、禁用） |

> 不提供 `/auth/register`、`/auth/refresh`（注册与账号管理全在 ai-connect 侧）。

## F-8 运行状态可视化

- 节点角标实时显示等待、排队、运行中、成功、失败、被拦截、已取消、已过期。
- 运行中显示 0–100% 或“不确定进度 + 已耗时”；视频轮询显示供应商阶段。
- 节点日志分“用户日志”和“调试日志”；用户日志不得含 Key、完整签名 URL、供应商原始敏感响应。
- 结果预览支持 Markdown、格式化 JSON、图片对比选择、视频播放。
- 失败卡片显示可理解原因、错误码、重试按钮和配置定位；被拦截显示缺失审批/选择项并提供跳转。

## 4. 页面与交互设计说明

### 4.1 项目列表页 `/projects`

卡片显示封面、名称、类型、进度、最近运行状态、更新时间。支持搜索、筛选、创建、复制、归档和删除。创建向导分“基础设定 → 选择预置工作流 → 确认”，创建后进入画布并自动定位项目设定节点。

### 4.2 工作流画布页 `/projects/{projectId}/workflows/{workflowId}`

布局：顶部运行/保存/发布/撤销重做；左侧节点库；中部画布；右侧参数面板；底部可折叠运行日志。运行按钮支持“运行全部”“运行到此节点”“仅运行此节点”“从此节点重跑”。存在未保存修改时运行会先生成不可变快照。离开页面前提示未同步修改。

### 4.3 节点参数面板

分为“输入”“提示词”“模型参数”“输出”“历史”五个 Tab。每个提示词 section 标注当前实际来源：用户覆盖/上游/默认；用户可一键恢复上游值或模板默认。提供最终 Prompt 预览但不暴露密钥。字段错误就地提示；上游资产可点击打开预览。

### 4.4 资产库页 `/projects/{projectId}/assets`

左侧分类树，顶部筛选，主区网格/列表。点击资产打开详情抽屉：预览、状态操作、版本时间线、生成参数、来源血缘。图片多版本支持并排比较与单选；切换 selected 前展示下游影响数量。

### 4.5 任务中心页 `/tasks`

按运行展示项目、工作流版本、进度、耗时、消耗、状态。支持查看 DAG 进度、取消、重试失败节点、恢复等待任务。管理员可按用户/供应商筛选；普通用户仅看自己的任务。

### 4.6 模板库页 `/templates`

Tab：内置模板、我的模板、共享模板（一期显示“即将开放”）。模板详情显示适用节点、版本、sections、模型默认参数和更新说明。用户可“基于此创建”，编辑后先预览渲染，再保存草稿或发布为个人稳定版。

### 4.7 关键交互防错

- 删除节点前提示受影响边和运行结果；删除只影响工作流草稿，不删除历史资产。
- 修改已审批内容时明确提示将产生新版本及下游 stale 范围。
- 高成本视频运行前展示预计点数；余额不足禁止提交。
- 锁定、解锁、批量重跑、删除资产必须二次确认。

---

## 5. 非功能需求

### 5.1 性能

| 指标 | 一期目标 |
|---|---|
| 画布规模 | 200 节点/300 边下拖拽与缩放 ≥ 45 FPS（主流桌面 Chrome） |
| 工作流加载 | 100 节点画布 P95 ≤ 2 秒（不含媒体原图） |
| 普通 API | P95 ≤ 500ms（不含外部 AI 调用） |
| 运行提交 | P95 ≤ 1 秒返回 runId |
| 状态推送 | 服务端状态变化到前端 P95 ≤ 2 秒 |
| 并发 | 单实例至少 50 个活跃工作流、20 个外部调用；各供应商可单独配置并发 |
| 资产列表 | 10 万项目资产量下分页查询 P95 ≤ 1 秒，必须走索引 |

前端对大画布使用节点虚拟化/只渲染可视区，日志和资产列表必须分页；不得把媒体 Base64 存入画布 JSON。

## 6. 里程碑与排期

估算单位为人日，假设团队：前端 2、后端 2、测试 1、产品/设计 1，外部 API 账号和对象存储已准备。总工期约 12–15 周；人日为研发+测试估算，不含供应商商务周期。

| 里程碑 | 目标与范围 | 验收标准 | 预估 | 依赖 |
|---|---|---|---:|---|
| T1 基础骨架 | Spring Boot/Vite、用户登录、SQLite 迁移、项目基础 CRUD、对象存储接入 | CI 可构建；注册登录；建表/回滚；创建项目；权限隔离测试通过 | 18–24 人日 | 无 |
| T2 画布与工作流 | React Flow、节点库、连线校验、保存加载、撤销重做、自动布局、分组、发布快照 | 100 节点保存刷新不丢失；非法类型/环被阻止；乐观锁冲突可见 | 25–32 人日 | T1 |
| T3 模板引擎 | 8 类鲸剧模板、2 通用节点协议、变量注入、预览、版本化、参数面板 | 针对每节点完成默认/上游/用户三层优先级自动测试；运行快照可复现 Prompt | 22–28 人日 | T1；与 T2 可部分并行 |
| T4 执行引擎+LLM | DAG、队列、SSE、LLM 网关、5 个 LLM 业务节点、审批门禁、日志 | “项目设定→大纲→角色卡→分集剧本→分镜”跑通；失败重试和恢复通过故障注入 | 32–40 人日 | T2、T3 |
| T5 图像与资产 | 图像网关、上传、私有存储、角色参考图/分镜图、多版本 select/lock、血缘、429 退避 | 角色图选定后才能生成分镜；切换版本使下游 stale；资产可预览下载 | 30–38 人日 | T4、对象存储 |
| T6 视频节点 | 视频适配器、异步轮询、提示词审批、图生视频、多版本、成本预估 | selected 分镜图+approved 提示词生成视频；长任务重启后继续；重复回调不重复扣费 | 20–28 人日 | T5、视频供应商 |
| T7 集成上线 | 标准预置管线、任务中心、模板库、管理配置、E2E | 新用户从创建项目到选定镜头视频全流程成功；核心 E2E/权限/恢复测试通过；有回滚方案 | 25–35 人日 | T1–T6 |

### 6.1 关键路径

`T1 → T2/T3 → T4 → T5 → T6 → T7`。T2 与 T3 可并行；资产存储、供应商选型、内容审核和 API 账号申请应从 T1 同步启动，否则会阻塞 T5/T6。

### 6.2 每阶段质量门槛

- 单元测试：模板求值、拓扑排序、状态机、幂等和额度结算核心模块覆盖率 ≥ 80%。
- 集成测试：每个供应商适配器具备 Mock 合约测试；至少一个真实沙箱冒烟测试。
- E2E：主流程、审批拦截、多版本选择、失败重试、断点续跑、权限越权。
- 发布：数据库迁移可回滚，配置与密钥不进代码仓库，监控面板与告警已启用。

---

## 7. 风险与开放问题

### 7.1 风险清单

| 风险 | 概率/影响 | 应对 | 责任阶段 |
|---|---|---|---|
| 视频 API 数分钟且状态不稳定 | 高/高 | 异步轮询、持久化 taskId、超时分层、断点恢复 | T6 |
| 多供应商参数/审核/错误码差异 | 高/高 | Adapter + 标准错误码 + 合约测试，不让业务层感知供应商 | T4–T6 |
| 参考图 URL 过期或泄露 | 中/高 | 私有存储、短时签名、任务时长感知 TTL、域名白名单 | T5 |
| 画布 JSON 与执行模型不一致 | 中/高 | 后端规范化、Schema 版本、发布快照、双向契约测试 | T2/T4 |
| 人物一致性达不到用户预期 | 高/高 | 强制选定参考图、锁定词注入、多参考图、低 denoise、模型评测基线 | T5/T6 |
| 上游修改造成下游数据不一致 | 高/高 | 精确资产版本引用、血缘表、stale 传播、影响分析 | T4/T5 |
| 并发 select 产生两个选中版本 | 中/高 | 事务锁 + 唯一选择表/约束 + 幂等请求 | T5 |
| 模板升级导致历史不可复现 | 中/高 | 版本不可变、运行保存模板完整快照与 checksum | T3 |
| API 成本失控或重试重复计费 | 高/高 | 预估确认、额度预占、幂等键、外部 taskId 恢复、日限额告警 | T4–T6 |
| 429 导致队列雪崩 | 高/中 | 供应商级限流、Retry-After、抖动退避、熔断和降级路由 | T4–T6 |
| 输出 JSON 不符合 Schema | 高/中 | 结构化输出、Schema 校验、一次自动修复、人工编辑兜底 | T4 |
| 复杂画布对新手不友好 | 中/高 | 创建向导、标准模板、语义节点、错误定位和渐进式参数面板 | T2/T7 |
| 长 Prompt/大上下文超过模型限制 | 中/中 | token 预估、摘要策略、按集/角色裁剪、明确截断告警 | T4 |
| 供应商内容审核误杀 | 中/中 | 展示标准错误与建议，允许更换合规模型，不自动规避审核 | T4–T6 |
| 用户上传版权/肖像风险 | 中/高 | 用户声明、举报/删除机制、审计与内容政策 | T5/T7 |
| SQLite 并发写锁（单写者） | 中/中 | 启用 WAL 模式、busy_timeout；写操作串行化、重试 | T1 |
| SSE 断线丢状态 | 中/中 | eventId 持久化、Last-Event-ID 补拉、详情接口校准 | T4 |
| 批量镜头导致任务爆量 | 高/中 | 扇出上限、分批提交、项目级并发和暂停机制 | T5/T6 |

### 7.2 上线前必须决策的开放问题

| 编号 | 问题 | 建议默认决策 | 最晚决策点 |
|---|---|---|---|
| O-1 | 一期生产数据库 | SQLite（WAL 模式）；后续用户量增长再评估 MySQL | T1 开始 |
| O-2 | 首发 LLM/图像/视频供应商 | 每类 1 个主供应商 + 接口层预留备选 | T1 结束 |
| O-3 | 部署目标与区域 | 选择靠近供应商且满足数据合规的单区域；明确是否沿用现有服务器 | T1 结束 |
| O-4 | 一期用户系统深度 | 做基础多用户和资源隔离，不做团队空间 | T1 开始 |
| O-5 | 文本是否也强制逐阶段人工确认 | 默认强制；专业用户二期可配置自动门禁 | T3 结束 |
| O-6 | 锁定后谁可解锁 | 一期资产所有者可解锁并审计；团队权限二期再细分 | T5 开始 |
| O-7 | 视频分辨率与时长上限 | 默认 480P 防漂移、单镜 3–6 秒；按模型能力配置 | T6 开始 |
| O-8 | 免费额度与点数换算 | 上线前按真实供应商成本+毛利制定；后端不得硬编码 | T7 开始 |
| O-9 | 内容审核责任边界 | 平台预审 + 供应商审核 + 申诉/人工处理流程 | T5 开始 |
| O-10 | “成片”定义 | 一期为选定镜头视频集合/导出包；自动拼接、配音和字幕列入二期 | 产品评审 |

### 7.3 二期候选

自动剪辑与时间线、TTS/配音/口型、字幕与 BGM、团队协作、共享模板市场、Webhook/API 开放平台、批量剧集生产、A/B 成本质量路由、人物一致性自动评分、成片发布到内容平台。

---

## 8. 一期整体验收标准

1. 用户可注册登录、创建项目并由标准模板实例化完整工作流。
2. 画布可编辑、校验、保存、刷新恢复和发布；100 节点规模满足性能目标。
3. 8 种短剧节点与 LLM 通用/素材上传节点参数完整，提示词来源和覆盖优先级可见。
4. 从项目设定到图生视频的门禁严格符合鲸剧方法论，不能绕过角色参考图和分镜图选择。
5. 每次运行可追溯到工作流、模板、参数、输入资产和供应商版本；历史运行可复现。
6. 429、超时、进程重启和 SSE 断线场景可恢复，不重复创建高成本任务或重复扣费。
7. 资产多版本、select、lock、stale 传播、血缘查看和私有下载全部通过测试。
8. 管理员可配置供应商/模型/额度并查看脱敏用量；任何日志不泄露 API Key 或永久公网素材 URL。

