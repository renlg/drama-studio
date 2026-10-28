import {Handle,Position,type NodeProps} from 'reactflow';
import {Progress,Tag} from 'antd';
import {useNodeAssets} from './NodeAssetsOutput';
import type {CanvasNodeData,NodeStatus} from '../types';
import {isImageAsset,isVideoAsset} from '../utils/assetTypes';

const color:Record<NodeStatus,string>={PENDING:'#8c8c8c',QUEUED:'#8c8c8c',RUNNING:'#1677ff',SUCCEEDED:'#52c41a',FAILED:'#ff4d4f',CANCELED:'#8c8c8c',BLOCKED:'#faad14',STALE:'#fa8c16'};

/** 节点卡片上的产出摘要：缩略图 + 文本首行 */
function NodeOutputPreview({nodeId}:{nodeId:string}){
  const {data}=useNodeAssets(nodeId,false);
  const list=data?.assets??[];
  if(!list.length)return null;
  const images=list.filter(a=>isImageAsset(a.assetType)&&(a.thumbnailUrl||a.previewUrl));
  const videos=list.filter(a=>isVideoAsset(a.assetType)&&a.playUrl);
  const text=list.find(a=>!isImageAsset(a.assetType)&&!isVideoAsset(a.assetType));
  const visibleImages=images.slice(0,4);
  const video=videos[0];

  return <div className="node-output nodrag" style={{marginTop:6,borderTop:'1px solid #303030',paddingTop:6}}>
    {!!visibleImages.length&&<div className="nodrag" style={{display:'grid',gridTemplateColumns:'repeat(2, 1fr)',gap:6,marginBottom:video?6:4}}>
      {visibleImages.map((a,index)=>{
        const src=a.thumbnailUrl||a.previewUrl;
        const original=a.previewUrl||a.thumbnailUrl;
        return <div key={a.id} onClick={()=>window.open(original,'_blank','noopener,noreferrer')}
          style={{position:'relative',height:84,cursor:'pointer',overflow:'hidden',borderRadius:4,background:'#1a1a1a'}}>
          <img src={src} alt={a.name||''} style={{width:'100%',height:'100%',objectFit:'cover',display:'block'}}/>
          {index===3&&images.length>4&&<span style={{position:'absolute',inset:0,display:'flex',alignItems:'center',justifyContent:'center',background:'#0008',color:'#fff',fontSize:16,fontWeight:600}}>
            +{images.length-4}
          </span>}
        </div>;
      })}
    </div>}
    {video&&<div className="nodrag nowheel" style={{position:'relative',height:94,marginBottom:4,overflow:'hidden',borderRadius:4,background:'#000'}}>
      <video controls muted playsInline preload="metadata" src={video.playUrl}
        onClick={()=>window.open(video.playUrl,'_blank','noopener,noreferrer')}
        style={{width:'100%',height:94,objectFit:'cover',display:'block',cursor:'pointer'}}/>
      {videos.length>1&&<span style={{position:'absolute',top:4,right:4,padding:'1px 5px',borderRadius:8,background:'#000a',color:'#fff',fontSize:10,pointerEvents:'none'}}>
        +{videos.length-1}
      </span>}
    </div>}
    {text?.contentText&&<div style={{fontSize:10,color:'#a6a6a6',overflow:'hidden',textOverflow:'ellipsis',display:'-webkit-box',WebkitLineClamp:2,WebkitBoxOrient:'vertical'}}>
      {text.contentText.slice(0,80)}
    </div>}
  </div>;
}

export default function DramaNode({data,selected,id}:NodeProps<CanvasNodeData>){
  const s=data.status||'PENDING';
  // 只有跑过的节点才拉产出（PENDING 状态避免无谓请求）
  const hasOutput=s!=='PENDING'&&s!=='QUEUED';
  return <div className={`drama-node ${selected?'selected':''}`} style={{borderColor:color[s]}}>
    <Handle type="target" position={Position.Left}/>
    <div className="node-title">{data.name}</div>
    {data.name==='分集扇出'&&<Tag color="geekblue">×{data.instanceCount??Number(data.config.episode_count||0)} 集</Tag>}
    <Tag color={color[s]}>{s}</Tag>
    {s==='RUNNING'&&<Progress percent={data.progress||0} size="small"/>}
    <div className="node-model">{String(data.config.provider||'AUTO')} · {String(data.config.model||'自动模型')}</div>
    {hasOutput&&<NodeOutputPreview nodeId={id}/>}
    <Handle type="source" position={Position.Right}/>
  </div>;
}
