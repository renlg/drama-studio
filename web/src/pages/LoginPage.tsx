import { Button, Card, Typography } from 'antd';
import { BASE } from '../auth';

const { Title, Paragraph } = Typography;

/** 登录页：只有一个按钮，点击跳后端 /auth/login 走 ai-connect OAuth2 授权码流程 */
export default function LoginPage() {
  const go = () => {
    location.href = BASE + '/auth/login';
  };

  return (
    <div className="login-wrap">
      <Card className="login-card">
        <Title level={2} style={{ marginBottom: 8 }}>
          鲸剧 · Drama Studio
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 28 }}>
          AI 短剧生成平台 · 使用 ai-connect 账号登录
        </Paragraph>
        <Button type="primary" size="large" block onClick={go}>
          使用 ai-connect 登录
        </Button>
      </Card>
    </div>
  );
}
