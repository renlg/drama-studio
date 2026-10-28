import {Empty,Form,Input,InputNumber,Select,Switch,Tabs,Typography,Tag} from 'antd';
import {useWorkflow} from '../store/workflow';
import {useCatalog,paramLabel} from '../store/catalog';
import type {CatalogDef,CatalogParam} from '../store/catalog';
import {EpisodeAssetsOutput} from './EpisodeOverviewNode';
import NodeAssetsOutput from './NodeAssetsOutput';

type Row=[string,CatalogParam|undefined,unknown];

/** 输入来源徽标：让用户知道这个值从哪来（PRD 硬约束：用户覆盖 > 上游 > 模板默认） */
function SourceTag({source}:{source?:string}){
  if(!source)return null;
  const map:Record<string,{c:string;t:string}>={USER:{c:'blue',t:'用户'},UPSTREAM:{c:'purple',t:'上游'},DEFAULT:{c:'default',t:'默认'}};
  const m=map[source];
  if(!m)return null;
  return <Tag color={m.c} style={{marginInlineStart:6}}>{m.t}</Tag>;
}

/** 按参数类型渲染控件 */
function Field({def,value,onChange}:{def?:CatalogParam;value:unknown;onChange:(v:unknown)=>void}){
  const t=def?.type??'string';

  if(t==='boolean')return <Switch checked={Boolean(value)} onChange={onChange}/>;

  if(t==='int'||t==='long')return <InputNumber style={{width:'100%'}} precision={0} value={value as number|undefined} onChange={onChange}/>;

  if(t==='decimal')return <InputNumber style={{width:'100%'}} step={0.05} value={value as number|undefined} onChange={onChange}/>;

  if(t==='enum'&&def?.options?.length)return <Select style={{width:'100%'}} value={value} options={def.options.map(x=>({value:x,label:String(x)}))} onChange={onChange}/>;
  if(t==='enum')return <Input value={String(value??'')} onChange={e=>onChange(e.target.value)} placeholder="枚举值"/>;

  if(t==='string[]')return <Select mode="tags" style={{width:'100%'}} value={(value as string[])??[]} onChange={onChange} placeholder="回车添加"/>;

  if(t==='textarea')return <Input.TextArea autoSize={{minRows:3,maxRows:10}} value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;

  if(t==='assetRef')return <Input value={String(value??'')} onChange={e=>onChange(e.target.value)} placeholder="由上游自动填充"/>;

  if(t==='assetRef[]')return <Input.TextArea autoSize={{minRows:2,maxRows:6}} value={Array.isArray(value)?value.join('\n'):String(value??'')} onChange={e=>onChange(e.target.value.split('\n').filter(Boolean))} placeholder="由上游自动填充"/>;

  return <Input value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;
}

function buildRows(def:CatalogDef|undefined,config:Record<string,unknown>):Row[]{
  const out:Row[]=[];
  if(def){
    for(const k of Object.keys(def.params)){
      const p=def.params[k];
      out.push([k,p,config[k]!==undefined?config[k]:p.defaultValue]);
    }
    return out;
  }
  for(const k of Object.keys(config)){
    out.push([k,undefined,config[k]]);
  }
  return out;
}

export default function ParameterPanel(){
  const s=useWorkflow();
  const catalog=useCatalog();
  const n=s.nodes.find(x=>x.id===s.selectedId);

  if(!n)return <aside className="parameter-panel"><Empty description="选择节点编辑参数"/></aside>;

  const def:CatalogDef|undefined=catalog.defs[n.type??''];
  const config:Record<string,unknown>=n.data.config??{};
  const rows:Row[]=buildRows(def,config);

  const bySource=(src:string)=>{const out:Row[]=[];for(const r of rows){if(r[1]?.source===src)out.push(r);}return out;};
  const userRows=bySource('USER');
  const upRows=bySource('UPSTREAM');
  const defRows:Row[]=[];for(const r of rows){if(!r[1]||r[1].source==='DEFAULT')defRows.push(r);}

  const renderGroup=(list:Row[])=>(
    <Form layout="vertical" size="small">
      {list.map(([k,p,v])=>(
        <Form.Item
          key={k}
          style={{marginBottom:12}}
          label={<span>{paramLabel(k)}<SourceTag source={p?.source}/></span>}
          extra={p?`${k} · ${p.type}`:k}
        >
          <Field def={p} value={v} onChange={x=>s.updateConfig(n.id,k,x)}/>
        </Form.Item>
      ))}
    </Form>
  );

  return (
    <aside className="parameter-panel">
      <Typography.Title level={4} style={{marginBottom:4}}>{n.data.name}</Typography.Title>
      <Typography.Text type="secondary" style={{fontSize:12}}>{n.type} · 共 {rows.length} 个参数</Typography.Text>
      <Tabs
        style={{marginTop:8}}
        items={[
          {key:'user',label:`用户 (${userRows.length})`,children:userRows.length?renderGroup(userRows):<Empty description="无用户参数"/>},
          {key:'upstream',label:`上游 (${upRows.length})`,children:upRows.length?renderGroup(upRows):<Empty description="无上游参数"/>},
          {key:'default',label:`默认 (${defRows.length})`,children:defRows.length?renderGroup(defRows):<Empty description="无默认参数"/>},
          {key:'output',label:'输出',children:n.type==='EPISODE_OVERVIEW'?<EpisodeAssetsOutput episodeNumber={Number(config.episode_number)||1} autoRefresh={config.auto_refresh!==false}/>:<NodeAssetsOutput nodeId={n.id} autoRefresh={Boolean(config.auto_refresh)}/>},
          {key:'history',label:'历史',children:<Empty description="暂无历史"/>},
        ]}
      />
    </aside>
  );
}
