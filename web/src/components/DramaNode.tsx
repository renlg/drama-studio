import {Handle,Position,type NodeProps} from 'reactflow';
import {Progress,Tag} from 'antd';
import {useParams} from 'react-router-dom';
import {useNodeAssets} from './NodeAssetsOutput';
import type {CanvasNodeData,NodeStatus} from '../types';

const color:Record<NodeStatus,string>={PENDING:'#8c8c8c',QUEUED:'#8c8c8c',RUNNING:'#1677ff',SUCCEEDED:'#52c41a',FAILED:'#ff4d4f',CANCELED:'#8c8c8c',BLOCKED:'#faad14',STALE:'#fa8c16'};

const isImage=(t:string)=>t==='STORYBOARD_IMAGE'||t==='CHARACTER_REFERENCE_IMAGE';
const isVideo=(t:string)=>t==='SHOT_VIDEO';

/** 节点卡片上的产出摘要：缩略图 + 文本首行 */
function NodeOutputPreview({nodeId}:{nodeId:string}){
  const {data}=useNodeAssets(nodeId,false);
  const list=data?.assets??[];
  if(!list.length)return null;
  const images=list.filter(a=>isImage(a.assetType));
  const videos=list.filter(a=>isVideo(a.assetType));
  const text=list.find(a=>!isImage(a.assetType)&&!isVideo(a.assetType));

  return <div className="node-output nodrag" style={{marginTop:6,borderTop:'1px solid #303030',paddingTop:6}}>
    {!!images.length&&<div style={{display:'flex',gap:3,marginBottom:4}}>
      {images.slice(0,3).map(a=><img key={a.id} src={a.thumbnailUrl||a.previewUrl} alt=""
        style={{width:32,height:32,objectFit:'cover',borderRadius:3,background:'#1a1a1a'}}/>)}
      {images.length>3&&<span style={{fontSize:10,color:'#8c8c8c',alignSelf:'center'}}>+{images.length-3}</span>}
    </div>}
    {!!videos.length&&<div style={{fontSize:10,color:'#722ed1',marginBottom:2}}>▶ 视频 {videos.length}</div>}
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
    <Tag color={color[s]}>{s}</Tag>
    {s==='RUNNING'&&<Progress percent={data.progress||0} size="small"/>}
    <div className="node-model">{String(data.config.provider||'AUTO')} · {String(data.config.model||'自动模型')}</div>
    {hasOutput&&<NodeOutputPreview nodeId={id}/>}
    <Handle type="source" position={Position.Right}/>
  </div>;
}
