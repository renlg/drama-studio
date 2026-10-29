import {useCallback,useEffect,useRef,useState} from 'react';
import ReactFlow,{Background,BackgroundVariant,MiniMap,Panel,ReactFlowProvider,type Connection,type Edge,type Node,type ReactFlowInstance} from 'reactflow';
import dagre from '@dagrejs/dagre';
import {AutoComplete,Button,Dropdown,Input,Modal,Select,Space,Switch,Tooltip,Typography,message} from 'antd';
import {AimOutlined,AppstoreAddOutlined,CopyOutlined,SnippetsOutlined,CloudUploadOutlined,EyeInvisibleOutlined,ExpandOutlined,PlayCircleOutlined,RedoOutlined,SaveOutlined,SearchOutlined,UndoOutlined,DownloadOutlined,UploadOutlined,BorderOuterOutlined} from '@ant-design/icons';
import {useParams,useNavigate} from 'react-router-dom';
import {api} from '../api/client';
import NodeLibrary from '../components/NodeLibrary';
import ParameterPanel from '../components/ParameterPanel';
import DramaNode from '../components/DramaNode';
import NodeGenerateModal from '../components/NodeGenerateModal';
import {useWorkflow} from '../store/workflow';
import {useCatalog,defaultConfig} from '../store/catalog';
import {newId} from '../utils/uuid';
import type {CanvasNodeData} from '../types';
import {canConnect} from '../utils/portCompat';

const nodeTypes={default:DramaNode};
type Toolbox={id:string;name:string;nodes:Node<CanvasNodeData>[];edges:Edge[]};

