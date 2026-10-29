import {Input,Typography} from 'antd';
import {useEffect,useState} from 'react';
import {useCatalog} from '../store/catalog';
const groups=[['TEXT','📝 文本'],['IMAGE','🖼 图片'],['VIDEO','🎬 视频'],['AUDIO','🎵 音频'],['CHARACTER','👤 角色'],['SCENE','🏞 场景']] as const;
export default function NodeLibrary(){
 const [q,setQ]=useState(''),catalog=useCatalog();useEffect(()=>{catalog.load().catch(()=>{})},[]);
 const defs=Object.values(catalog.defs).filter(d=>(d.label+d.type).toLowerCase().includes(q.toLowerCase()));
 return <aside className="node-library"><Typography.Title level={5}>节点库</Typography.Title><Input.Search placeholder="搜索节点" value={q} onChange={e=>setQ(e.target.value)}/>{groups.map(([key,title])=>{const nodes=defs.filter(d=>d.category===key);return nodes.length?<section key={key} className="library-group"><div className="library-group-title">{title}</div>{nodes.map(d=><div draggable key={d.type} className="library-item" onDragStart={e=>{e.dataTransfer.setData('node/type',d.type);e.dataTransfer.setData('node/label',d.label)}}>{d.label}<small>{d.type}</small></div>)}</section>:null})}</aside>
}
