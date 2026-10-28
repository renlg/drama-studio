import {useCallback,useEffect,useRef,useState} from 'react';
import ReactFlow,{Background,BackgroundVariant,Controls,MiniMap,ReactFlowProvider,type ReactFlowInstance} from 'reactflow';
import {AutoComplete,Button,Dropdown,Input,Modal,Space,Typography,message} from 'antd';
import {CloudUploadOutlined,ExpandOutlined,PlayCircleOutlined,RedoOutlined,SaveOutlined,UndoOutlined} from '@ant-design/icons';
import {useParams,useNavigate} from 'react-router-dom';
import {api} from '../api/client';
import NodeLibrary from '../components/NodeLibrary';
import ParameterPanel from '../components/ParameterPanel';
import DramaNode from '../components/DramaNode';
import {useWorkflow} from '../store/workflow';
import {useCatalog,defaultConfig} from '../store/catalog';
import EpisodeOverviewNode from '../components/EpisodeOverviewNode';
import {newId} from '../utils/uuid';

const nodeTypes={default:DramaNode,episodeOverview:EpisodeOverviewNode};

function Canvas(){
 const {workflowId,projectId}=useParams(),nav=useNavigate(),s=useWorkflow(),cat=useCatalog();
 const rf=useRef<ReactFlowInstance>(),[find,setFind]=useState(''),[addAt,setAddAt]=useState<{x:number;y:number}>(),[nodeQuery,setNodeQuery]=useState('');
 useEffect(()=>{cat.load().catch(()=>{})},[]);
 useEffect(()=>{api.get(`/workflows/${workflowId}`).then(({data})=>s.load(data.canvas,data.revision))},[workflowId]);
 useEffect(()=>{if(s.viewport&&rf.current)rf.current.setViewport(s.viewport)},[s.viewport]);
 const save=useCallback(async()=>{const {data}=await api.put(`/workflows/${workflowId}`,{revision:s.revision,canvas:{schemaVersion:'1.0',viewport:rf.current?.getViewport(),nodes:s.nodes,edges:s.edges}});s.markSaved(data.revision)},[s.nodes,s.edges,s.revision,workflowId]);
 useEffect(()=>{if(!s.dirty)return;const t=setTimeout(()=>save().catch(()=>message.error('自动保存失败')),2000);return()=>clearTimeout(t)},[s.dirty,s.nodes,s.edges]);
 const run=async()=>{await save();const {data}=await api.post(`/workflows/${workflowId}/runs`,{});message.success(`任务已提交 ${data.id}`)};
 const options=s.nodes.filter(n=>(n.data.name+(n.type??'')).toLowerCase().includes(find.toLowerCase())).map(n=>({value:n.id,label:`${n.data.name} · ${n.type}`}));
 const categories=Object.values(cat.defs).filter(d=>(d.label+d.type).toLowerCase().includes(nodeQuery.toLowerCase())).reduce<Record<string,typeof cat.defs[string][]>>((all,d)=>{(all[d.category]??=[]).push(d);return all},{});
 const addNode=(type:string)=>{const def=cat.defs[type];if(!def||!addAt)return;s.addNode({id:newId(),type,position:addAt,data:{name:def.label??type,config:defaultConfig(def)}});setAddAt(undefined);setNodeQuery('')};
 return <div className="workflow-page">
  <div className="canvas-toolbar"><Space>
   <Button icon={<UndoOutlined/>} onClick={s.undo} disabled={!s.past.length}/><Button icon={<RedoOutlined/>} onClick={s.redo} disabled={!s.future.length}/>
   <Button icon={<SaveOutlined/>} onClick={save}>保存</Button><Button icon={<CloudUploadOutlined/>} onClick={()=>api.post(`/workflows/${workflowId}/publish`).then(()=>message.success('已发布'))}>发布</Button>
   <Button icon={<ExpandOutlined/>} onClick={()=>rf.current?.fitView({duration:300})}>适应全图</Button>
   <Dropdown.Button type="primary" icon={<PlayCircleOutlined/>} onClick={run} menu={{items:[{key:'all',label:'运行全部'},{key:'to',label:'运行到此节点'},{key:'only',label:'仅运行此节点'},{key:'rerun',label:'从此节点重跑'}]}}>运行</Dropdown.Button>
   <AutoComplete style={{width:220}} value={find} options={options} placeholder="定位节点名 / 类型" onSearch={setFind} onSelect={id=>{const node=s.nodes.find(n=>n.id===id);if(node){s.select(id);rf.current?.fitView({nodes:[node],duration:350,maxZoom:1.3})}}}/>
   <Button onClick={()=>nav(`/projects/${projectId}/assets`)}>资产库</Button>
  </Space><span>{s.dirty?'有未保存修改':'已同步'}</span></div>
  <div className="canvas-body"><NodeLibrary/><div className="flow" onDoubleClick={e=>{if(!(e.target as HTMLElement).classList.contains('react-flow__pane')||!rf.current)return;setAddAt(rf.current.screenToFlowPosition({x:e.clientX,y:e.clientY}))}} onDragOver={e=>e.preventDefault()} onDrop={e=>{
   e.preventDefault();
   try {
    const type=e.dataTransfer.getData('node/type');
    if(!type) return;
    const def=cat.defs[type];
    if(!def){message.error('节点类型未加载，请刷新页面重试');return;}
    const p=rf.current!.screenToFlowPosition({x:e.clientX,y:e.clientY});
    s.addNode({id:newId(),type,position:p,data:{name:def.label??type,config:defaultConfig(def)}});
   } catch(err) {
    console.error('add node failed',err);
    message.error('添加节点失败：'+(err instanceof Error?err.message:String(err)));
   }
  }}>
   <ReactFlow nodes={s.nodes.map(n=>({...n,type:n.type==='EPISODE_OVERVIEW'?'episodeOverview':'default'}))} edges={s.edges} nodeTypes={nodeTypes} onInit={x=>{rf.current=x;if(s.viewport)x.setViewport(s.viewport);else x.fitView()}} onNodesChange={s.changeNodes} onEdgesChange={s.changeEdges} onConnect={s.connect} onNodeClick={(_,n)=>s.select(n.id)} {...(!s.viewport?{fitView:true}:{})} minZoom={.05} maxZoom={4} panOnScroll zoomOnDoubleClick={false} selectionOnDrag={false} panOnDrag={true} deleteKeyCode={['Delete','Backspace']} multiSelectionKeyCode="Shift"><MiniMap pannable zoomable nodeColor={()=>'#4a5568'} maskColor="rgba(240,242,245,0.75)" style={{background:'#fafafa',border:'1px solid #e8e8e8',borderRadius:6}}/><Controls/><Background variant={BackgroundVariant.Dots} gap={20} size={1}/></ReactFlow>
  </div><ParameterPanel/></div><div className="run-log">运行日志 · 状态变化通过 SSE 实时推送，可在任务中心查看详情 · 选中节点后按 Delete 键删除</div>
  <Modal title="添加节点" open={!!addAt} footer={null} onCancel={()=>{setAddAt(undefined);setNodeQuery('')}}><Input.Search autoFocus placeholder="搜索节点名称或类型" value={nodeQuery} onChange={e=>setNodeQuery(e.target.value)} style={{marginBottom:16}}/>{Object.entries(categories).map(([category,defs])=><div key={category} style={{marginBottom:16}}><Typography.Text strong>{category}</Typography.Text><div style={{display:'grid',gridTemplateColumns:'repeat(2, minmax(0, 1fr))',gap:8,marginTop:8}}>{defs.map(def=><Button key={def.type} onClick={()=>addNode(def.type)} style={{height:'auto',padding:'8px 12px',textAlign:'left'}}><span>{def.label}<br/><Typography.Text type="secondary" style={{fontSize:11}}>{def.type}</Typography.Text></span></Button>)}</div></div>)}</Modal>
 </div>
}
export default function WorkflowPage(){return <ReactFlowProvider><Canvas/></ReactFlowProvider>}
