package com.dramastudio.template;

import java.util.*;

/** 六类通用创作节点目录。具体能力由节点的 tool 参数在运行时决定。 */
public final class NodeCatalog {
  public record Param(String type,Object defaultValue,boolean editable,String source,List<Object> options){
    public Param(String type,Object defaultValue,boolean editable,String source){this(type,defaultValue,editable,source,List.of());}
  }
  public record Port(String name,String type,boolean required,boolean multiple){}
  public record Definition(String type,String label,String capability,String category,List<Port> inputs,List<Port> outputs,LinkedHashMap<String,Param> params){}

  private static final LinkedHashMap<String,Definition> ALL=new LinkedHashMap<>();
  private static Param p(String type,Object value,boolean editable,String source){return new Param(type,value,editable,source);}
  private static Param pe(Object value,String source,Object... options){return new Param("enum",value,true,source,List.of(options));}
  private static LinkedHashMap<String,Param> params(Object... values){var m=new LinkedHashMap<String,Param>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],(Param)values[i+1]);return m;}
  private static List<Port> ports(Port... values){return List.of(values);}
  private static Port port(String name,String type,boolean required,boolean multiple){return new Port(name,type,required,multiple);}

  static {
    add("TEXT","文本节点","LLM","TEXT",ports(port("text","TEXT",false,true),port("refs","ANY",false,true)),ports(port("text","TEXT",true,false)),params(
        "tool",pe("生成","USER","生成","扩写","润色","改写","续写"),"prompt",p("textarea","",true,"USER"),
        "system_prompt",p("textarea","你是专业内容创作者，直接输出成品，不要复述需求",true,"USER"),"negative_prompt",p("textarea","",true,"USER"),
        "temperature",p("decimal",0.7,true,"DEFAULT"),"max_tokens",p("int",4096,true,"DEFAULT"),"output_format",pe("TEXT","DEFAULT","TEXT","JSON")));

    add("IMAGE","图片节点","RUNTIME","IMAGE",ports(port("refs","ASSET_REF_LIST",false,true),port("chars","CHARACTER",false,true),port("scenes","SCENE",false,true)),ports(port("images","IMAGE_ASSET_LIST",true,true)),params(
        "tool",pe("文生图","USER","文生图","图生图","抠图","裁剪","旋转","宫格切分","宫格生成"),"prompt",p("textarea","",true,"USER"),
        "negative_prompt",p("textarea","different face, extra people, watermark, text",true,"USER"),"width",p("int",1024,true,"DEFAULT"),"height",p("int",1536,true,"DEFAULT"),
        "seed",p("long",null,true,"DEFAULT"),"steps",p("int",20,true,"DEFAULT"),"cfg_scale",p("decimal",7.0,true,"DEFAULT"),"image_strength",p("decimal",0.6,true,"DEFAULT"),
        "output_count",p("int",1,true,"DEFAULT"),"key_color",p("string","#FFFFFF",true,"DEFAULT"),"similarity",p("decimal",0.12,true,"DEFAULT"),"blend",p("decimal",0.05,true,"DEFAULT"),
        "output_format",pe("png","DEFAULT","png","webp"),"x",p("int",0,true,"USER"),"y",p("int",0,true,"USER"),"angle",pe(90,"USER",90,180,270),
        "rows",p("int",3,true,"USER"),"cols",p("int",3,true,"USER"),"grid_type",pe("三视图","USER","三视图","表情九宫格","四宫格","九宫格")));

    add("VIDEO","视频节点","RUNTIME","VIDEO",ports(port("refs","ASSET_REF_LIST",false,true),port("chars","CHARACTER",false,true),port("scenes","SCENE",false,true),port("audios","AUDIO_ASSET_LIST",false,true)),ports(port("videos","VIDEO_ASSET_LIST",true,true)),params(
        "tool",pe("文生视频","USER","文生视频","图生视频","智能多帧","拼接","截取","旋转","倒放","倍速","补帧","分辨率调整","抽帧","去字幕","音频分离","配音配乐"),
        "prompt",p("textarea","",true,"USER"),"negative_prompt",p("textarea","",true,"USER"),"mode",pe("text","DEFAULT","text","keyframe","reference"),
        "duration_sec",p("decimal",5,true,"DEFAULT"),"resolution",pe("720P","DEFAULT","720P"),"aspect_ratio",pe("9:16","DEFAULT","9:16","16:9","1:1"),
        "first_frame_url",p("string","",true,"UPSTREAM"),"seed",p("long",null,true,"DEFAULT"),"camera_movement",p("string","轻微推进",true,"DEFAULT"),"output_count",p("int",1,true,"DEFAULT"),
        "shot_duration_sec",p("int",5,true,"DEFAULT"),"max_shots",p("int",5,true,"DEFAULT"),"transition",pe("none","DEFAULT","none","fade"),
        "transition_duration",p("decimal",0.5,true,"DEFAULT"),"reencode",p("boolean",true,true,"DEFAULT"),"output_name",p("string","merged",true,"DEFAULT"),
        "start_sec",p("decimal",0,true,"DEFAULT"),"end_sec",p("decimal",null,true,"DEFAULT"),"angle",pe(90,"USER",90,180,270),"reverse_audio",p("boolean",true,true,"USER"),
        "speed",p("decimal",1.0,true,"USER"),"fps",p("int",30,true,"DEFAULT"),"interpolation_mode",pe("mci","DEFAULT","dup","blend","mci"),
        "width",p("int",720,true,"DEFAULT"),"height",p("int",1280,true,"DEFAULT"),"fit",pe("cover","DEFAULT","contain","cover","stretch"),"pad_color",p("string","#000000",true,"DEFAULT"),
        "extract_mode",pe("single","DEFAULT","single","first","last","every_n"),"at_sec",p("decimal",0,true,"DEFAULT"),"every_n_sec",p("decimal",2,true,"DEFAULT"),"max_frames",p("int",10,true,"DEFAULT"),
        "x",p("int",0,true,"USER"),"y",p("int",0,true,"USER"),"w",p("int",300,true,"USER"),"h",p("int",80,true,"USER"),"format",pe("m4a","USER","m4a","mp3","wav"),
        "audio_volume",p("decimal",1.0,true,"DEFAULT"),"keep_original_audio",p("boolean",false,true,"DEFAULT"),"audio_offset_sec",p("decimal",0,true,"DEFAULT"),"shortest",p("boolean",true,true,"DEFAULT")));

    add("AUDIO","音频节点","RUNTIME","AUDIO",ports(port("refs","ASSET_REF_LIST",false,true)),ports(port("audios","AUDIO_ASSET_LIST",true,true)),params(
        "tool",pe("上传","USER","上传","截取","音量调整"),"files",p("assetRef[]",List.of(),true,"USER"),"description",p("textarea","",true,"USER"),
        "start_sec",p("decimal",0,true,"USER"),"end_sec",p("decimal",null,true,"USER"),"volume",p("decimal",1.0,true,"USER")));

    add("CHARACTER","角色节点","RUNTIME","CHARACTER",ports(port("refs","ASSET_REF_LIST",false,true)),ports(port("character","CHARACTER",true,false)),params(
        "tool",pe("生成","USER","生成","上传"),"name",p("string","",true,"USER"),"appearance",p("string","默认形象",true,"USER"),
        "description",p("textarea","",true,"USER"),"voice_desc",p("textarea","",true,"USER"),"prompt",p("textarea","",true,"USER"),"files",p("assetRef[]",List.of(),true,"USER")));
    add("SCENE","场景节点","RUNTIME","SCENE",ports(port("refs","ASSET_REF_LIST",false,true)),ports(port("scene","SCENE",true,false)),params(
        "tool",pe("生成","USER","生成","上传"),"name",p("string","",true,"USER"),"view_name",p("string","默认视角",true,"USER"),
        "description",p("textarea","",true,"USER"),"scene_group",p("string","",true,"USER"),"prompt",p("textarea","",true,"USER"),"files",p("assetRef[]",List.of(),true,"USER")));
  }

  private static void add(String type,String label,String capability,String category,List<Port> inputs,List<Port> outputs,LinkedHashMap<String,Param> params){
    params.putIfAbsent("provider",p("enum","AUTO",true,"DEFAULT"));params.putIfAbsent("model",p("string","",true,"DEFAULT"));
    params.putIfAbsent("timeout_sec",p("int","VIDEO".equals(type)?1800:"IMAGE".equals(type)?300:120,true,"DEFAULT"));params.putIfAbsent("retry_count",p("int",3,true,"DEFAULT"));
    ALL.put(type,new Definition(type,label,capability,category,inputs,outputs,params));
  }
  public static Map<String,Definition> all(){return Collections.unmodifiableMap(ALL);}
  public static Definition get(String type){return ALL.get(type);}
  private NodeCatalog(){}
}
