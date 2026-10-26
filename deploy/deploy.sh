#!/bin/bash
# ============================================================
# drama-studio 一键部署脚本
#   - 在 VM 上构建后端 jar + 前端 dist（本机禁装依赖）
#   - 拉回本机 → 上传火山服务器 → 重启服务 → 配置 nginx → 健康检查
#   - 失败自动回滚（后端 jar / nginx 配置均有备份）
#
# 用法:
#   bash deploy/deploy.sh                # 完整部署（构建 + 上传 + 重启）
#   bash deploy/deploy.sh --skip-build   # 跳过构建，用已有产物部署
#   bash deploy/deploy.sh --nginx-only   # 只配置 nginx 并重载
#   bash deploy/deploy.sh --status       # 只看线上状态
# ============================================================
set -uo pipefail

SERVER="volc-ark"
REMOTE_DIR="/opt/drama-studio"
APP_PORT=18080
VM="utm-vm"
STAMP=$(date +%Y%m%d-%H%M%S)

# 颜色
R='\033[0;31m'; G='\033[0;32m'; Y='\033[0;33m'; B='\033[0;34m'; N='\033[0m'
ok()   { echo -e "${G}✓${N} $1"; }
warn() { echo -e "${Y}!${N} $1"; }
err()  { echo -e "${R}✗${N} $1"; }
step() { echo -e "\n${B}==>${N} $1"; }

MODE="${1:-full}"
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)

# ---------- 只查状态 ----------
if [ "$MODE" = "--status" ]; then
  step "线上状态"
  ssh -o ConnectTimeout=10 "$SERVER" "
    echo -n '服务状态: '; systemctl is-active drama-studio
    echo -n '后端进程: '; ps -eo args | grep -c '[d]rama-studio.jar' || echo 0
    echo -n '18080 监听: '; ss -tln | grep -q ':18080 ' && echo 是 || echo 否
    echo -n 'auth/login: '; curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/auth/login
    echo -n '前端页面: '; curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/drama-studio/
    echo -n '数据库大小: '; du -h $REMOTE_DIR/data/drama-studio.db 2>/dev/null | cut -f1
    echo -n '运行时长: '; systemctl show drama-studio -p ActiveEnterTimestamp --value
  "
  exit 0
fi

# ---------- 只配 nginx ----------
if [ "$MODE" = "--nginx-only" ]; then
  step "配置 nginx 子目录"
  scp -o ConnectTimeout=10 deploy/nginx-inject.py "$SERVER:/tmp/nginx-inject.py" >/dev/null || { err "上传注入脚本失败"; exit 1; }
  ssh -o ConnectTimeout=15 "$SERVER" "
    for f in gitea.conf taiwei.conf; do
      [ -f /etc/nginx/conf.d/\$f ] && cp /etc/nginx/conf.d/\$f /etc/nginx/conf.d/\$f.bak-$STAMP
    done
    python3 /tmp/nginx-inject.py || exit 1
    if ! nginx -t 2>&1 | grep -q successful; then
      echo 'nginx 校验失败，回滚'
      for f in gitea.conf taiwei.conf; do
        [ -f /etc/nginx/conf.d/\$f.bak-$STAMP ] && cp /etc/nginx/conf.d/\$f.bak-$STAMP /etc/nginx/conf.d/\$f
      done
      exit 1
    fi
    nginx -s reload && echo 'nginx 已重载'
  " || { err "nginx 配置失败"; exit 1; }
  ok "nginx 子目录配置完成"
  exit 0
fi

