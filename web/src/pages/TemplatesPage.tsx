import { useQuery } from '@tanstack/react-query';
import { Card, Collapse, Empty, Space, Tabs, Tag, Typography } from 'antd';
import { api, unwrap } from '../api/client';

interface Template {
  id: string;
  name: string;
  nodeType: string;
  scope: string;
  status: string;
  definition: {
    version: string;
    sections: {
      key: string;
      valueType: string;
      defaultValue: unknown;
      editable: boolean;
    }[];
  };
}

export default function TemplatesPage() {
  const q = useQuery({
    queryKey: ['templates'],
    queryFn: () => unwrap<Template[]>(api.get('/templates')),
  });

  const built = (
    <Space direction="vertical" style={{ width: '100%' }}>
      {q.data?.map((t) => (
        <Card
          key={t.id}
          title={t.name}
          extra={
            <>
              <Tag>{t.nodeType}</Tag>
              <Tag color="green">{t.definition.version}</Tag>
            </>
          }
        >
          <Collapse
            items={[
              {
                key: 'sections',
                label: `参数与提示词段落 (${t.definition.sections.length})`,
                children: t.definition.sections.map((s) => (
                  <p key={s.key}>
                    <code>{s.key}</code> · {s.valueType} · 默认：
                    {String(s.defaultValue ?? '空')} · {s.editable ? '可编辑' : '只读'}
                  </p>
                )),
              },
            ]}
          />
        </Card>
      ))}
    </Space>
  );

  return (
    <main className="page">
      <Typography.Title>模板库</Typography.Title>
      <Typography.Paragraph type="secondary">
        内置鲸剧方法论可预览、派生并在运行时冻结具体版本。
      </Typography.Paragraph>
      <Tabs
        items={[
          { key: 'built', label: '内置模板', children: built },
          {
            key: 'mine',
            label: '我的模板',
            children: <Empty description="可基于内置模板创建个人版本" />,
          },
          {
            key: 'shared',
            label: '共享模板',
            children: <Empty description="即将开放" />,
          },
        ]}
      />
    </main>
  );
}
