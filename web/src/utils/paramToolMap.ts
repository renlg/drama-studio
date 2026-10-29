export const PARAM_TOOL_MAP:Record<string,Record<string,string[]>>={
  TEXT:{prompt:['生成','扩写','润色','改写','续写'],system_prompt:['生成','扩写','润色','改写','续写'],negative_prompt:['生成','扩写','润色','改写','续写'],temperature:['生成','扩写','润色','改写','续写'],max_tokens:['生成','扩写','润色','改写','续写'],output_format:['生成','扩写','润色','改写','续写']},
  IMAGE:{prompt:['文生图','图生图','宫格生成'],negative_prompt:['文生图','图生图','宫格生成'],model:['文生图','图生图','宫格生成'],width:['文生图','图生图','裁剪','宫格生成'],height:['文生图','图生图','裁剪','宫格生成'],seed:['文生图','图生图','宫格生成'],steps:['文生图','图生图','宫格生成'],cfg_scale:['文生图','图生图','宫格生成'],image_strength:['图生图'],output_count:['文生图','图生图'],key_color:['抠图'],similarity:['抠图'],blend:['抠图'],output_format:['抠图'],x:['裁剪'],y:['裁剪'],angle:['旋转'],rows:['宫格切分','宫格生成'],cols:['宫格切分','宫格生成'],grid_type:['宫格生成']},
  VIDEO:{prompt:['文生视频','图生视频'],negative_prompt:['文生视频','图生视频'],model:['文生视频','图生视频'],aspect_ratio:['文生视频','图生视频'],duration_sec:['文生视频','图生视频'],first_frame_url:['图生视频']},
  AUDIO:{files:['上传'],description:['上传'],start_sec:['截取'],end_sec:['截取'],volume:['音量调整']},
  CHARACTER:{name:['生成','上传'],appearance:['生成'],description:['生成','上传'],voice_desc:['生成','上传'],prompt:['生成'],files:['上传']},
  SCENE:{name:['生成','上传'],view_name:['生成'],description:['生成','上传'],scene_group:['生成','上传'],prompt:['生成'],files:['上传']},
};