# ---------- 1. 构建（VM） ----------
if [ "$MODE" != "--skip-build" ]; then
  step "1/6 构建后端 + 前端（VM）"
  rsync -az --exclude 'node_modules' --exclude 'dist' --exclude 'target' \
    --exclude '.git' --exclude '*.db*' --exclude 'vite.config.js' \
    --exclude 'vite.config.d.ts' --exclude '*.tsbuildinfo' \
    "$ROOT/" "$VM:~/workspace/drama-studio/" || { err "rsync 到 VM 失败"; exit 1; }

  ssh -o ConnectTimeout=10 "$VM" '
    export PATH="$HOME/.local/bin:$HOME/node/22/bin:$HOME/jdk/current/bin:$HOME/maven/current/bin:$PATH"
    export JAVA_HOME="$HOME/jdk/current"
    export https_proxy=http://127.0.0.1:1081
    cd ~/workspace/drama-studio
    # 清理 tsc 残留（否则 vite 会优先加载 .js 而忽略 .ts 里的 base 配置）
    rm -f web/vite.config.js web/vite.config.d.ts
    echo "--- 后端 mvn package ---"
    cd backend && mvn package -DskipTests -q 2>&1 | tail -5
    ls -lh target/*.jar 2>/dev/null | tail -1
    echo "--- 前端 npm run build ---"
    cd ../web && rm -rf dist && npm run build 2>&1 | tail -6
    # 构建后再次清理（tsc -b 可能重新生成）
    rm -f vite.config.js vite.config.d.ts
  ' || { err "VM 构建失败"; exit 1; }
  ok "构建完成"
else
  warn "跳过构建（--skip-build）"
fi

# ---------- 2. 拉回产物 ----------
step "2/6 拉回构建产物"
rsync -az "$VM:~/workspace/drama-studio/backend/target/drama-studio-1.0.0.jar" \
  backend/target/ || { err "拉回 jar 失败"; exit 1; }
rsync -az "$VM:~/workspace/drama-studio/web/dist/" web/dist/ || { err "拉回 dist 失败"; exit 1; }
ok "产物已拉回（jar $(du -h backend/target/drama-studio-1.0.0.jar | cut -f1)，dist $(du -sh web/dist | cut -f1)）"

# 校验前端 base 路径
if ! grep -q '/drama-studio/assets/' web/dist/index.html; then
  err "前端产物 base 路径不对（应为 /drama-studio/assets/）"
  grep -o 'src="[^"]*"' web/dist/index.html || true
  exit 1
fi
ok "前端 base 路径正确"

# ---------- 3. 备份线上版本 ----------
step "3/6 备份线上版本"
ssh -o ConnectTimeout=10 "$SERVER" "
  mkdir -p $REMOTE_DIR/backend $REMOTE_DIR/web $REMOTE_DIR/data $REMOTE_DIR/backup
  if [ -f $REMOTE_DIR/backend/drama-studio.jar ]; then
    cp $REMOTE_DIR/backend/drama-studio.jar $REMOTE_DIR/backup/drama-studio.jar.$STAMP
    echo '后端 jar 已备份'
  fi
  ls -t $REMOTE_DIR/backup/drama-studio.jar.* 2>/dev/null | tail -n +6 | xargs -r rm -f
" || { err "备份失败"; exit 1; }
ok "备份完成（保留最近 5 份）"

# ---------- 4. 上传 ----------
step "4/6 上传产物"
scp -o ConnectTimeout=10 backend/target/drama-studio-1.0.0.jar \
  "$SERVER:$REMOTE_DIR/backend/drama-studio.jar" || { err "上传 jar 失败"; exit 1; }
ssh -o ConnectTimeout=10 "$SERVER" "rm -rf $REMOTE_DIR/web/*"
scp -r -o ConnectTimeout=10 web/dist/* "$SERVER:$REMOTE_DIR/web/" || { err "上传前端失败"; exit 1; }
ok "上传完成"

# ---------- 5. 重启后端 ----------
step "5/6 重启后端"
scp -o ConnectTimeout=10 deploy/drama-studio.service "$SERVER:/etc/systemd/system/drama-studio.service" >/dev/null
ssh -o ConnectTimeout=10 "$SERVER" "systemctl daemon-reload && systemctl enable drama-studio >/dev/null 2>&1 && systemctl restart drama-studio"

# 等待启动（最多 60s）
READY=0
for i in $(seq 1 30); do
  sleep 2
  if ssh -o ConnectTimeout=5 "$SERVER" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$APP_PORT/auth/login 2>/dev/null | grep -q 302"; then
    READY=1; break
  fi
done

if [ "$READY" -ne 1 ]; then
  err "后端启动失败，开始回滚"
  ssh -o ConnectTimeout=10 "$SERVER" "
    echo '--- 失败日志 ---'
    journalctl -u drama-studio -n 30 --no-pager | grep -iE 'error|exception|caused by' | tail -8
    if [ -f $REMOTE_DIR/backup/drama-studio.jar.$STAMP ]; then
      cp $REMOTE_DIR/backup/drama-studio.jar.$STAMP $REMOTE_DIR/backend/drama-studio.jar
      systemctl restart drama-studio
      echo '--- 已回滚到上一版本 ---'
    else
      echo '--- 无备份可回滚 ---'
    fi
  "
  exit 1
fi
ok "后端已启动（${APP_PORT}）"

# ---------- 6. nginx ----------
step "6/6 配置 nginx 子目录"
scp -o ConnectTimeout=10 deploy/nginx-inject.py "$SERVER:/tmp/nginx-inject.py" >/dev/null
ssh -o ConnectTimeout=15 "$SERVER" "
  # 备份两个可能的入口配置
  for f in gitea.conf taiwei.conf; do
    [ -f /etc/nginx/conf.d/\$f ] && cp /etc/nginx/conf.d/\$f /etc/nginx/conf.d/\$f.bak-$STAMP
  done
  python3 /tmp/nginx-inject.py || { echo '注入失败'; exit 1; }
  if ! nginx -t 2>&1 | grep -q successful; then
    echo 'nginx 校验失败，回滚配置'
    for f in gitea.conf taiwei.conf; do
      [ -f /etc/nginx/conf.d/\$f.bak-$STAMP ] && cp /etc/nginx/conf.d/\$f.bak-$STAMP /etc/nginx/conf.d/\$f
    done
    exit 1
  fi
  nginx -s reload && echo 'nginx 已重载'
" || { err "nginx 配置失败（已回滚配置）"; exit 1; }
ok "nginx 子目录配置完成"

# ---------- 健康检查 ----------
step "健康检查"
ssh -o ConnectTimeout=10 "$SERVER" "
  printf '后端 /auth/login : %s\n' \$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$APP_PORT/auth/login)
  printf '前端 /drama-studio/ : %s\n' \$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/drama-studio/)
  printf 'API (未认证应403) : %s\n' \$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$APP_PORT/api/v1/projects)
  printf '静态资源 : %s\n' \$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/drama-studio/assets/\$(ls $REMOTE_DIR/web/assets/ | grep '\.js\$' | head -1))
"

echo -e "\n${G}========== 部署完成 ==========${N}"
echo -e "访问: ${B}http://14.103.23.160/drama-studio/${N}"
echo -e "备份: $REMOTE_DIR/backup/  （最近 5 份）"
