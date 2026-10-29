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

const VIDEO_KEYS=new Set(['model','aspect_ratio','duration_sec','negative_prompt']);
const IMAGE_KEYS=new Set(['model','aspect_ratio','negative_prompt']);
const SIMPLE_KEYS:Record<string,Set<string>>={TEXT:new Set(['prompt']),CHARACTER:new Set(['prompt','name','description']),SCENE:new Set(['prompt','name','description']),AUDIO:new Set(['prompt'])};
const MODEL_LABELS:Record<string,string>={'video-free':'即梦通用（免费）','agnes-video-2.5-flash':'Agnes 2.5 Flash','agnes-video-v2.0':'Agnes 2.0','grok-imagine-video':'Grok Imagine','grok-imagine-video-1.5':'Grok Imagine 1.5'};

function Field({name,def,value,onChange}:{name:string;def?:CatalogParam;value:unknown;onChange:(v:unknown)=>void}){
  if(name==='duration_sec')return <InputNumber min={4} max={12} precision={0} addonAfter="秒" style={{width:'100%'}} value={Number(value??5)} onChange={onChange}/>;
  if(def?.type==='enum'&&def.options?.length)return <Select style={{width:'100%'}} value={value} options={def.options.map(x=>({value:x,label:name==='model'?(MODEL_LABELS[String(x)]??String(x)):String(x)}))} onChange={onChange}/>;
  if(def?.type==='int'||def?.type==='long'||def?.type==='decimal')return <InputNumber style={{width:'100%'}} value={value as number|undefined} onChange={onChange}/>;
  if(def?.type==='textarea'||name==='prompt'||name==='negative_prompt'||name==='description')return <Input.TextArea rows={name==='prompt'?4:2} value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;
  return <Input value={String(value??'')} onChange={e=>onChange(e.target.value)}/>;
}

export default function ParameterPanel(){
  const {projectId}=useParams(),queryClient=useQueryClient(),s=useWorkflow(),catalog=useCatalog();
  const n=s.nodes.find(x=>x.id===s.selectedId),{data,reload}=useNodeAssets(n?.id,false);
  if(!n)return <aside className="parameter-panel"><Empty description="点击画布中的节点查看属性"/></aside>;
  const def=catalog.defs[n.type??''],config=n.data.config??{},tool=String(config.tool??def?.params.tool?.defaultValue??'');
  const scoped=PARAM_TOOL_MAP[n.type??'']??{},allowed=n.type==='VIDEO'?VIDEO_KEYS:n.type==='IMAGE'?IMAGE_KEYS:(SIMPLE_KEYS[n.type??'']??new Set<string>());
  const keys=Object.keys(def?.params??{}).filter(k=>allowed.has(k)&&(!scoped[k]||scoped[k].includes(tool)));
  const assets=data?.assets??[],canUpload=['IMAGE','CHARACTER','SCENE','VIDEO','AUDIO'].includes(n.type??'');
  const accept=n.type==='VIDEO'?'video/*':n.type==='AUDIO'?'audio/*':'image/*',assetType=['CHARACTER','SCENE'].includes(n.type??'')?'IMAGE':n.type;
  const upload=async({file,onSuccess,onError}:UploadRequestOption)=>{try{const body=new FormData();body.append('file',file as File);body.append('assetType',assetType??'IMAGE');await api.post(`/projects/${projectId}/nodes/${n.id}/upload`,body);message.success('上传成功');await reload();await queryClient.invalidateQueries({queryKey:['node-assets',projectId,n.id]});onSuccess?.({})}catch(e){message.error('上传失败');onError?.(e as Error)}};
  return <aside className="parameter-panel">
    <Input value={n.data.name} onChange={e=>s.updateData(n.id,{name:e.target.value})}/>
    <Space style={{marginTop:8}}><Tag>{n.type}</Tag><Tag color={n.data.status==='FAILED'?'red':n.data.status==='RUNNING'?'blue':n.data.status==='SUCCEEDED'?'green':'default'}>{n.data.status||'PENDING'}</Tag></Space>
    <div className="parameter-preview">{assets.length?assets.slice(0,4).map(a=>isImageAsset(a.assetType)?<Image key={a.id} src={a.thumbnailUrl||a.previewUrl}/>:isVideoAsset(a.assetType)?<video key={a.id} controls src={a.playUrl}/>:<Typography.Paragraph key={a.id}>{a.contentText||a.name}</Typography.Paragraph>):<Empty description="暂无产出"/>}</div>
    <Space.Compact block>{canUpload&&<Upload accept={accept} showUploadList={false} customRequest={upload}><Button icon={<UploadOutlined/>} style={{width:'100%'}}>上传</Button></Upload>}<Button type="primary" style={{width:'100%'}} onClick={()=>s.openGenerate(n.id)}>✦ AI 生成</Button></Space.Compact>
    <Form layout="vertical" size="small" style={{marginTop:16}}>
      {def?.params.tool&&['VIDEO','IMAGE','CHARACTER','SCENE'].includes(n.type??'')&&<Form.Item label="工具"><Field name="tool" def={def.params.tool} value={tool} onChange={v=>s.updateConfig(n.id,'tool',v)}/></Form.Item>}
      {keys.map(k=><Form.Item key={k} label={paramLabel(k)}><Field name={k} def={def?.params[k]} value={config[k]??def?.params[k]?.defaultValue} onChange={v=>s.updateConfig(n.id,k,v)}/></Form.Item>)}
    </Form>
  </aside>;
}
