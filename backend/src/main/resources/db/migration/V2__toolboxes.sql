CREATE TABLE toolboxes (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  nodes_json TEXT NOT NULL,
  edges_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_toolboxes_user ON toolboxes(user_id, created_at);
