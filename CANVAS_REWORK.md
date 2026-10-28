# 通用 AI 创作画布改造

## 主要改动

- `NodeCatalog` 新增通用文本、图片、视频、智能多帧，以及拼接、裁剪、音频混流、缩放、抽帧、抠图、补帧节点，并向前端提供 `category` 和枚举选项。
- `PromptBuilder`、`WorkflowExecutor`、`AiConnectAdapters` 支持通用生成参数；`IMAGE_GEN.output_count` 和 `VIDEO_MULTI_KEYFRAME` 均串行调用供应商。
- 新增 `FfmpegService`，通过 `ProcessBuilder` 运行 ffmpeg；输入从 OSS 下载到临时目录，输出上传 OSS 并创建资产，结束后清理临时文件。
- 新建项目默认创建空白“自由创作画布”；原短剧管线保留为模板，可在模板库选择项目后一键创建。
- 节点库改为读取后端目录并按文本、图片、视频、剪辑、短剧、素材分组；画布工具栏新增节点定位查找。

## 新节点参数

- `TEXT_GEN`：`prompt`、`system_prompt`、`negative_prompt`、`temperature`、`max_tokens`、`output_format`。
- `IMAGE_GEN`：`prompt`、`negative_prompt`、`width`、`height`、`seed`、`steps`、`cfg_scale`、`image_strength`、`output_count`。
- `VIDEO_GEN`：`prompt`、`negative_prompt`、`mode`、`duration_sec`、`resolution`、`aspect_ratio`、`first_frame_url`、`seed`。
- `VIDEO_MULTI_KEYFRAME`：`prompt`、`shot_duration_sec`、`aspect_ratio`、`max_shots`。
- `VIDEO_CONCAT`：`transition`、`transition_duration`、`reencode`、`output_name`。
- `MEDIA_TRIM`：`start_sec`、`end_sec`、`reencode`。
- `VIDEO_MUX_AUDIO`：`audio_volume`、`keep_original_audio`、`audio_offset_sec`、`shortest`。
- `MEDIA_SCALE`：`width`、`height`、`fit`、`pad_color`。
- `MEDIA_EXTRACT_FRAME`：`mode`、`at_sec`、`every_n_sec`、`max_frames`。
- `IMAGE_CHROMA_KEY`：`key_color`、`similarity`、`blend`、`output_format`。
- `VIDEO_INTERPOLATE`：`fps`、`mode`。

## ffmpeg 配置

解析顺序：`app.ffmpeg.bin` → 环境变量 `FFMPEG_BIN` → `/usr/bin/ffmpeg` → `/usr/local/bin/ffmpeg` → 通过 `python3` 调用 `imageio_ffmpeg.get_ffmpeg_exe()`。无需改现有 `application.yml`；部署时可使用 JVM 配置或环境变量。

## 已知限制

- ai-connect 图片接口固定 `n=1`，多图由执行器串行请求；多张参考图当前只使用第 1 张，并在资产 metadata 中记录 warning。
- 视频参数固定 `720P`、`n=1`，时长会钳制到 4–12 秒；免费通道队列满时仍可能在重试后失败。
- `fade` 拼接使用 `xfade` 并重新编码视频；当前不保留输入音轨，需要音频时可在下游使用 `VIDEO_MUX_AUDIO`。
- 抠图适合纯色或干净背景，不等同于 AI 语义抠图。
