package com.dramastudio.service;
import com.dramastudio.entity.*; import com.dramastudio.repository.*; import com.dramastudio.template.NodeCatalog; import com.fasterxml.jackson.databind.ObjectMapper; import java.util.*; import org.springframework.http.HttpStatus; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException;
@Service public class ProjectService {
 private final ProjectRepository projects; private final WorkflowRepository workflows; private final JdbcTemplate jdbc; private final ObjectMapper json;
 public ProjectService(ProjectRepository p,WorkflowRepository w,JdbcTemplate j,ObjectMapper o){projects=p;workflows=w;jdbc=j;json=o;}
 public List<Project> list(String user){return projects.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user);}
 public Project getOwned(String id,String user){return projects.findById(id).filter(p->p.deletedAt==null&&p.ownerUserId.equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"项目不存在"));}
 @Transactional public Workflow create(CreateProject r,String user){Project p=new Project();p.ownerUserId=user;p.name=r.name;p.description=r.description;p.storyEnvironment=r.storyEnvironment;p.genresJson=write(r.genres==null?List.of():r.genres);p.dramaType=or(r.dramaType,"动态漫");p.artStyle=or(r.artStyle,"半写实国漫");p.episodeCount=r.episodeCount==null?10:r.episodeCount;p.episodeDurationSec=r.episodeDurationSec==null?90:r.episodeDurationSec;projects.saveAndFlush(p);Workflow w=new Workflow();w.projectId=p.id;w.name="自由创作画布";w.createdBy=user;w.canvasJson=emptyCanvas();workflows.saveAndFlush(w);normalize(w);return w;}
 public record CreateProject(String name,String description,String storyEnvironment,List<String> genres,String dramaType,String artStyle,Integer episodeCount,Integer episodeDurationSec){}

 /**
  * 标准短剧管线（含分集扇出）：
  *   PROJECT_SETUP -> STORY_OUTLINE -> CHARACTER_CARD
  *                 -> SCENE_PROP_CARD（场景/道具卡）
  *                 -> EPISODE_SPLITTER（剧本自动拆多集）
  *                 -> EPISODE_BRANCH（按集数扇出 N 条分支）
  *                      ├─ EPISODE_SCRIPT   （每集一份剧本）
  *                      ├─ STORYBOARD_SCRIPT（每集分镜表）
  *                      ├─ STORYBOARD_IMAGE （角色图+场景图+道具图作参考图）
  *                      └─ IMAGE_TO_VIDEO   （分镜图生视频）
  *                 -> CHARACTER_REFERENCE_IMAGE（全局角色定妆照，扇出前）
  *                 -> SCENE_REFERENCE_IMAGE / PROP_REFERENCE_IMAGE（参考图）
  */
 private String emptyCanvas(){return write(Map.of("schemaVersion","1.0","viewport",Map.of("x",0,"y",0,"zoom",1),"nodes",List.of(),"edges",List.of()));}
 @Transactional public Workflow createDramaTemplate(String projectId,String user){Project p=getOwned(projectId,user);Workflow w=new Workflow();w.projectId=p.id;w.name="短剧管线模板";w.createdBy=user;w.canvasJson=dramaTemplate(p);workflows.saveAndFlush(w);normalize(w);return w;}
 private String standardCanvas(Project p){return dramaTemplate(p);}
 private String dramaTemplate(Project p){try{
   var ns=new ArrayList<Map<String,Object>>();var pos=new HashMap<String,int[]>();var id=new HashMap<String,String>();
   String[][] nodes={
     {"SETUP","PROJECT_SETUP",null},
     {"OUTLINE","STORY_OUTLINE",null},
     {"CHAR","CHARACTER_CARD",null},
     {"SCENEPROP","SCENE_PROP_CARD",null},
     {"CHARIMG","CHARACTER_REFERENCE_IMAGE",null},
     {"SCENEIMG","SCENE_REFERENCE_IMAGE",null},
     {"PROPIMG","PROP_REFERENCE_IMAGE",null},
     {"SPLIT","EPISODE_SPLITTER",null},
     {"BRANCH","EPISODE_BRANCH",null},
     {"SCRIPT","EPISODE_SCRIPT",null},
     {"BOARD","STORYBOARD_SCRIPT",null},
     {"BOARDIMG","STORYBOARD_IMAGE",null},
     {"VIDEO","IMAGE_TO_VIDEO",null}
   };
   int idx=0;
   for(String[] spec:nodes){
     var d=NodeCatalog.get(spec[1]); if(d==null)continue;
     String nid=UUID.randomUUID().toString(); id.put(spec[0],nid);
     Map<String,Object> cfg=defaults(d);
     if("EPISODE_SPLITTER".equals(spec[1])){
       cfg.put("episode_count",p.episodeCount);
       cfg.put("episode_duration_sec",p.episodeDurationSec);
     }
     if("PROJECT_SETUP".equals(spec[1])){
       cfg.put("title",p.name==null?"":p.name);
       cfg.put("story_environment",p.storyEnvironment==null?"":p.storyEnvironment);
       cfg.put("episode_count",p.episodeCount);
       cfg.put("episode_duration_sec",p.episodeDurationSec);
       cfg.put("drama_type",p.dramaType);
       cfg.put("art_style",p.artStyle);
     }
     int col=idx%5, row=idx/5;
     ns.add(Map.of("id",nid,"type",spec[1],
         "position",Map.of("x",80+col*280,"y",80+row*260),
         "data",Map.of("name",d.label(),"config",cfg)));
     idx++;
   }
   // 每集一个产出概览节点
   var overview=NodeCatalog.get("EPISODE_OVERVIEW");
   for(int episode=1;episode<=p.episodeCount;episode++){
     var config=defaults(overview);config.put("episode_number",episode);
     ns.add(Map.of("id",UUID.randomUUID().toString(),"type",overview.type(),
         "position",Map.of("x",80+(episode-1)*260,"y",80+3*260+40),
         "data",Map.of("name",overview.label()+" "+episode,"config",config)));
   }
   var es=new ArrayList<Map<String,Object>>();
   // 设定 → 大纲 / 角色卡 / 场景道具卡
   es.add(edge(id.get("SETUP"),"project_state",id.get("OUTLINE"),"project_state"));
   es.add(edge(id.get("SETUP"),"project_state",id.get("CHAR"),"project_state"));
   es.add(edge(id.get("OUTLINE"),"outline",id.get("CHAR"),"outline"));
   es.add(edge(id.get("OUTLINE"),"outline",id.get("SCENEPROP"),"outline"));
   // 角色/场景/道具 → 参考图
   es.add(edge(id.get("CHAR"),"characters",id.get("CHARIMG"),"character"));
   es.add(edge(id.get("SCENEPROP"),"scenes",id.get("SCENEIMG"),"scene"));
   es.add(edge(id.get("SCENEPROP"),"props",id.get("PROPIMG"),"props"));
   // 大纲 → 分集拆解 → 分集扇出
   es.add(edge(id.get("OUTLINE"),"outline",id.get("SPLIT"),"outline"));
   es.add(edge(id.get("SPLIT"),"episodes",id.get("BRANCH"),"episodes"));
   // 扇出分支：每集剧本 → 分镜表 → 分镜图 → 视频
   es.add(edge(id.get("BRANCH"),"episode",id.get("SCRIPT"),"episode"));
   es.add(edge(id.get("OUTLINE"),"outline",id.get("SCRIPT"),"outline"));
   es.add(edge(id.get("CHAR"),"characters",id.get("SCRIPT"),"characters"));
   es.add(edge(id.get("SCRIPT"),"episode_script",id.get("BOARD"),"episode_script"));
   es.add(edge(id.get("CHAR"),"characters",id.get("BOARD"),"characters"));
   es.add(edge(id.get("BOARD"),"shots",id.get("BOARDIMG"),"shots"));
   es.add(edge(id.get("CHARIMG"),"images",id.get("BOARDIMG"),"character_refs"));
   es.add(edge(id.get("SCENEIMG"),"images",id.get("BOARDIMG"),"scene_assets"));
   es.add(edge(id.get("BOARDIMG"),"images",id.get("VIDEO"),"source_image"));
   es.add(edge(id.get("BOARD"),"shots",id.get("VIDEO"),"shot"));
   return json.writeValueAsString(Map.of("schemaVersion","1.0",
       "viewport",Map.of("x",0,"y",0,"zoom",0.55),"nodes",ns,"edges",es));
 }catch(Exception e){throw new IllegalStateException(e);}}

 private Map<String,Object> defaults(NodeCatalog.Definition d){var m=new LinkedHashMap<String,Object>();d.params().forEach((k,v)->m.put(k,v.defaultValue()));return m;}
 private Map<String,Object> edge(String s,String sh,String t,String th){return Map.of("id",UUID.randomUUID().toString(),"source",s,"sourceHandle",sh,"target",t,"targetHandle",th);}
 @SuppressWarnings("unchecked") public void normalize(Workflow w){try{Map<String,Object> canvas=json.readValue(w.canvasJson,Map.class);jdbc.update("delete from edges where workflow_id=?",w.id);jdbc.update("delete from nodes where workflow_id=?",w.id);String now=java.time.Instant.now().toString();for(var n:(List<Map<String,Object>>)canvas.getOrDefault("nodes",List.of())){Map<String,Object> pos=(Map<String,Object>)n.get("position"),data=(Map<String,Object>)n.getOrDefault("data",Map.of());jdbc.update("insert into nodes(id,workflow_id,node_type,name,config_json,position_x,position_y,created_at,updated_at) values(?,?,?,?,?,?,?,?,?)",n.get("id"),w.id,n.get("type"),data.getOrDefault("name",n.get("type")),write(data.getOrDefault("config",Map.of())),pos.getOrDefault("x",0),pos.getOrDefault("y",0),now,now);}for(var e:(List<Map<String,Object>>)canvas.getOrDefault("edges",List.of()))jdbc.update("insert into edges(id,workflow_id,source_node_id,source_port,target_node_id,target_port,created_at,updated_at) values(?,?,?,?,?,?,?,?)",e.get("id"),w.id,e.get("source"),e.getOrDefault("sourceHandle","output"),e.get("target"),e.getOrDefault("targetHandle","input"),now,now);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"画布 JSON 无效: "+e.getMessage());}}
 private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}} private String or(String v,String d){return v==null||v.isBlank()?d:v;}
}
