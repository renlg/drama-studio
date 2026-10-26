#!/bin/bash
# drama-studio 一键部署脚本（本机执行，部署到火山服务器 volc-ark）
# 用法: bash deploy/deploy.sh [后端jar路径] [前端dist路径]
set -e

SERVER="volc-ark"
REMOTE_DIR="/opt/drama-studio"
BACKEND_JAR="${1:-backend/target/drama-studio-0.0.1-SNAPSHOT.jar}"
WEB_DIST="${2:-web/dist}"

echo "=== 1. 检查本地构建产物 ==="
[ -f "$BACKEND_JAR" ] || { echo "✗ 后端 jar 不存在: $BACKEND_JAR"; exit 1; }
[ -d "$WEB_DIST" ] || { echo "✗ 前端 dist 不存在: $WEB_DIST"; exit 1; }
echo "✓ 产物齐全"

echo "=== 2. 创建远程目录 ==="
ssh -o ConnectTimeout=10 "$SERVER" "mkdir -p $REMOTE_DIR/backend $REMOTE_DIR/web $REMOTE_DIR/data"

echo "=== 3. 上传后端 jar ==="
scp "$BACKEND_JAR" "$SERVER:$REMOTE_DIR/backend/drama-studio.jar"
echo "✓ 后端 jar 已上传"

echo "=== 4. 上传前端 ==="
ssh -o ConnectTimeout=10 "$SERVER" "rm -rf $REMOTE_DIR/web/*"
scp -r "$WEB_DIST"/* "$SERVER:$REMOTE_DIR/web/"
echo "✓ 前端已上传"

echo "=== 5. 上传 systemd 服务 ==="
scp deploy/drama-studio.service "$SERVER:/etc/systemd/system/drama-studio.service"
ssh -o ConnectTimeout=10 "$SERVER" "systemctl daemon-reload && systemctl enable drama-studio"

echo "=== 6. 配置 nginx 子目录 ==="
scp deploy/drama-studio-nginx.conf "$SERVER:/etc/nginx/conf.d/drama-studio.conf"
ssh -o ConnectTimeout=10 "$SERVER" "nginx -t && nginx -s reload"
echo "✓ nginx 已重载"

echo "=== 7. 启动后端 ==="
ssh -o ConnectTimeout=10 "$SERVER" "systemctl restart drama-studio && sleep 5 && systemctl is-active drama-studio"
echo "✓ 服务已启动"

echo "=== 8. 健康检查 ==="
ssh -o ConnectTimeout=10 "$SERVER" "curl -s -o /dev/null -w '后端 API: %{http_code}\n' http://127.0.0.1:18080/api/health || echo '后端未就绪'; curl -s -o /dev/null -w '前端: %{http_code}\n' http://127.0.0.1:80/drama-studio/ || echo '前端未就绪'"

echo "=== 完成 ==="
echo "访问: http://14.103.23.160/drama-studio/"
