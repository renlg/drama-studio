import {Dropdown,Tooltip,type MenuProps} from 'antd';
import {useReactFlow,type Node} from 'reactflow';
import {useCatalog,defaultConfig,type CatalogDef,type CatalogPort} from '../store/catalog';
import {useWorkflow} from '../store/workflow';
import type {CanvasNodeData} from '../types';
import {canConnect} from '../utils/portCompat';
import {newId} from '../utils/uuid';

const groups=[['TEXT','📝 文本'],['IMAGE','🖼 图片'],['VIDEO','🎬 视频'],['AUDIO','🎵 音频'],['CHARACTER','👤 角色'],['SCENE','🏞 场景']] as const;

function compatibleInput(def:CatalogDef,output:CatalogPort){
  const compatible=def.inputs.filter(p=>canConnect(output.type,p.type));
  return compatible.find(p=>p.required)??compatible[0];
}

export default function NodeAddHandle({nodeId,output}:{nodeId:string;output?:CatalogPort}){
  const catalog=useCatalog(),workflow=useWorkflow(),reactFlow=useReactFlow<CanvasNodeData>();
  const candidates=output?Object.values(catalog.defs).filter(def=>compatibleInput(def,output)):[];
  const createNext=(type:string)=>{
    if(!output)return;
    const source=reactFlow.getNode(nodeId) as Node<CanvasNodeData>|undefined;
    const def=catalog.defs[type],input=def&&compatibleInput(def,output);
    if(!source||!def||!input)return;
    let position={x:source.position.x+(source.width??220)+100,y:source.position.y};
    const flowElement=document.querySelector('.workflow-page .flow');
    if(flowElement){
      const bounds=flowElement.getBoundingClientRect(),screen=reactFlow.flowToScreenPosition(position);
      const estimatedRight=screen.x+220*reactFlow.getZoom();
      if(screen.x<bounds.left||estimatedRight>bounds.right||screen.y<bounds.top||screen.y>bounds.bottom-80){
        position=reactFlow.screenToFlowPosition({x:bounds.left+bounds.width*.68,y:bounds.top+bounds.height*.5});
      }
    }
    const id=newId();
    workflow.addNode({id,type,position,data:{name:def.label??type,config:defaultConfig(def)}});
    workflow.connect({source:nodeId,sourceHandle:output.name,target:id,targetHandle:input.name});
    workflow.changeNodes(useWorkflow.getState().nodes.map(node=>({type:'select',id:node.id,selected:node.id===id})));
    workflow.select(id);
  };
  const items:MenuProps['items']=groups.flatMap(([category,label])=>{
    const children=candidates.filter(def=>def.category===category).map(def=>({key:def.type,label:def.label,onClick:()=>createNext(def.type)}));
    return children.length?[{key:`group-${category}`,type:'group' as const,label,children}]:[];
  });
  const disabled=!items.length;
  const button=<button type="button" className="node-add-next nodrag" disabled={disabled} aria-label="添加下一个节点" onClick={e=>e.stopPropagation()}>+</button>;
  return <Tooltip title={disabled?'当前节点没有可连接的下一节点':undefined}>
    <span className="node-add-next-wrap nodrag" onClick={e=>e.stopPropagation()}>{disabled?button:<Dropdown trigger={['click']} menu={{items}} placement="bottomLeft">{button}</Dropdown>}</span>
  </Tooltip>;
}
