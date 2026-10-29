package com.dramastudio.service;

import com.dramastudio.entity.Toolbox;
import com.dramastudio.repository.ToolboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class ToolboxService {
  private final ToolboxRepository repository;private final ObjectMapper json;
  public ToolboxService(ToolboxRepository repository,ObjectMapper json){this.repository=repository;this.json=json;}
  public List<Map<String,Object>> list(String userId){return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();}
  @Transactional public Map<String,Object> create(String userId,String name,List<Map<String,Object>> nodes,List<Map<String,Object>> edges){
    if(name==null||name.isBlank())throw new IllegalArgumentException("工具箱名称不能为空");if(nodes==null||nodes.isEmpty())throw new IllegalArgumentException("至少选择一个节点");
    Toolbox t=new Toolbox();t.userId=userId;t.name=name.trim();t.nodesJson=write(nodes);t.edgesJson=write(edges==null?List.of():edges);return view(repository.save(t));
  }
  @Transactional public Map<String,Object> update(String userId,String id,String name,List<Map<String,Object>> nodes,List<Map<String,Object>> edges){
    Toolbox t=repository.findByIdAndUserId(id,userId).orElseThrow();if(name!=null&&!name.isBlank())t.name=name.trim();if(nodes!=null)t.nodesJson=write(nodes);if(edges!=null)t.edgesJson=write(edges);return view(repository.save(t));
  }
  @Transactional public void delete(String userId,String id){Toolbox t=repository.findByIdAndUserId(id,userId).orElseThrow();repository.delete(t);}
  private Map<String,Object> view(Toolbox t){Map<String,Object> out=new LinkedHashMap<>();out.put("id",t.id);out.put("name",t.name);out.put("nodes",read(t.nodesJson));out.put("edges",read(t.edgesJson));out.put("createdAt",t.createdAt);return out;}
  private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("工具箱数据无法序列化",e);}}
  @SuppressWarnings("unchecked") private List<Map<String,Object>> read(String value){try{return json.readValue(value,List.class);}catch(Exception e){throw new IllegalArgumentException("工具箱数据损坏",e);}}
}
