import {useCallback,useEffect,useState} from 'react';
import {Badge,Collapse,Empty,Skeleton,Space,Tag,Typography} from 'antd';
import {Handle,Position,type NodeProps} from 'reactflow';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';
import type {CanvasNodeData,NodeStatus} from '../types';
import {isAudioAsset,isImageAsset,isTextAsset,isVideoAsset} from '../utils/assetTypes';
import './EpisodeOverviewNode.css';

export interface EpisodeAsset {id:string;assetType:string;name:string;contentStatus:string;isSelected:boolean;versionNo:number;previewUrl?:string;thumbnailUrl?:string;playUrl?:string;contentText?:string}
export interface EpisodeAssets {episodeNumber:number;groups:Record<string,EpisodeAsset[]>}
const statusColor:Record<NodeStatus,string>={PENDING:'#8c8c8c',QUEUED:'#8c8c8c',RUNNING:'#1677ff',SUCCEEDED:'#52c41a',FAILED:'#ff4d4f',CANCELED:'#8c8c8c',BLOCKED:'#faad14',STALE:'#fa8c16'};

export function useEpisodeAssets(episodeNumber:number,autoRefresh=true){
 const {projectId}=useParams();const [data,setData]=useState<EpisodeAssets>();const [loading,setLoading]=useState(true);const [failed,setFailed]=useState(false);
 const load=useCallback(async()=>{if(!projectId)return;try{const response=await api.get(`/projects/${projectId}/episodes/${episodeNumber}/assets`);setData(response.data);setFailed(false)}catch{setFailed(true)}finally{setLoading(false)}},[projectId,episodeNumber]);
 useEffect(()=>{setLoading(true);load();if(!autoRefresh)return;const timer=window.setInterval(load,10000);return()=>window.clearInterval(timer)},[load,autoRefresh]);
 return {data,loading,failed};
}

const collect=(data:EpisodeAssets|undefined,pred:(t:string)=>boolean)=>{const all=Object.values(data?.groups??{}).flat();return all.filter(a=>pred(a.assetType))};
export function EpisodeAssetsOutput({episodeNumber,autoRefresh=true}:{episodeNumber:number;autoRefresh?:boolean}){
 const {data,loading,failed}=useEpisodeAssets(episodeNumber,autoRefresh);if(loading)return <Skeleton active/>;if(failed)return <Empty description="分集产出加载失败"/>;
 const images=collect(data,isImageAsset),videos=collect(data,isVideoAsset),audios=collect(data,isAudioAsset),texts=collect(data,isTextAsset);
 if(!images.length&&!videos.length&&!audios.length&&!texts.length)return <Empty description={`第 ${episodeNumber} 集暂无产出`}/>;
 return <div className="episode-assets nodrag nowheel">
  {!!images.length&&<section><Typography.Title level={5}>图片</Typography.Title><div className="episode-image-grid">{images.map(a=><figure key={a.id}><img src={a.thumbnailUrl||a.previewUrl} alt={a.name}/><figcaption>{a.name} · v{a.versionNo}{a.isSelected?' · 已选':''}</figcaption></figure>)}</div></section>}
  {!!videos.length&&<section><Typography.Title level={5}>视频</Typography.Title>{videos.map(a=><div key={a.id} className="episode-video"><video controls preload="metadata" src={a.playUrl}/><Typography.Text>{a.name} · v{a.versionNo}</Typography.Text></div>)}</section>}
  {!!texts.length&&<section><Typography.Title level={5}>文本</Typography.Title><Collapse size="small" items={texts.map(a=>({key:a.id,label:`${a.name} · v${a.versionNo}`,children:<pre>{a.contentText}</pre>}))}/></section>}
 </div>;
}

export default function EpisodeOverviewNode({data,selected}:NodeProps<CanvasNodeData>){
 const episodeNumber=Number(data.config.episode_number)||1,autoRefresh=data.config.auto_refresh!==false;const {data:assets,loading}=useEpisodeAssets(episodeNumber,autoRefresh);const status=data.status||'PENDING';
 const images=collect(assets,isImageAsset).length,videos=collect(assets,isVideoAsset).length,texts=collect(assets,isTextAsset).length;
 return <div className={`drama-node episode-overview-node ${selected?'selected':''}`} style={{borderColor:statusColor[status]}}><Handle type="target" position={Position.Left}/><div className="node-title">第 {episodeNumber} 集 · 分集产出</div>{loading?<Skeleton active paragraph={false}/>:<Space size={14}><Badge count={images} showZero color="#1677ff"/><span>图</span><Badge count={videos} showZero color="#722ed1"/><span>视频</span><Badge count={texts} showZero color="#13c2c2"/><span>文本</span></Space>}<div className="node-model"><Tag color={statusColor[status]}>{status}</Tag>{autoRefresh?'自动刷新':'手动刷新'}</div><Handle type="source" position={Position.Right}/></div>;
}
