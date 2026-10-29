import {Handle,Position,type NodeProps} from 'reactflow';
import {Progress,Tag} from 'antd';
import {useNodeAssets} from './NodeAssetsOutput';
import type {CanvasNodeData,NodeStatus} from '../types';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';

const icon:Record<string,string>={TEXT:'📝',IMAGE:'🖼',VIDEO:'🎬',AUDIO:'🎵',CHARACTER:'👤',SCENE:'🏞'};
const statusColor:Record<NodeStatus,string>={PENDING:'default',QUEUED:'default',RUNNING:'processing',SUCCEEDED:'success',FAILED:'error',CANCELED:'default',BLOCKED:'warning',STALE:'warning'};

function NodeOutputPreview({nodeId}:{nodeId:string}){
  const {data}=useNodeAssets(nodeId,false),list=data?.assets??[];
  if(!list.length)return null;
  const images=list.filter(a=>isImageAsset(a.assetType)&&(a.thumbnailUrl||a.previewUrl));
  const videos=list.filter(a=>isVideoAsset(a.assetType)&&a.playUrl),text=list.find(a=>!isImageAsset(a.assetType)&&!isVideoAsset(a.assetType));
  return <div className="node-output nodrag">
    {!!images.length&&<div className={images.length===1?'node-media-grid node-media-single':'node-media-grid'}>{images.slice(0,4).map((a,index)=><div className="node-media-item" key={a.id} onClick={()=>window.open(a.previewUrl||a.thumbnailUrl,'_blank','noopener,noreferrer')}><img src={a.thumbnailUrl||a.previewUrl} alt={a.name||''}/>{index===3&&images.length>4&&<span className="media-more-overlay">+{images.length-4}</span>}</div>)}</div>}
    {!!videos.length&&<div className="node-media-video nodrag nowheel"><video controls muted playsInline preload="metadata" src={videos[0].playUrl}/>{videos.length>1&&<span className="media-count">+{videos.length-1}</span>}</div>}
    {text?.contentText&&<div className="node-text-preview">{text.contentText.slice(0,80)}</div>}
  </div>;
}

export default function DramaNode({data,selected,id}:NodeProps<CanvasNodeData>){
  const s=data.status||'PENDING',type=String((data as CanvasNodeData&{nodeType?:string}).nodeType??'');
  const hasOutput=s!=='PENDING'&&s!=='QUEUED';
  return <div className={`drama-node ${selected?'selected':''}`} data-status={s}>
    <Handle type="target" position={Position.Left}/>
    <div className="node-header"><span className="node-icon">{icon[type]||'◆'}</span><span className="node-title">{data.name}</span><Tag color={statusColor[s]}>{s}</Tag></div>
    <div className="node-body"><div className="node-tool">{String(data.config.tool||'默认工具')}</div>{s==='RUNNING'&&<Progress percent={data.progress||0} size="small"/>}<div className="node-model">{String(data.config.provider||'AUTO')} · {String(data.config.model||'自动模型')}</div></div>
    {hasOutput&&<NodeOutputPreview nodeId={id}/>}<div className="node-footer">{type||'节点'} · {String(data.config.tool||'默认')}</div>
    <Handle type="source" position={Position.Right}/>
  </div>;
}