function Canvas(){
 const {workflowId,projectId}=useParams(),nav=useNavigate(),s=useWorkflow(),cat=useCatalog();
 const rf=useRef<ReactFlowInstance>();
 const [addAt,setAddAt]=useState<{x:number;y:number}>(),[nodeQuery,setNodeQuery]=useState('');
 const [searchOpen,setSearchOpen]=useState(false),[search,setSearch]=useState(''),[searchType,setSearchType]=useState<string>();
 const [hideEdges,setHideEdges]=useState(false),[subjectsOnly,setSubjectsOnly]=useState(false),[snap,setSnap]=useState(false),[touchpad,setTouchpad]=useState(true),[zoom,setZoom]=useState(100);
 const [editorId,setEditorId]=useState<string>(),[editorValue,setEditorValue]=useState('');
 const [toolboxOpen,setToolboxOpen]=useState(false),[toolboxes,setToolboxes]=useState<Toolbox[]>([]),[toolboxName,setToolboxName]=useState('');
 useEffect(()=>{cat.load().catch(()=>{})},[]);
 useEffect(()=>{api.get(`/workflows/${workflowId}`).then(({data})=>s.load(data.canvas,data.revision))},[workflowId]);
 useEffect(()=>{if(s.viewport&&rf.current)rf.current.setViewport(s.viewport)},[s.viewport]);
 const save=useCallback(async()=>{const {data}=await api.put(`/workflows/${workflowId}`,{revision:s.revision,canvas:{schemaVersion:'1.0',viewport:rf.current?.getViewport(),nodes:s.nodes,edges:s.edges}});s.markSaved(data.revision)},[s.nodes,s.edges,s.revision,workflowId]);
 useEffect(()=>{if(!s.dirty)return;const t=setTimeout(()=>save().catch(()=>message.error('自动保存失败')),2000);return()=>clearTimeout(t)},[s.dirty,s.nodes,s.edges]);
 const run=async(mode='all')=>{if(mode!=='all'&&!s.selectedId){message.warning('请先选择目标节点');return}await save();const {data}=await api.post(`/workflows/${workflowId}/runs`,{mode,targetNodeIds:mode==='all'?[]:[s.selectedId]});message.success(`任务已提交 ${data.id}`)};
 const categories=Object.values(cat.defs).filter(d=>(d.label+d.type).toLowerCase().includes(nodeQuery.toLowerCase())).reduce<Record<string,typeof cat.defs[string][]>>((all,d)=>{(all[d.category]??=[]).push(d);return all},{});
 const addNode=(type:string)=>{const def=cat.defs[type];if(!def||!addAt)return;s.addNode({id:newId(),type,position:addAt,data:{name:def.label??type,config:defaultConfig(def)}});setAddAt(undefined);setNodeQuery('')};
 const connect=useCallback((connection:Connection)=>{const source=s.nodes.find(n=>n.id===connection.source),target=s.nodes.find(n=>n.id===connection.target);const sd=cat.defs[source?.type??''],td=cat.defs[target?.type??''];const outPorts=sd?.outputs??[],inPorts=td?.inputs??[];if(!outPorts.length||!inPorts.length)return;const output=outPorts.find(p=>p.name===connection.sourceHandle)??outPorts[0];const input=inPorts.find(p=>p.name===connection.targetHandle)??inPorts.find(p=>canConnect(output.type,p.type))??inPorts[0];if(!canConnect(output.type,input.type)){message.warning('端口类型不兼容');return}s.connect({...connection,sourceHandle:output.name,targetHandle:input.name})},[s.nodes,s.connect,cat.defs]);
 const arrange=()=>{if(s.nodes.some(n=>!n.width||n.width<=0||!n.height||n.height<=0)){message.warning('节点尚未完成尺寸测量，请稍后重试');return}const g=new dagre.graphlib.Graph().setDefaultEdgeLabel(()=>({}));g.setGraph({rankdir:'LR',ranksep:90,nodesep:50});s.nodes.forEach(n=>g.setNode(n.id,{width:n.width!,height:n.height!}));s.edges.forEach(e=>g.setEdge(e.source,e.target));dagre.layout(g);s.replaceNodes(s.nodes.map(n=>{const p=g.node(n.id);return{...n,position:{x:p.x-n.width!/2,y:p.y-n.height!/2}}}));setTimeout(()=>rf.current?.fitView({duration:350}),0)};
 const visibleIds=new Set(subjectsOnly?s.nodes.filter(n=>n.type==='CHARACTER'||n.type==='SCENE').map(n=>n.id):s.nodes.map(n=>n.id));
 const renderedNodes=s.nodes.map(n=>({...n,type:'default',data:{...n.data,nodeType:n.type},hidden:!visibleIds.has(n.id)}));
 const renderedEdges=(hideEdges?[]:s.edges).filter(e=>visibleIds.has(e.source)&&visibleIds.has(e.target));
 const matches=s.nodes.filter(n=>(!searchType||n.type===searchType)&&(n.data.name+' '+n.type+' '+JSON.stringify(n.data.config)).toLowerCase().includes(search.toLowerCase()));
 const openEditor=(node:Node<CanvasNodeData>)=>{if(node.data.nodeType!=='TEXT')return;setEditorId(node.id);setEditorValue(String(node.data.config.prompt??''))};
 const loadToolboxes=()=>api.get('/toolboxes').then(({data})=>setToolboxes(data));
 // 复制/粘贴：选中节点 + 内部连线
 const doCopy=()=>{const n=s.copySelection();if(n)message.success(`已复制 ${n} 个节点`);else message.warning('请先选择节点');};
 const doPaste=()=>{const n=s.pasteClipboard();if(n)message.success(`已粘贴 ${n} 个节点`);else message.warning('剪贴板为空');};
 // 导出画布为 JSON 文件
 const exportCanvas=()=>{const blob=new Blob([JSON.stringify({schemaVersion:'1.0',viewport:rf.current?.getViewport(),nodes:s.nodes,edges:s.edges},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`canvas-${workflowId??'export'}.json`;a.click();URL.revokeObjectURL(url);message.success('已导出画布 JSON')};
 // 导入 JSON 到当前画布
 const importRef=useRef<HTMLInputElement>(null);
 const importCanvas=async(file:File)=>{try{const text=await file.text();const data=JSON.parse(text);if(!Array.isArray(data.nodes))throw new Error('缺少 nodes 数组');const map=new Map<string,string>();data.nodes.forEach((n:Node<CanvasNodeData>)=>map.set(n.id,newId()));const nodes=data.nodes.map((n:Node<CanvasNodeData>)=>({...n,id:map.get(n.id)!,selected:false}));const edges=(Array.isArray(data.edges)?data.edges:[]).map((e:Edge)=>({...e,id:newId(),source:map.get(e.source)??e.source,target:map.get(e.target)??e.target}));s.replaceCanvas([...s.nodes,...nodes],[...s.edges,...edges]);message.success(`已导入 ${nodes.length} 个节点`);setTimeout(()=>rf.current?.fitView({duration:350}),0)}catch(err){message.error('导入失败：'+(err as Error).message)}};
 const saveToolbox=async()=>{const chosen=s.nodes.filter(n=>n.selected);if(chosen.length<1){message.warning('请先选择一个或多个节点');return}const ids=new Set(chosen.map(n=>n.id)),minX=Math.min(...chosen.map(n=>n.position.x)),minY=Math.min(...chosen.map(n=>n.position.y));const nodes=chosen.map(n=>({...n,position:{x:n.position.x-minX,y:n.position.y-minY},selected:false}));const edges=s.edges.filter(e=>ids.has(e.source)&&ids.has(e.target));await api.post('/toolboxes',{name:toolboxName,nodes,edges});setToolboxName('');await loadToolboxes();message.success('已添加到工具箱')};
 const applyToolbox=(box:Toolbox)=>{const origin=rf.current?.screenToFlowPosition({x:window.innerWidth/2,y:window.innerHeight/2})??{x:100,y:100};const ids=new Map<string,string>();box.nodes.forEach(n=>ids.set(n.id,newId()));const nodes=box.nodes.map(n=>({...n,id:ids.get(n.id)!,position:{x:origin.x+n.position.x,y:origin.y+n.position.y},selected:false}));const edges=box.edges.map(e=>({...e,id:newId(),source:ids.get(e.source)!,target:ids.get(e.target)!}));s.addGroup(nodes,edges);setToolboxOpen(false);message.success('工具箱已应用，请补充素材和提示词')};
 // 快捷键：Cmd/Ctrl+C 复制、+V 粘贴、+A 全选、+Z 撤销、+Shift+Z 重做、+S 保存
 useEffect(()=>{
  const onKey=(e:KeyboardEvent)=>{const el=e.target as HTMLElement;const typing=el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable);
   const mod=e.metaKey||e.ctrlKey;
   if(mod&&e.key.toLowerCase()==='s'){e.preventDefault();save().then(()=>message.success('已保存')).catch(()=>message.error('保存失败'));return}
   if(mod&&e.key.toLowerCase()==='z'){e.preventDefault();if(e.shiftKey)s.redo();else s.undo();return}
   if(mod&&e.key.toLowerCase()==='a'){e.preventDefault();s.selectAll();return}
   if(typing)return;
   if(mod&&e.key.toLowerCase()==='c'){e.preventDefault();doCopy();return}
   if(mod&&e.key.toLowerCase()==='v'){e.preventDefault();doPaste();return}
  };
  window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey);
 },[save,doCopy,doPaste,s]);
 return <div className="workflow-page">
  <div className="canvas-toolbar"><Space>
   <Button icon={<UndoOutlined/>} onClick={s.undo} disabled={!s.past.length} title="撤销 ⌘Z"/><Button icon={<RedoOutlined/>} onClick={s.redo} disabled={!s.future.length} title="重做 ⇧⌘Z"/>
   <Button icon={<CopyOutlined/>} onClick={doCopy} title="复制 ⌘C"/><Button icon={<SnippetsOutlined/>} onClick={doPaste} title="粘贴 ⌘V"/>
   <Button icon={<BorderOuterOutlined/>} onClick={s.selectAll} title="全选 ⌘A"/>
   <Button icon={<DownloadOutlined/>} onClick={exportCanvas} title="导出 JSON"/>
   <Button icon={<UploadOutlined/>} onClick={()=>importRef.current?.click()} title="导入 JSON"/>
   <input ref={importRef} type="file" accept="application/json,.json" style={{display:'none'}} onChange={e=>{const f=e.target.files?.[0];if(f)importCanvas(f);e.target.value=''}}/>
   <Button icon={<SaveOutlined/>} onClick={save}>保存</Button><Button icon={<CloudUploadOutlined/>} onClick={()=>api.post(`/workflows/${workflowId}/publish`).then(()=>message.success('已发布'))}>发布</Button>
   <Dropdown.Button type="primary" icon={<PlayCircleOutlined/>} onClick={()=>run('all')} menu={{onClick:({key})=>run(key==='rerun'?'from':key),items:[{key:'all',label:'运行全部'},{key:'to',label:'运行到此节点'},{key:'only',label:'仅运行此节点'},{key:'rerun',label:'从此节点重跑'}]}}>运行</Dropdown.Button>
   <Button icon={<SearchOutlined/>} onClick={()=>setSearchOpen(true)}>搜索</Button><Button icon={<AppstoreAddOutlined/>} onClick={()=>{setToolboxOpen(true);loadToolboxes()}}>工具箱</Button>
   <Button onClick={()=>nav(`/projects/${projectId}/assets`)}>资产库</Button>
  </Space><span>{s.dirty?'有未保存修改':'已同步'}</span></div>
  <div className="canvas-body"><NodeLibrary/><div className="flow" onDoubleClick={e=>{if(!(e.target as HTMLElement).classList.contains('react-flow__pane')||!rf.current)return;setAddAt(rf.current.screenToFlowPosition({x:e.clientX,y:e.clientY}))}} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const type=e.dataTransfer.getData('node/type'),def=cat.defs[type];if(!def||!rf.current)return message.error('节点类型未加载，请刷新页面重试');const p=rf.current.screenToFlowPosition({x:e.clientX,y:e.clientY});s.addNode({id:newId(),type,position:p,data:{name:def.label??type,config:defaultConfig(def)}})}}>
   <ReactFlow nodes={renderedNodes} edges={renderedEdges} nodeTypes={nodeTypes} onInit={x=>{rf.current=x;if(s.viewport)x.setViewport(s.viewport);else x.fitView()}} onMove={(_,v)=>setZoom(Math.round(v.zoom*100))} onNodesChange={s.changeNodes} onEdgesChange={s.changeEdges} onConnect={connect} onNodeClick={(_,n)=>{s.select(n.id);s.openGenerate(n.id)}} onNodeDoubleClick={(_,n)=>openEditor(n as Node<CanvasNodeData>)} {...(!s.viewport?{fitView:true}:{})} minZoom={.05} maxZoom={4} panOnScroll={touchpad} zoomOnScroll={!touchpad} zoomOnPinch snapToGrid={snap} snapGrid={[20,20]} zoomOnDoubleClick={false} selectionOnDrag panOnDrag deleteKeyCode={['Delete','Backspace']} multiSelectionKeyCode="Shift">
    <MiniMap pannable zoomable className="dark-minimap" nodeColor="var(--ds-node-border)" nodeStrokeColor="var(--ds-node-border-hover)" maskColor="var(--ds-minimap-mask)"/>
    <Panel position="bottom-left" className="viewport-controls"><Space size={4} wrap>
     <Tooltip title="重置视角"><Button size="small" icon={<ExpandOutlined/>} onClick={()=>rf.current?.fitView({duration:300})}/></Tooltip>
     <Tooltip title="隐藏连线"><Button size="small" type={hideEdges?'primary':'default'} icon={<EyeInvisibleOutlined/>} onClick={()=>setHideEdges(x=>!x)}/></Tooltip>
     <Button size="small" onClick={arrange}>整理画布</Button><Button size="small" type={subjectsOnly?'primary':'default'} onClick={()=>setSubjectsOnly(x=>!x)}>只看角色/场景</Button>
     <span>吸附 <Switch size="small" checked={snap} onChange={setSnap}/></span><Button size="small" onClick={()=>setTouchpad(x=>!x)}>{touchpad?'触控板模式':'鼠标模式'}</Button><span className="zoom-label">{zoom}%</span>
    </Space></Panel>
    <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="var(--ds-canvas-dot)"/>
   </ReactFlow>
  </div><ParameterPanel/></div><div className="run-log">通用 AI 创作画布 · 状态变化通过 SSE 实时推送 · Shift 多选，Delete / Backspace 删除</div>
  <Modal title="添加节点" open={!!addAt} footer={null} onCancel={()=>{setAddAt(undefined);setNodeQuery('')}}><Input.Search autoFocus placeholder="搜索节点名称或类型" value={nodeQuery} onChange={e=>setNodeQuery(e.target.value)} style={{marginBottom:16}}/>{Object.entries(categories).map(([category,defs])=><div key={category} style={{marginBottom:16}}><Typography.Text strong>{category}</Typography.Text><div className="node-picker">{defs.map(def=><Button key={def.type} onClick={()=>addNode(def.type)}>{def.label}</Button>)}</div></div>)}</Modal>
  <Modal title="搜索节点" open={searchOpen} footer={null} onCancel={()=>setSearchOpen(false)}><Space.Compact style={{width:'100%',marginBottom:12}}><Input value={search} onChange={e=>setSearch(e.target.value)} placeholder="按名称或内容搜索"/><Select allowClear value={searchType} onChange={setSearchType} placeholder="类型" style={{width:180}} options={Object.values(cat.defs).map(d=>({value:d.type,label:d.label}))}/></Space.Compact><div className="search-results">{matches.map(n=><Button block key={n.id} icon={<AimOutlined/>} onClick={()=>{rf.current?.setCenter(n.position.x+(n.width||210)/2,n.position.y+(n.height||100)/2,{zoom:1.2,duration:350});s.select(n.id);setSearchOpen(false)}}>{n.data.name} · {cat.defs[n.type??'']?.label}</Button>)}</div></Modal>
  <Modal title="放大编辑文本" open={!!editorId} onCancel={()=>setEditorId(undefined)} onOk={()=>{if(editorId)s.updateConfig(editorId,'prompt',editorValue);setEditorId(undefined)}} width="80vw"><Input.TextArea value={editorValue} onChange={e=>setEditorValue(e.target.value)} style={{height:'80vh',resize:'none'}}/></Modal>
  <Modal title="工具箱" open={toolboxOpen} footer={null} onCancel={()=>setToolboxOpen(false)}><Typography.Text>Shift 多选节点后保存为可复用组合</Typography.Text><Space.Compact style={{width:'100%',margin:'12px 0'}}><Input value={toolboxName} onChange={e=>setToolboxName(e.target.value)} placeholder="工具箱名称"/><Button type="primary" disabled={!toolboxName.trim()} onClick={saveToolbox}>添加到工具箱</Button></Space.Compact><div className="toolbox-list">{toolboxes.map(box=><Button block key={box.id} onClick={()=>applyToolbox(box)}>{box.name} · {box.nodes.length} 个节点</Button>)}</div></Modal>
  <NodeGenerateModal/>
 </div>
}
export default function WorkflowPage(){return <ReactFlowProvider><Canvas/></ReactFlowProvider>}
