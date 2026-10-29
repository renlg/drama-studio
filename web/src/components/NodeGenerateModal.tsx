import {useEffect,useMemo,useState} from 'react';
import {Button,Empty,Input,Segmented,Select,Slider,Upload,message} from 'antd';
import {UploadOutlined,CloseOutlined} from '@ant-design/icons';
import type {UploadRequestOption} from 'rc-upload/lib/interface';
import {useQueryClient} from '@tanstack/react-query';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';
import {useWorkflow} from '../store/workflow';
import {uploadAccept,assetTypeOfFile} from '../utils/uploadAccept';
import {useCatalog} from '../store/catalog';
import {PARAM_TOOL_MAP} from '../utils/paramToolMap';
import {useNodeAssets} from './NodeAssetsOutput';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';
import {listProviders,categoryOfNodeType,resolveSelection} from '../api/modelProviders';
import type {ModelProvider,ModelCategory} from '../api/modelProviders';

const AI_VIDEO=new Set(['文生视频','图生视频']);
const CREATIVE=new Set(['negative_prompt','model','aspect_ratio','duration_sec']);
const RATIOS=[
  {label:'9:16',value:'9:16',icon:'▯'},
  {label:'16:9',value:'16:9',icon:'▭'},
  {label:'1:1',value:'1:1',icon:'◻'},
];

