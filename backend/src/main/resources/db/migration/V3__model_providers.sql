CREATE TABLE model_providers (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  base_url TEXT NOT NULL,
  api_key_enc TEXT,
  is_default INTEGER NOT NULL DEFAULT 0,
  enabled INTEGER NOT NULL DEFAULT 1,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_model_providers_enabled ON model_providers(enabled, sort_order);

CREATE TABLE provider_models (
  id TEXT PRIMARY KEY,
  provider_id TEXT NOT NULL REFERENCES model_providers(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  model_id TEXT NOT NULL,
  display_name TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  enabled INTEGER NOT NULL DEFAULT 1,
  sort_order INTEGER NOT NULL DEFAULT 0,
  extra_json TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX idx_provider_models_provider ON provider_models(provider_id, category, sort_order);

INSERT INTO model_providers (id,name,base_url,api_key_enc,is_default,enabled,sort_order,created_at,updated_at) VALUES
 ('11111111-1111-4111-8111-111111111111','ai-connect','https://ai-connect.top/v1',NULL,1,1,0,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');

INSERT INTO provider_models (id,provider_id,category,model_id,display_name,is_default,enabled,sort_order,extra_json,created_at,updated_at) VALUES
 ('m-text-1','11111111-1111-4111-8111-111111111111','TEXT','hermes-token','Hermes Token',1,1,0,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-img-1','11111111-1111-4111-8111-111111111111','IMAGE','image-free','即梦通用（免费）',1,1,0,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-img-2','11111111-1111-4111-8111-111111111111','IMAGE','hermes-image','即梦 Seedream 5 Pro',0,1,1,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-img-3','11111111-1111-4111-8111-111111111111','IMAGE','doubao-seedream-5-0-pro','豆包 Seedream 5.0 Pro',0,1,2,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-vid-1','11111111-1111-4111-8111-111111111111','VIDEO','video-free','即梦通用（免费）',1,1,0,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-vid-2','11111111-1111-4111-8111-111111111111','VIDEO','agnes-video-2.5-flash','Agnes Video 2.5 Flash',0,1,1,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-vid-3','11111111-1111-4111-8111-111111111111','VIDEO','agnes-video-v2.0','Agnes Video 2.0',0,1,2,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-vid-4','11111111-1111-4111-8111-111111111111','VIDEO','grok-imagine-video','Grok Imagine Video',0,1,3,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-vid-5','11111111-1111-4111-8111-111111111111','VIDEO','grok-imagine-video-1.5','Grok Imagine Video 1.5',0,1,4,NULL,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('m-aud-1','11111111-1111-4111-8111-111111111111','AUDIO','audio-free','即梦通用音频（免费）',1,1,0,'{"unverified":true}','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');
