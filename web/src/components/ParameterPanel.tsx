import {useEffect,useMemo,useState} from 'react';
import {Button,Empty,Form,Image,Input,InputNumber,Select,Space,Tag,Typography,Upload,message} from 'antd';
import {UploadOutlined} from '@ant-design/icons';
import type {UploadRequestOption} from 'rc-upload/lib/interface';
import {useQueryClient} from '@tanstack/react-query';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';
import {useWorkflow} from '../store/workflow';
import {useCatalog,paramLabel} from '../store/catalog';
import type {CatalogParam} from '../store/catalog';
import {useNodeAssets} from './NodeAssetsOutput';
import {PARAM_TOOL_MAP} from '../utils/paramToolMap';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';
import {uploadAccept,assetTypeOfFile} from '../utils/uploadAccept';
import {listProviders,categoryOfNodeType,resolveSelection} from '../api/modelProviders';
import type {ModelProvider} from '../api/modelProviders';

const VIDEO_KEYS=new Set(['aspect_ratio','duration_sec','negative_prompt']);
const IMAGE_KEYS=new Set(['aspect_ratio','negative_prompt']);
const SIMPLE_KEYS:Record<string,Set<string>>={TEXT:new Set(['prompt']),CHARACTER:new Set(['prompt','name','description']),SCENE:new Set(['prompt','name','description']),AUDIO:new Set(['prompt'])};

function Field({name,def,value,onChange}:{name:string;def?:CatalogParam;value:unknown;onChange:(v:unknown)=>void}){
  if(name==='duration_sec')return <InputNumber min={4} max={12} precision={0} addonAfter="秒" style={{width:'100%'}} value={Number(value??5)} onChange={onChange}/>;
  if(def?.type==='enum'&&def.options?.length)return <Select style={{width:'100%'}} value={value} options={def.options.map(x=>({value:x,label:String(x)}))} onChange={onChange}/>;
  if(def?.type==='int'||def?.type==='long'||def?.type==='decimal')return <InputNumber style={{width:'100%'}} value={value as number|undefined} onChange={onChange}/>;
  if(def?.type==='textarea'||name==='prompt'||name==='negative_prompt'||name==='description')return <Input.TextArea rows={name==='prompt'?4:2} value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;
  return <Input value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;
}

export default function ParameterPanel(){
  const {projectId}=useParams(),queryClient=useQueryClient(),s=useWorkflow(),catalog=useCatalog();
  const n=s.nodes.find(x=>x.id===s.selectedId),{data,reload}=useNodeAssets(n?.id,false);
  const [providers,setProviders]=useState<ModelProvider[]>([]);
  useEffect(()=>{let alive=true;listProviders(true).then(ps=>{if(alive)setProviders(ps)}).catch(()=>{if(alive)setProviders([])});return()=>{alive=false}},[]);
  if(!n)return <aside className="parameter-panel"><Empty description="点击画布中的节点查看属性"/></aside>;
  const def=catalog.defs[n.type??''],config=n.data.config??{},tool=String(config.tool??def?.params.tool?.defaultValue??'');
  const scoped=PARAM_TOOL_MAP[n.type??'']??{},allowed=n.type==='VIDEO'?VIDEO_KEYS:n.type==='IMAGE'?IMAGE_KEYS:(SIMPLE_KEYS[n.type??'']??new Set<string>());
  const keys=Object.keys(def?.params??{}).filter(k=>allowed.has(k)&&(!scoped[k]||scoped[k].includes(tool)));
  const assets=data?.assets??[],canUpload=['IMAGE','CHARACTER','SCENE','VIDEO','AUDIO'].includes(n.type??'');
  const accept=uploadAccept(n.type??'',String(n.data.config.tool??''));
  const upload=async({file,onSuccess,onError}:UploadRequestOption)=>{try{const body=new FormData();body.append('file',file as File);body.append('assetType',assetTypeOfFile(file as File));await api.post(`/projects/${projectId}/nodes/${n.id}/upload`,body);message.success('上传成功');await reload();await queryClient.invalidateQueries({queryKey:['node-assets',projectId,n.id]});onSuccess?.({})}catch(e){message.error('上传失败');onError?.(e as Error)}};

  const category=categoryOfNodeType(n.type??'');
  const wantP=config.providerId?String(config.providerId):undefined;
  const wantM=config.model?String(config.model):undefined;
  const sel=resolveSelection(providers,category,wantP,wantM);
  const providerOptions=providers.filter(p=>p.enabled&&(p.models[category]??[]).some(m=>m.enabled)).map(p=>({value:p.id,label:p.name}));
  const modelOptions=((sel.provider?.models[category])??[]).filter(m=>m.enabled).map(m=>({value:m.modelId,label:m.displayName}));
  const showProvider=n.type!=='TEXT'&&n.type!=='AUDIO'&&modelOptions.length>0;

  return <aside className="parameter-panel">
    <Input value={n.data.name} onChange={e=>s.updateData(n.id,{name:e.target.value})}/>
    <Space style={{marginTop:8}}><Tag>{n.type}</Tag><Tag color={n.data.status==='FAILED'?'red':n.data.status==='RUNNING'?'blue':n.data.status==='SUCCEEDED'?'green':'default'}>{n.data.status||'PENDING'}</Tag></Space>
    <div className="parameter-preview">{assets.length?assets.slice(0,4).map(a=>isImageAsset(a.assetType)?<Image key={a.id} src={a.thumbnailUrl||a.previewUrl}/>:isVideoAsset(a.assetType)?<video key={a.id} controls src={a.playUrl}/>:<Typography.Paragraph key={a.id}>{a.contentText||a.name}</Typography.Paragraph>):<Empty description="暂无产出"/>}</div>
    <Space.Compact block>{canUpload&&<Upload accept={accept} showUploadList={false} customRequest={upload}><Button icon={<UploadOutlined/>} style={{width:'100%'}}>上传</Button></Upload>}<Button type="primary" style={{width:'100%'}} onClick={()=>s.openGenerate(n.id)}>✦ AI 生成</Button></Space.Compact>
    <Form layout="vertical" size="small" style={{marginTop:16}}>
      {def?.params.tool&&['VIDEO','IMAGE','CHARACTER','SCENE'].includes(n.type??'')&&<Form.Item label="工具"><Field name="tool" def={def.params.tool} value={tool} onChange={v=>s.updateConfig(n.id,'tool',v)}/></Form.Item>}
      {showProvider&&<Form.Item label="供应商">
        <Select value={sel.provider?.id} options={providerOptions} placeholder="选择供应商"
          onChange={v=>{s.updateConfig(n.id,'providerId',v);s.updateConfig(n.id,'model',undefined)}}/>
      </Form.Item>}
      {showProvider&&<Form.Item label="模型">
        <Select value={sel.model?.modelId} options={modelOptions} placeholder="选择模型"
          onChange={v=>s.updateConfig(n.id,'model',v)}/>
      </Form.Item>}
      {keys.map(k=><Form.Item key={k} label={paramLabel(k)}><Field name={k} def={def?.params[k]} value={config[k]??def?.params[k]?.defaultValue} onChange={v=>s.updateConfig(n.id,k,v)}/></Form.Item>)}
    </Form>
  </aside>;
}
