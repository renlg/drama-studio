import {ColorPicker,Empty,Form,Input,InputNumber,Select,Space,Switch,Tabs,Typography,Tag} from 'antd';
import {useWorkflow} from '../store/workflow';
import {useCatalog,paramLabel} from '../store/catalog';
import type {CatalogDef,CatalogParam} from '../store/catalog';
import {EpisodeAssetsOutput} from './EpisodeOverviewNode';
import NodeAssetsOutput from './NodeAssetsOutput';

type Row=[string,CatalogParam|undefined,unknown];

const PARAM_TOOL_MAP:Record<string,Record<string,string[]>>={
  TEXT:{prompt:['生成','扩写','润色','改写','续写'],system_prompt:['生成','扩写','润色','改写','续写'],negative_prompt:['生成','扩写','润色','改写','续写'],temperature:['生成','扩写','润色','改写','续写'],max_tokens:['生成','扩写','润色','改写','续写'],output_format:['生成','扩写','润色','改写','续写']},
  IMAGE:{prompt:['文生图','图生图','宫格生成'],negative_prompt:['文生图','图生图','宫格生成'],width:['文生图','图生图','裁剪','宫格生成'],height:['文生图','图生图','裁剪','宫格生成'],seed:['文生图','图生图','宫格生成'],steps:['文生图','图生图','宫格生成'],cfg_scale:['文生图','图生图','宫格生成'],image_strength:['图生图'],output_count:['文生图','图生图'],key_color:['抠图'],similarity:['抠图'],blend:['抠图'],output_format:['抠图'],x:['裁剪'],y:['裁剪'],angle:['旋转'],rows:['宫格切分','宫格生成'],cols:['宫格切分','宫格生成'],grid_type:['宫格生成']},
  VIDEO:{prompt:['文生视频','图生视频','智能多帧'],negative_prompt:['文生视频','图生视频'],mode:['文生视频'],duration_sec:['文生视频','图生视频'],resolution:['文生视频','图生视频'],aspect_ratio:['文生视频','智能多帧'],first_frame_url:['图生视频'],seed:['文生视频'],camera_movement:['图生视频'],output_count:['图生视频'],shot_duration_sec:['智能多帧'],max_shots:['智能多帧'],transition:['拼接'],transition_duration:['拼接'],reencode:['拼接','截取'],output_name:['拼接'],start_sec:['截取'],end_sec:['截取'],angle:['旋转'],reverse_audio:['倒放'],speed:['倍速'],fps:['补帧'],interpolation_mode:['补帧'],width:['分辨率调整'],height:['分辨率调整'],fit:['分辨率调整'],pad_color:['分辨率调整'],extract_mode:['抽帧'],at_sec:['抽帧'],every_n_sec:['抽帧'],max_frames:['抽帧'],x:['去字幕'],y:['去字幕'],w:['去字幕'],h:['去字幕'],format:['音频分离'],audio_volume:['配音配乐'],keep_original_audio:['配音配乐'],audio_offset_sec:['配音配乐'],shortest:['配音配乐']},
  AUDIO:{files:['上传'],description:['上传'],start_sec:['截取'],end_sec:['截取'],volume:['音量调整']},
  CHARACTER:{name:['生成','上传'],appearance:['生成'],description:['生成','上传'],voice_desc:['生成','上传'],prompt:['生成'],files:['上传']},
  SCENE:{name:['生成','上传'],view_name:['生成'],description:['生成','上传'],scene_group:['生成','上传'],prompt:['生成'],files:['上传']},
};

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
  const currentTool=String(config.tool??def?.params.tool?.defaultValue??'');
  const scoped=PARAM_TOOL_MAP[n.type??'']??{};
  const rows:Row[]=buildRows(def,config).filter(([key])=>key==='tool'||!scoped[key]||scoped[key].includes(currentTool));

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
      <Space style={{justifyContent:'space-between',width:'100%'}}><Typography.Title level={4} style={{marginBottom:4}}>{n.data.name}</Typography.Title><ColorPicker value={n.data.colorTag||'var(--ds-text-muted)'} presets={[{label:'标签',colors:['var(--ds-error)','var(--ds-warning)','var(--ds-success)','var(--ds-running)','var(--ds-accent)','var(--ds-text-muted)']}]} onChange={(_,hex)=>s.updateData(n.id,{colorTag:hex})}/></Space>
      <Typography.Text type="secondary" style={{fontSize:12}}>{n.type} · 共 {rows.length} 个参数</Typography.Text>
      {def?.params.tool&&<div className="tool-selector"><Typography.Text>当前工具</Typography.Text><Field def={def.params.tool} value={currentTool} onChange={x=>s.updateConfig(n.id,'tool',x)}/></div>}
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