export default function NodeGenerateModal(){
 const {projectId}=useParams(),s=useWorkflow(),catalog=useCatalog(),queryClient=useQueryClient();
 const node=s.nodes.find(n=>n.id===s.generateNodeId),type=node?.type??'',def=catalog.defs[type];
 const [tool,setTool]=useState(''),[prompt,setPrompt]=useState(''),[params,setParams]=useState<Record<string,unknown>>({});
 const [submitting,setSubmitting]=useState(false);
 const [providers,setProviders]=useState<ModelProvider[]>([]);
 const [providerId,setProviderId]=useState<string|undefined>();
 const [modelId,setModelId]=useState<string|undefined>();
 const {data,reload}=useNodeAssets(node?.id,false);

 useEffect(()=>{if(!node)return;
  setTool(String(node.data.config.tool??def?.params.tool?.defaultValue??''));
  setPrompt(String(node.data.config.prompt??''));
  setParams({...node.data.config});
  setProviderId(node.data.config.providerId?String(node.data.config.providerId):undefined);
  setModelId(node.data.config.model?String(node.data.config.model):undefined);
 },[node?.id,def]);

 // 供应商清单（只取启用的）
 useEffect(()=>{if(!node)return;let alive=true;
  listProviders(true).then(ps=>{if(alive)setProviders(ps)}).catch(()=>{if(alive)setProviders([])});
  return ()=>{alive=false};
 },[node?.id]);

 const category:ModelCategory=categoryOfNodeType(type);
 const sel=useMemo(()=>resolveSelection(providers,category,providerId,modelId),[providers,category,providerId,modelId]);
 const providerOptions=useMemo(()=>providers
   .filter(p=>p.enabled&&(p.models[category]??[]).some(m=>m.enabled))
   .map(p=>({value:p.id,label:p.name})),[providers,category]);
 const modelOptions=useMemo(()=>((sel.provider?.models[category])??[])
   .filter(m=>m.enabled)
   .map(m=>({value:m.modelId,label:m.displayName})),[sel.provider,category]);

 const keys=useMemo(()=>Object.keys(PARAM_TOOL_MAP[type]??{})
   .filter(k=>CREATIVE.has(k)&&k!=='model'&&PARAM_TOOL_MAP[type][k]?.includes(tool)),[type,tool]);
 const canUpload=['IMAGE','CHARACTER','SCENE','VIDEO','AUDIO'].includes(type);
 const canGenerate=type==='TEXT'||['IMAGE','CHARACTER','SCENE'].includes(type)||(type==='VIDEO'&&AI_VIDEO.has(tool));
 const accept=uploadAccept(type,tool);
 const close=()=>s.openGenerate(undefined);
 const error=(e:unknown)=>message.error((e as {response?:{data?:{message?:string}}})?.response?.data?.message||'操作失败');
 const refresh=async()=>{await reload();await queryClient.invalidateQueries({queryKey:['node-assets',projectId,node!.id]})};

 const upload=async({file,onSuccess,onError}:UploadRequestOption)=>{
  try{const body=new FormData();body.append('file',file as File);
   body.append('assetType',assetTypeOfFile(file as File));
   await api.post(`/projects/${projectId}/nodes/${node!.id}/upload`,body);
   message.success('上传成功');await refresh();onSuccess?.({})}
  catch(e){error(e);onError?.(e as Error)}
 };

 const poll=(runId:string,nodeId:string)=>{
  let attempts=0;
  const timer=window.setInterval(async()=>{
   if(++attempts>150){window.clearInterval(timer);setSubmitting(false);message.warning('生成超时，请稍后查看');return}
   try{const {data:runs}=await api.get(`/runs/${runId}/node-runs`);
    const current=(runs as {nodeId:string;status:string;errorMessage?:string}[]).find(x=>x.nodeId===nodeId);
    if(current&&['SUCCEEDED','FAILED','BLOCKED','CANCELED'].includes(current.status)){
     window.clearInterval(timer);setSubmitting(false);
     if(current.status==='SUCCEEDED'){message.success('生成完成');await refresh()}
     else message.error(current.errorMessage||'生成失败')
    }}catch{/* 网络抖动后继续轮询 */}
  },2000)
 };

 const generate=async()=>{
  if(!prompt.trim()){message.warning('请填写提示词');return}
  setSubmitting(true);
  const next:Record<string,unknown>={...params,prompt,tool,
    providerId:sel.provider?.id,model:sel.model?.modelId};
  try{
   Object.entries(next).forEach(([k,v])=>s.updateConfig(node!.id,k,v));
   const {data:result}=await api.post(`/projects/${projectId}/nodes/${node!.id}/generate`,{prompt,tool,params:next});
   message.success('生成任务已提交');poll(result.runId,node!.id)
  }catch(e){setSubmitting(false);error(e)}
 };

 const outputs=data?.assets??[];
 const showModelRow=type!=='TEXT'&&type!=='AUDIO'&&modelOptions.length>0;
 const showRatio=keys.includes('aspect_ratio'),showDuration=keys.includes('duration_sec');

 // @ 引用：读取当前节点的上游节点（连线来源），把它们作为参考素材
 const [refs,setRefs]=useState<{id:string;name:string;kind:'图片'|'视频'|'文本';preview?:string}[]>([]);
 useEffect(()=>{if(!node)return;
  const upstream=s.edges.filter(e=>e.target===node.id).map(e=>e.source);
  const list=s.nodes.filter(n=>upstream.includes(n.id)).map(n=>{
   const kind=n.data.nodeType==='TEXT'?'文本':n.data.nodeType==='VIDEO'?'视频':'图片';
   return {id:n.id,name:String(n.data.name||n.type),kind,preview:undefined};
  });
  setRefs(list as {id:string;name:string;kind:'图片'|'视频'|'文本';preview?:string}[]);
 },[node?.id,s.edges,s.nodes]);

 const insertRef=(r:{name:string;kind:string})=>{
  const token=`@${r.name}`;
  setPrompt(p=>p?`${p} ${token}`:token);
 };

 if(!node)return null;

 return <div className="jm-mask" onClick={close}>
  <div className="jm-modal" onClick={e=>e.stopPropagation()}>
   <header className="jm-head">
    <span className="jm-title">{node.data.name||type}</span>
    <button className="jm-close" onClick={close} aria-label="关闭"><CloseOutlined/></button>
   </header>

   <div className="jm-stage">
    {outputs.length
      ? <div className="jm-outputs">{outputs.slice(0,4).map(a=>
          isImageAsset(a.assetType)
            ? <img key={a.id} src={a.thumbnailUrl||a.previewUrl} alt={a.name||''}/>
            : isVideoAsset(a.assetType)
              ? <video key={a.id} controls src={a.playUrl}/>
              : <p key={a.id} className="jm-text-output">{a.contentText||a.name}</p>)}
        </div>
      : <div className="jm-stage-empty">
          <span className="jm-stage-icon">{type==='VIDEO'?'▷':type==='AUDIO'?'♪':'◫'}</span>
          <span className="jm-stage-hint">还没有产出，填写描述后生成</span>
        </div>}
   </div>

   {canUpload&&<div className="jm-upload-row">
     <Upload accept={accept} showUploadList={false} customRequest={upload} multiple={false}>
      <button className="jm-upload-btn"><UploadOutlined/> 上传素材</button>
     </Upload>
     <span className="jm-upload-hint">{type==='VIDEO'?'支持图片（首帧）或视频':'支持图片'}</span>
    </div>}

   {def?.params.tool&&(def.params.tool.options??[]).length>1&&<div className="jm-seg">
     <Segmented block value={tool} options={(def.params.tool.options??[]).map(x=>({value:String(x),label:String(x)}))} onChange={v=>setTool(String(v))}/>
    </div>}

   {showModelRow&&<div className="jm-row">
     <span className="jm-row-label">模型</span>
     <Select className="jm-provider" value={sel.provider?.id} options={providerOptions} placeholder="供应商"
       onChange={v=>{setProviderId(String(v));setModelId(undefined)}} popupMatchSelectWidth={false}/>
     <Select className="jm-model" value={sel.model?.modelId} options={modelOptions} placeholder="选择模型"
       onChange={v=>setModelId(String(v))} popupMatchSelectWidth={false}/>
    </div>}

   {showRatio&&<div className="jm-row">
     <span className="jm-row-label">比例</span>
     <div className="jm-ratios">{RATIOS.map(r=>
       <button key={r.value}
         className={'jm-ratio'+(String(params.aspect_ratio??'9:16')===r.value?' is-on':'')}
         onClick={()=>setParams(x=>({...x,aspect_ratio:r.value}))}>
         <span className="jm-ratio-icon">{r.icon}</span><span>{r.label}</span>
       </button>)}
     </div>
    </div>}

   {showDuration&&<div className="jm-row">
     <span className="jm-row-label">时长</span>
     <div className="jm-duration">
       <Slider min={4} max={12} value={Number(params.duration_sec??5)} tooltip={{open:false}}
         onChange={v=>setParams(x=>({...x,duration_sec:v}))}/>
       <span className="jm-duration-val">{Number(params.duration_sec??5)}s</span>
     </div>
    </div>}

   {refs.length>0&&<div className="jm-refs">
     <span className="jm-refs-label">引用上游</span>
     {refs.map(r=><button key={r.id} className="jm-ref" onClick={()=>insertRef(r)} title={`插入 @${r.name}`}>
       <span className="jm-ref-kind">{r.kind}</span>{r.name}
     </button>)}
    </div>}

   {type!=='AUDIO'&&<div className="jm-prompt">
     <Input.TextArea autoSize={{minRows:3,maxRows:6}} value={prompt} bordered={false}
       onChange={e=>setPrompt(e.target.value)}
       placeholder={type==='VIDEO'?'描述画面内容、镜头运动、光线氛围…':'描述你想生成的内容…'}/>
    </div>}

   <footer className="jm-foot">
    <span className="jm-foot-hint">{sel.provider?`${sel.provider.name} · ${sel.model?.displayName??'默认模型'}`:''}</span>
    {canGenerate&&<button className="jm-generate" disabled={submitting} onClick={generate}>
      {submitting?'生成中…':'生成'}
     </button>}
   </footer>
  </div>
 </div>;
}
