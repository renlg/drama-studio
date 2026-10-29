import {App as AntApp,Button,Layout,Menu,Typography} from 'antd';
import {ApiOutlined,AppstoreOutlined,BookOutlined,PlayCircleOutlined} from '@ant-design/icons';
import {Navigate,Route,Routes,useLocation,useNavigate} from 'react-router-dom';
import ProjectsPage from './pages/ProjectsPage';
import WorkflowPage from './pages/WorkflowPage';
import AssetsPage from './pages/AssetsPage';
import TasksPage from './pages/TasksPage';
import TemplatesPage from './pages/TemplatesPage';
import ModelProvidersPage from './pages/ModelProvidersPage';
import LoginPage from './pages/LoginPage';
import {BASE,clearToken,isLoggedIn,setToken} from './auth';

/** 需要登录才能访问的页面统一包一层；未登录直接跳 /login */
function RequireAuth({children}:{children:React.ReactNode}){
  const loc=useLocation();
  if(!isLoggedIn())return <Navigate to="/login" state={{from:loc.pathname}} replace/>;
  return <>{children}</>;
}

function Shell(){
  const nav=useNavigate(),loc=useLocation();
  const items=[
    ['/projects',<AppstoreOutlined/>, '项目'],
    ['/tasks',<PlayCircleOutlined/>, '任务'],
    ['/templates',<BookOutlined/>, '模板'],
    ['/model-providers',<ApiOutlined/>, '模型'],
  ].map(([key,icon,label])=>({key:key as string,icon,label}));

  return (
    <Layout className="app">
      <Layout.Header className="header">
        <Typography.Title level={3}>鲸剧 · Drama Studio</Typography.Title>
        <Menu theme="dark" mode="horizontal" selectedKeys={[loc.pathname]} items={items} onClick={e=>nav(e.key)}/>
        <Button onClick={()=>{clearToken();location.replace(BASE+'/login')}}>退出</Button>
      </Layout.Header>
      <Layout.Content>
        <Routes>
          <Route path="/projects" element={<ProjectsPage/>}/>
          <Route path="/projects/:projectId/workflows/:workflowId" element={<WorkflowPage/>}/>
          <Route path="/projects/:projectId/assets" element={<AssetsPage/>}/>
          <Route path="/tasks" element={<TasksPage/>}/>
          <Route path="/templates" element={<TemplatesPage/>}/>
          <Route path="/model-providers" element={<ModelProvidersPage/>}/>
          <Route path="*" element={<Navigate to="/projects" replace/>}/>
        </Routes>
      </Layout.Content>
    </Layout>
  );
}

/** OAuth 回调落地页：接住 ?token=xxx，存下后进项目列表 */
function AuthCallback(){
  const token=new URLSearchParams(location.search).get('token');
  if(token){
    setToken(token);
    location.replace(BASE+'/projects');
  }else{
    location.replace(BASE+'/login');
  }
  return <>正在登录…</>;
}

export default function App(){
  const loc=useLocation();
  const authed=isLoggedIn();

  // 回调页优先：必须在登录守卫之前处理，否则刚拿到 token 会被打回登录页
  if(loc.pathname==='/login-callback')return <AntApp><AuthCallback/></AntApp>;

  // 未登录 → 整站只显示登录页
  if(!authed)return <AntApp><LoginPage/></AntApp>;

  return <AntApp><Shell/></AntApp>;
}
