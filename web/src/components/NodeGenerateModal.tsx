import {useEffect,useMemo,useState} from 'react';
import {Button,Empty,Form,Image,Input,InputNumber,Modal,Segmented,Select,Slider,Space,Typography,Upload,message} from 'antd';
import {UploadOutlined} from '@ant-design/icons';
import type {UploadRequestOption} from 'rc-upload/lib/interface';
import {useQueryClient} from '@tanstack/react-query';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';
import {useWorkflow} from '../store/workflow';
import {useCatalog,paramLabel} from '../store/catalog';
import {PARAM_TOOL_MAP} from '../utils/paramToolMap';
import {useNodeAssets} from './NodeAssetsOutput';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';

const AI_VIDEO=new Set(['文生视频','图生视频']);
const CREATIVE=new Set(['negative_prompt','model','aspect_ratio','duration_sec']);
const MODEL_LABELS:Record<string,string>={'video-free':'即梦通用（免费）','agnes-video-2.5-flash':'Agnes 2.5 Flash','agnes-video-v2.0':'Agnes 2.0','grok-imagine-video':'Grok Imagine','grok-imagine-video-1.5':'Grok Imagine 1.5'};

export default function NodeGenerateModal(){
 const {projectId}=useParams(),s=useWorkflow(),catalog=useCatalog(),queryClient=useQueryClient();
 const node=s.nodes.find(n=>n.id===s.generateNodeId),type=node?.type??'',def=catalog.defs[type];
 const [tool,setTool]=useState(''),[prompt,setPrompt]=useState(''),[params,setParams]=useState<Record<string,unknown>>({}),[submitting,setSubmitting]=useState(false);
 const {data,reload}=useNodeAssets(node?.id,false);
 useEffect(()=>{if(!node)return;setTool(String(node.data.config.tool??def?.params.tool?.defaultValue??''));setPrompt(String(node.data.config.prompt??''));setParams({...node.data.config})},[node?.id,def]);
 const keys=useMemo(()=>Object.keys(PARAM_TOOL_MAP[type]??{}).filter(k=>CREATIVE.has(k)&&PARAM_TOOL_MAP[type][k]?.includes(tool)),[type,tool]);
 const canUpload=['IMAGE','CHARACTER','SCENE','VIDEO','AUDIO'].includes(type),canGenerate=type==='TEXT'||['IMAGE','CHARACTER','SCENE'].includes(type)||(type==='VIDEO'&&AI_VIDEO.has(tool));
 const assetType=['CHARACTER','SCENE'].includes(type)?'IMAGE':type,accept=type==='VIDEO'?'video/*':type==='AUDIO'?'audio/*':'image/*';
 const close=()=>s.openGenerate(undefined),error=(e:unknown)=>message.error((e as {response?:{data?:{message?:string}}})?.response?.data?.message||'操作失败');
 const refresh=async()=>{await reload();await queryClient.invalidateQueries({queryKey:['node-assets',projectId,node!.id]})};
 const upload=async({file,onSuccess,onError}:UploadRequestOption)=>{try{const body=new FormData();body.append('file',file as File);body.append('assetType',assetType);await api.post(`/projects/${projectId}/nodes/${node!.id}/upload`,body);message.success('上传成功');await refresh();onSuccess?.({})}catch(e){error(e);onError?.(e as Error)}};
 const poll=(runId:string,nodeId:string)=>{let attempts=0;const timer=window.setInterval(async()=>{if(++attempts>150){window.clearInterval(timer);setSubmitting(false);message.warning('生成超时，请稍后查看');return}try{const {data:runs}=await api.get(`/runs/${runId}/node-runs`);const current=(runs as {nodeId:string;status:string;errorMessage?:string}[]).find(x=>x.nodeId===nodeId);if(current&&['SUCCEEDED','FAILED','BLOCKED','CANCELED'].includes(current.status)){window.clearInterval(timer);setSubmitting(false);if(current.status==='SUCCEEDED'){message.success('生成完成');await reload();await queryClient.invalidateQueries({queryKey:['node-assets',projectId,nodeId]})}else message.error(current.errorMessage||'生成失败')}}catch{/* 网络抖动后继续轮询 */}},2000)};
 const generate=async()=>{if(!prompt.trim()){message.warning('请填写提示词');return}setSubmitting(true);const next={...params,prompt,tool};try{Object.entries(next).forEach(([k,v])=>s.updateConfig(node!.id,k,v));const {data:result}=await api.post(`/projects/${projectId}/nodes/${node!.id}/generate`,{prompt,tool,params:next});message.success('生成任务已提交');poll(result.runId,node!.id)}catch(e){setSubmitting(false);error(e)}};
 const outputs=data?.assets??[];
 const field=(k:string)=>{const value=params[k]??def?.params[k]?.defaultValue;if(k==='model')return <Select value={value} options={(def?.params.model?.options??[]).map(x=>({value:String(x),label:MODEL_LABELS[String(x)]??String(x)}))} onChange={v=>setParams(x=>({...x,model:v}))}/>;if(k==='aspect_ratio')return <Segmented block value={String(value??'9:16')} options={['9:16','16:9','1:1'].map(v=>({label:`▯ ${v}`,value:v}))} onChange={v=>setParams(x=>({...x,aspect_ratio:v}))}/>;if(k==='duration_sec')return <Space.Compact block><Slider min={4} max={12} value={Number(value??5)} style={{flex:1,marginInline:8}} onChange={v=>setParams(x=>({...x,duration_sec:v}))}/><InputNumber min={4} max={12} value={Number(value??5)} addonAfter="秒" onChange={v=>setParams(x=>({...x,duration_sec:v??5}))}/></Space.Compact>;return <Input.TextArea rows={2} value={String(value??'')} onChange={e=>setParams(x=>({...x,[k]:e.target.value}))}/>};
 return <Modal open={!!node} onCancel={close} footer={null} width={720} centered title={node?.data.name??''} destroyOnClose>
  <section className="generate-preview">{outputs.length?outputs.slice(0,6).map(a=>isImageAsset(a.assetType)?<Image key={a.id} src={a.thumbnailUrl||a.previewUrl}/>:isVideoAsset(a.assetType)?<video key={a.id} controls src={a.playUrl}/>:<Typography.Paragraph key={a.id}>{a.contentText||a.name}</Typography.Paragraph>):<Empty description="暂无当前产出"/>}</section>
  <Form layout="vertical" className="generate-actions">
   {def?.params.tool&&<Form.Item label="工具"><Select value={tool} options={(def.params.tool.options??[]).map(x=>({value:String(x),label:String(x)}))} onChange={setTool}/></Form.Item>}
   {keys.map(k=><Form.Item key={k} label={paramLabel(k)}>{field(k)}</Form.Item>)}
   {type!=='AUDIO'&&<Form.Item label="提示词"><Input.TextArea rows={4} value={prompt} onChange={e=>setPrompt(e.target.value)} placeholder="描述你想生成的内容"/></Form.Item>}
   <div className="generate-footer">{canUpload&&<Upload accept={accept} showUploadList={false} customRequest={upload}><Button icon={<UploadOutlined/>}>上传</Button></Upload>}{canGenerate&&<Button type="primary" loading={submitting} onClick={generate}>AI 生成</Button>}</div>
  </Form>
 </Modal>;
}
