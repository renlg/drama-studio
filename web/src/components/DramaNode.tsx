import {useState} from 'react';
import {Handle,Position,type NodeProps} from 'reactflow';
import {Image,Modal,Progress,Tag,Upload,message} from 'antd';
import type {UploadRequestOption} from 'rc-upload/lib/interface';
import {useQueryClient} from '@tanstack/react-query';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';
import {useNodeAssets} from './NodeAssetsOutput';
import type {CanvasNodeData,NodeStatus} from '../types';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';
import {useWorkflow} from '../store/workflow';
import {useCatalog,type CatalogPort} from '../store/catalog';
import NodeAddHandle from './NodeAddHandle';
import {uploadAccept,canUploadNode,assetTypeOfFile} from '../utils/uploadAccept';

const icon:Record<string,string>={TEXT:'📝',IMAGE:'🖼',VIDEO:'🎬',AUDIO:'🎵',CHARACTER:'👤',SCENE:'🏞'};
const statusColor:Record<NodeStatus,string>={PENDING:'default',QUEUED:'default',RUNNING:'processing',SUCCEEDED:'success',FAILED:'error',CANCELED:'default',BLOCKED:'warning',STALE:'warning'};
const portLabels:Record<string,string>={text:'文本',refs:'参考素材',images:'图片',chars:'角色',scenes:'场景',audios:'音频',character:'角色',scene:'场景',video:'视频',input:'输入',output:'输出'};

function PortHandles({ports,kind}:{ports:CatalogPort[];kind:'input'|'output'}){const position=kind==='input'?Position.Left:Position.Right,type=kind==='input'?'target' as const:'source' as const;return <>{ports.map((port,index)=>{const top=`${((index+1)/(ports.length+1))*100}%`;return <div className={`node-port node-port-${kind}`} style={{top}} key={port.name}><span className="node-port-label">{portLabels[port.name]??port.name}</span><Handle type={type} id={port.name} position={position}/></div>})}</>}

function NodeMedia({nodeId,type}:{nodeId:string;type:string}){
  const {projectId}=useParams(),queryClient=useQueryClient(),openGenerate=useWorkflow(x=>x.openGenerate),tool=useWorkflow(x=>String(x.nodes.find(n=>n.id===nodeId)?.data?.config?.tool??'')),{data,reload}=useNodeAssets(nodeId,false),[videoOpen,setVideoOpen]=useState(false);
  const list=data?.assets??[],images=list.filter(a=>isImageAsset(a.assetType)&&(a.thumbnailUrl||a.previewUrl)),videos=list.filter(a=>isVideoAsset(a.assetType)&&a.playUrl),firstImage=images[0],firstVideo=videos[0];
  const canUpload=canUploadNode(type),accept=uploadAccept(type,tool);
  const upload=async({file,onSuccess,onError}:UploadRequestOption)=>{try{const body=new FormData();body.append('file',file as File);body.append('assetType',assetTypeOfFile(file as File));await api.post(`/projects/${projectId}/nodes/${nodeId}/upload`,body);await queryClient.invalidateQueries({queryKey:['node-assets',projectId,nodeId]});await reload();message.success('上传成功');onSuccess?.({})}catch(e){message.error('上传失败');onError?.(e as Error)}};
  const media=firstImage?<div className="node-media-image"><Image src={firstImage.thumbnailUrl||firstImage.previewUrl} preview={{src:firstImage.previewUrl||firstImage.thumbnailUrl}} alt={firstImage.name}/>{images.length>1&&<span className="media-count">+{images.length-1}</span>}</div>:firstVideo?<button className="node-video-poster" onClick={()=>setVideoOpen(true)}><video muted playsInline preload="metadata" src={firstVideo.playUrl}/><span className="video-play">▶</span>{videos.length>1&&<span className="media-count">+{videos.length-1}</span>}</button>:null;
  return <div className="node-media-frame nodrag nowheel" onClick={e=>e.stopPropagation()}>
    {media}
    {!media&&canUpload&&<Upload accept={accept} showUploadList={false} customRequest={upload}><div className="node-media-empty">＋ 上传</div></Upload>}
    {!media&&!canUpload&&<div className="node-media-empty">暂无产出</div>}
    <div className="node-media-actions">{canUpload&&<Upload accept={accept} showUploadList={false} customRequest={upload}><button>⤒ 上传</button></Upload>}<button onClick={()=>openGenerate(nodeId)}>✦ 生成</button></div>
    <Modal open={videoOpen} footer={null} onCancel={()=>setVideoOpen(false)} width={820} centered><video controls autoPlay src={firstVideo?.playUrl} style={{width:'100%'}}/></Modal>
  </div>;
}

export default function DramaNode({data,selected,id}:NodeProps<CanvasNodeData>){const def=useCatalog(x=>x.defs[String((data as CanvasNodeData&{nodeType?:string}).nodeType??'')]);const s=data.status||'PENDING',type=String((data as CanvasNodeData&{nodeType?:string}).nodeType??'');return <div className={`drama-node ${selected?'selected':''}`} data-status={s}><PortHandles ports={def?.inputs??[]} kind="input"/><div className="node-header"><span className="node-icon">{icon[type]||'◆'}</span><span className="node-title">{data.name}</span><Tag color={statusColor[s]}>{s}</Tag></div><div className="node-body"><div className="node-tool">{String(data.config.tool||'默认工具')}</div>{s==='RUNNING'&&<Progress percent={data.progress||0} size="small"/>}</div><NodeMedia nodeId={id} type={type}/><div className="node-footer">{type||'节点'} · {String(data.config.model||'自动模型')}</div><PortHandles ports={def?.outputs??[]} kind="output"/><NodeAddHandle nodeId={id} output={def?.outputs.find(Boolean)}/></div>}
