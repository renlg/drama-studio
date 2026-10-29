import React from 'react';
import ReactDOM from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {ConfigProvider,theme as antdTheme} from 'antd';
import zhCN from 'antd/locale/zh_CN';
import 'antd/dist/reset.css';
import 'reactflow/dist/style.css';
import './styles.css';
import App from './App';

/** AntD 5 深色主题令牌：黑底白字，与 styles.css 的 --ds-* 变量保持一致 */
const darkTheme = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    colorPrimary: '#7b9cff',
    colorBgBase: '#0d0e10',
    colorBgContainer: '#1a1c21',
    colorBgElevated: '#1a1c21',
    colorBgLayout: '#0d0e10',
    colorBgSpotlight: '#22252b',
    colorBorder: '#2a2d34',
    colorBorderSecondary: '#22252b',
    colorText: '#ffffff',
    colorTextSecondary: '#9aa0a9',
    colorTextTertiary: '#6b7079',
    colorTextQuaternary: '#5a5f68',
    colorFillSecondary: 'rgba(255,255,255,.08)',
    colorFillTertiary: 'rgba(255,255,255,.05)',
    colorFillQuaternary: 'rgba(255,255,255,.03)',
    borderRadius: 8,
    fontSize: 13,
    controlHeight: 32,
  },
  components: {
    Layout: {headerBg: '#1a1c21', bodyBg: '#0d0e10', siderBg: '#1a1c21'},
    Menu: {darkItemBg: 'transparent', itemBg: 'transparent', horizontalItemSelectedColor: '#7b9cff'},
    Card: {colorBgContainer: '#1a1c21'},
    Table: {headerBg: '#22252b', rowHoverBg: '#22252b', colorBgContainer: '#1a1c21'},
    Modal: {contentBg: '#1a1c21', headerBg: 'transparent'},
    Select: {optionSelectedBg: 'rgba(123,156,255,.15)'},
    Input: {colorBgContainer: '#22252b'},
    Button: {defaultBg: '#22252b'},
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={darkTheme}>
      <QueryClientProvider client={new QueryClient()}>
        <BrowserRouter basename="/drama-studio">
          <App/>
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  </React.StrictMode>
);
