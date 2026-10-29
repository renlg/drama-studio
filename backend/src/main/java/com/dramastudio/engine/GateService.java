package com.dramastudio.engine;
import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class GateService {
 private final JdbcTemplate jdbc;public GateService(JdbcTemplate j){jdbc=j;}
 public List<String> missing(String project,String type){return missing(project,type,Map.of());}
 public List<String> missing(String project,String type,Map<String,Object> config){return List.of();}
 private void approved(String p,String type,List<String> out){Integer n=jdbc.queryForObject("select count(*) from assets where project_id=? and asset_type=? and content_status in ('approved','locked','selected') and deleted_at is null",Integer.class,p,type);if(n==null||n==0)out.add(type+" 需要 approved/locked");}
 private void selected(String p,String type,List<String> out){Integer n=jdbc.queryForObject("select count(*) from assets where project_id=? and asset_type=? and (is_selected=1 or content_status='locked') and deleted_at is null",Integer.class,p,type);if(n==null||n==0)out.add(type+" 需要 selected/locked");}
 private boolean has(String p,String type){Integer n=jdbc.queryForObject("select count(*) from assets where project_id=? and asset_type=? and deleted_at is null",Integer.class,p,type);return n!=null&&n>0;}
 private void approvedAny(String p,List<String> types,List<String> out){for(String type:types)if(hasApproved(p,type))return;out.add(String.join(" 或 ",types)+" 需要 approved/locked");}
 private boolean hasApproved(String p,String type){Integer n=jdbc.queryForObject("select count(*) from assets where project_id=? and asset_type=? and content_status in ('approved','locked','selected') and deleted_at is null",Integer.class,p,type);return n!=null&&n>0;}
}
