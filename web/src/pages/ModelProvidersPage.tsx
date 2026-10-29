import {useEffect,useState} from 'react';
import {Button,Empty,Form,Input,InputNumber,Modal,Popconfirm,Select,Space,Switch,Table,Tabs,Tag,Typography,message} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import {
  listProviders,createProvider,updateProvider,deleteProvider,setDefaultProvider,
  addModel,updateModel,deleteModel,setDefaultModel,testProvider,
} from '../api/modelProviders';
import type {ModelProvider,ProviderModel,ModelCategory} from '../api/modelProviders';

const CATS: {key: ModelCategory; label: string}[] = [
  {key:'TEXT',label:'文本'},
  {key:'IMAGE',label:'图片'},
  {key:'VIDEO',label:'视频'},
  {key:'AUDIO',label:'音频'},
];

export default function ModelProvidersPage(){
 const [providers,setProviders]=useState<ModelProvider[]>([]);
 const [loading,setLoading]=useState(false);
 const [activeId,setActiveId]=useState<string>('');
 const [cat,setCat]=useState<ModelCategory>('IMAGE');
 const [err,setErr]=useState<string>('');

 const load=async(keepId?:string)=>{
  setLoading(true);setErr('');
  try{
   const list=await listProviders(false);
   setProviders(list);
   setActiveId(prev=>keepId??(list.some(p=>p.id===prev)?prev:(list[0]?.id??'')))
  }catch(e){
   setErr((e as {response?:{data?:{message?:string}}})?.response?.data?.message||'加载失败（需管理员权限）')
  }finally{setLoading(false)}
 };
 useEffect(()=>{void load()},[]);

 const provider=providers.find(p=>p.id===activeId);
 const models=(provider?.models[cat]??[]);

 // ---- 供应商弹窗
 const [pOpen,setPOpen]=useState(false),[pEdit,setPEdit]=useState<ModelProvider|null>(null);
 const [pForm]=Form.useForm();
 const openProvider=(p?:ModelProvider)=>{
  setPEdit(p??null);
  pForm.setFieldsValue(p
    ?{name:p.name,baseUrl:p.baseUrl,apiKey:'',enabled:p.enabled,sortOrder:p.sortOrder}
    :{name:'',baseUrl:'',apiKey:'',enabled:true,sortOrder:0});
  setPOpen(true)
 };
 const saveProvider=async()=>{
  try{
   const v=await pForm.validateFields();
   if(pEdit)await updateProvider(pEdit.id,v);
   else await createProvider(v);
   message.success(pEdit?'已保存':'已新增');
   setPOpen(false);await load(pEdit?.id)
  }catch(e){
   if((e as {errorFields?:unknown}).errorFields)return;
   message.error((e as {response?:{data?:{message?:string}}})?.response?.data?.message||'保存失败')
  }
 };

 // ---- 模型弹窗
 const [mOpen,setMOpen]=useState(false),[mEdit,setMEdit]=useState<ProviderModel|null>(null);
 const [mForm]=Form.useForm();
 const openModel=(m?:ProviderModel)=>{
  setMEdit(m??null);
  mForm.setFieldsValue(m
    ?{modelId:m.modelId,displayName:m.displayName,enabled:m.enabled,sortOrder:m.sortOrder,extraJson:m.extraJson??''}
    :{modelId:'',displayName:'',enabled:true,sortOrder:models.length,extraJson:''});
  setMOpen(true)
 };
 const saveModel=async()=>{
  if(!provider)return;
  try{
   const v=await mForm.validateFields();
   if(mEdit)await updateModel(provider.id,mEdit.id,v);
   else await addModel(provider.id,{...v,category:cat});
   message.success(mEdit?'已保存':'已新增');
   setMOpen(false);await load(provider.id)
  }catch(e){
   if((e as {errorFields?:unknown}).errorFields)return;
   message.error((e as {response?:{data?:{message?:string}}})?.response?.data?.message||'保存失败')
  }
 };

 const columns=[
  {title:'显示名',dataIndex:'displayName',render:(v:string,r:ProviderModel)=><Space>
     <span>{v}</span>{r.isDefault&&<Tag color="blue">默认</Tag>}{!r.enabled&&<Tag>停用</Tag>}</Space>},
  {title:'模型 ID',dataIndex:'modelId',render:(v:string)=><Typography.Text code copyable>{v}</Typography.Text>},
  {title:'操作',width:220,render:(_:unknown,r:ProviderModel)=><Space size={4}>
     {!r.isDefault&&<Button size="small" type="link" onClick={async()=>{if(!provider)return;await setDefaultModel(provider.id,r.id);message.success('已设为默认');await load(provider.id)}}>设默认</Button>}
     <Button size="small" type="link" onClick={()=>openModel(r)}>编辑</Button>
     <Popconfirm title="删除该模型？" onConfirm={async()=>{if(!provider)return;await deleteModel(provider.id,r.id);message.success('已删除');await load(provider.id)}}>
       <Button size="small" type="link" danger>删除</Button>
     </Popconfirm>
   </Space>},
 ];

 return <div className="providers-page">
  <div className="providers-head">
   <div>
    <Typography.Title level={4} style={{margin:0}}>模型供应商</Typography.Title>
    <Typography.Text type="secondary">
      按供应商配置文本 / 图片 / 视频 / 音频模型，节点里可直接选择使用哪个供应商的哪个模型。
    </Typography.Text>
   </div>
   <Button type="primary" icon={<PlusOutlined/>} onClick={()=>openProvider()}>新增供应商</Button>
  </div>

  {err&&<Empty description={err}/>}

  <div className="providers-body">
   <aside className="providers-list">
    {providers.map(p=><button key={p.id}
      className={'provider-item'+(p.id===activeId?' is-on':'')}
      onClick={()=>setActiveId(p.id)}>
      <span className="provider-item-name">{p.name}</span>
      <span className="provider-item-meta">
        {p.isDefault&&<Tag color="blue">默认</Tag>}
        {!p.enabled&&<Tag>停用</Tag>}
        <span className="provider-item-count">{p.modelCount} 个模型</span>
      </span>
     </button>)}
    {!providers.length&&!loading&&!err&&<Empty description="还没有供应商" image={Empty.PRESENTED_IMAGE_SIMPLE}/>}
   </aside>

   <section className="providers-detail">
    {!provider?<Empty description="选择左侧供应商"/>:<>
      <div className="provider-info">
       <div className="provider-info-main">
        <Typography.Text strong style={{fontSize:15}}>{provider.name}</Typography.Text>
        <Space size={4} wrap>
          {provider.isDefault&&<Tag color="blue">默认供应商</Tag>}
          {provider.enabled?<Tag color="green">启用</Tag>:<Tag>停用</Tag>}
          <Tag color={provider.hasKey?'green':'orange'}>{provider.hasKey?`密钥 ${provider.keyMask}`:'未配置密钥'}</Tag>
        </Space>
       </div>
       <Typography.Text type="secondary" style={{fontSize:12}}>{provider.baseUrl}</Typography.Text>
       <Space wrap>
        <Button size="small" onClick={()=>openProvider(provider)}>编辑</Button>
        {!provider.isDefault&&<Button size="small" onClick={async()=>{await setDefaultProvider(provider.id);message.success('已设为默认');await load(provider.id)}}>设为默认</Button>}
        <Button size="small" onClick={async()=>{const r=await testProvider(provider.id);message.info(r.message)}}>测试配置</Button>
        <Popconfirm title="删除供应商及其全部模型？" onConfirm={async()=>{await deleteProvider(provider.id);message.success('已删除');await load()}}>
          <Button size="small" danger>删除</Button>
        </Popconfirm>
       </Space>
      </div>

      <Tabs activeKey={cat} onChange={k=>setCat(k as ModelCategory)}
        items={CATS.map(c=>({key:c.key,label:`${c.label} (${(provider.models[c.key]??[]).length})`}))}
        tabBarExtraContent={<Button size="small" type="primary" icon={<PlusOutlined/>} onClick={()=>openModel()}>新增{catsLabel(cat)}模型</Button>}/>

      <Table rowKey="id" size="small" pagination={false} dataSource={models} columns={columns}
        locale={{emptyText:<Empty description={`该供应商还没有${catsLabel(cat)}模型`} image={Empty.PRESENTED_IMAGE_SIMPLE}/>}}/>
     </>}
   </section>
  </div>

  <Modal open={pOpen} title={pEdit?'编辑供应商':'新增供应商'} onCancel={()=>setPOpen(false)} onOk={saveProvider} destroyOnClose>
   <Form form={pForm} layout="vertical" style={{marginTop:12}}>
    <Form.Item name="name" label="名称" rules={[{required:true,message:'请输入名称'}]}><Input placeholder="如 ai-connect / 火山方舟"/></Form.Item>
    <Form.Item name="baseUrl" label="Base URL" rules={[{required:true,message:'请输入 Base URL'}]}
      extra="OpenAI 兼容地址，通常以 /v1 结尾"><Input placeholder="https://api.example.com/v1"/></Form.Item>
    <Form.Item name="apiKey" label="API Key"
      extra={pEdit?'留空表示不修改；填入则覆盖':'留空则回落到系统默认密钥'}>
      <Input.Password placeholder="sk-..." autoComplete="new-password"/>
    </Form.Item>
    <Space size={16}>
     <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch/></Form.Item>
     <Form.Item name="sortOrder" label="排序"><InputNumber min={0}/></Form.Item>
    </Space>
   </Form>
  </Modal>

  <Modal open={mOpen} title={mEdit?'编辑模型':`新增${catsLabel(cat)}模型`} onCancel={()=>setMOpen(false)} onOk={saveModel} destroyOnClose>
   <Form form={mForm} layout="vertical" style={{marginTop:12}}>
    <Form.Item name="modelId" label="模型 ID" rules={[{required:true,message:'请输入模型 ID'}]}
      extra="上游真实模型名，如 doubao-seedream-5-0-pro"><Input placeholder="model-id"/></Form.Item>
    <Form.Item name="displayName" label="显示名" extra="留空则用模型 ID"><Input placeholder="如 即梦 Seedream 5 Pro"/></Form.Item>
    <Space size={16}>
     <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch/></Form.Item>
     <Form.Item name="sortOrder" label="排序"><InputNumber min={0}/></Form.Item>
    </Space>
    <Form.Item name="extraJson" label="附加配置（可选）" extra="JSON 字符串，透传给上游的特殊参数">
      <Input.TextArea rows={2} placeholder='{"unverified":true}'/>
    </Form.Item>
   </Form>
  </Modal>
 </div>;
}

function catsLabel(c: ModelCategory){return CATS.find(x=>x.key===c)?.label??c}
