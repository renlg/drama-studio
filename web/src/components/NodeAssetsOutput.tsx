import {useCallback,useEffect,useState} from 'react';
import {Collapse,Empty,Image,Skeleton,Space,Tag,Typography} from 'antd';
import {useParams} from 'react-router-dom';
import {api} from '../api/client';

export interface NodeAsset {
  id: string;
  assetType: string;
  name: string;
  logicalKey: string;
  contentStatus: string;
  isSelected: boolean;
  isLocked: boolean;
  versionNo: number;
  previewUrl?: string;
  thumbnailUrl?: string;
  playUrl?: string;
  contentText?: string;
  createdAt: string;
}
export interface NodeAssets {nodeId: string; assets: NodeAsset[]}

/** 拉取某个画布节点的历次运行产出（assets.node_run_id -> node_runs.node_id） */
export function useNodeAssets(nodeId: string | undefined, autoRefresh = false) {
  const {projectId} = useParams();
  const [data, setData] = useState<NodeAssets>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    if (!projectId || !nodeId) return;
    try {
      const response = await api.get(`/projects/${projectId}/nodes/${nodeId}/assets`);
      setData(response.data);
      setFailed(false);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [projectId, nodeId]);

  useEffect(() => {
    setLoading(true);
    load();
    if (!autoRefresh) return;
    const timer = window.setInterval(load, 10000);
    return () => window.clearInterval(timer);
  }, [load, autoRefresh]);

  return {data, loading, failed, reload: load};
}

const isImage = (t: string) => t === 'STORYBOARD_IMAGE' || t === 'CHARACTER_REFERENCE_IMAGE';
const isVideo = (t: string) => t === 'SHOT_VIDEO';
const isText = (t: string) => !isImage(t) && !isVideo(t);

const STATUS_TAG: Record<string, string> = {
  draft: 'default', approved: 'green', selected: 'blue',
  locked: 'purple', rejected: 'red', revision: 'orange',
};

/** 参数面板「输出」页签：展示该节点跑出来的资产 */
export default function NodeAssetsOutput({nodeId, autoRefresh = false}: {nodeId: string; autoRefresh?: boolean}) {
  const {data, loading, failed, reload} = useNodeAssets(nodeId, autoRefresh);

  if (loading) return <Skeleton active />;
  if (failed) return <Empty description="节点产出加载失败" />;

  const list = data?.assets ?? [];
  if (!list.length) {
    return <Empty description="该节点暂无产出，运行后可在此查看" />;
  }

  const images = list.filter(a => isImage(a.assetType));
  const videos = list.filter(a => isVideo(a.assetType));
  const texts = list.filter(a => isText(a.assetType));

  return (
    <div className="node-assets-output">
      <div style={{marginBottom: 8}}>
        <Typography.Text type="secondary" style={{fontSize: 12}}>
          共 {list.length} 个产出
        </Typography.Text>
        <a style={{float: 'right', fontSize: 12}} onClick={reload}>刷新</a>
      </div>

      {!!images.length && (
        <section style={{marginBottom: 12}}>
          <Typography.Title level={5}>图片 ({images.length})</Typography.Title>
          <Space wrap>
            {images.map(a => (
              <div key={a.id} style={{width: 104}}>
                <Image
                  src={a.thumbnailUrl || a.previewUrl}
                  width={104}
                  height={104}
                  style={{objectFit: 'cover', border: '1px solid #303030', borderRadius: 4}}
                  fallback="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='104' height='104'%3E%3Crect width='104' height='104' fill='%23141414'/%3E%3C/svg%3E"
                />
                <div style={{fontSize: 11, color: '#8c8c8c', marginTop: 2}}>
                  v{a.versionNo} {a.isSelected ? '· 已选' : ''}
                </div>
              </div>
            ))}
          </Space>
        </section>
      )}

      {!!videos.length && (
        <section style={{marginBottom: 12}}>
          <Typography.Title level={5}>视频 ({videos.length})</Typography.Title>
          {videos.map(a => (
            <div key={a.id} style={{marginBottom: 8}}>
              <video controls preload="metadata" src={a.playUrl}
                     style={{width: '100%', borderRadius: 4, background: '#000'}} />
              <Typography.Text style={{fontSize: 11}} type="secondary">
                {a.name} · v{a.versionNo}
              </Typography.Text>
            </div>
          ))}
        </section>
      )}

      {!!texts.length && (
        <section>
          <Typography.Title level={5}>文本 ({texts.length})</Typography.Title>
          <Collapse
            size="small"
            defaultActiveKey={texts.length === 1 ? [texts[0].id] : []}
            items={texts.map(a => ({
              key: a.id,
              label: (
                <Space size={6}>
                  <span style={{fontSize: 12}}>{a.assetType}</span>
                  <Tag color={STATUS_TAG[a.contentStatus] || 'default'} style={{marginInlineEnd: 0}}>
                    v{a.versionNo} {a.contentStatus}
                  </Tag>
                  {a.isSelected && <Tag color="blue">已选</Tag>}
                  {a.isLocked && <Tag color="purple">锁定</Tag>}
                </Space>
              ),
              children: a.contentText
                ? <pre style={{whiteSpace: 'pre-wrap', fontSize: 12, margin: 0, maxHeight: 320, overflow: 'auto'}}>{a.contentText}</pre>
                : <Empty description="无文本内容" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
            }))}
          />
        </section>
      )}
    </div>
  );
}
