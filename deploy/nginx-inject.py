#!/usr/bin/env python3
"""drama-studio nginx 子目录配置注入（幂等 + 可更新）

背景：火山服务器 80 端口有多个 server 块：
  - gitea.conf : server_name 14.103.23.160  ← 用 IP 访问时的实际入口（精确匹配优先）
  - taiwei.conf: server_name taiwei-agent.top ... _  ← 域名/兜底

因此 /drama-studio/ 必须注入 gitea.conf，否则用 IP 访问会命中 gitea.conf 的
location / 而 404。本脚本向两个文件的 location / 之前插入配置块。

关键点（踩过的坑）：子目录 SPA 必须用 **root + 真实目录结构**，
不能用 alias + try_files——alias 下 $uri 带 location 前缀，拼出的文件不存在，
fallback 又触发 location 重匹配 → 除 / 以外全部 404。
故建软链 /opt/drama-studio/webroot/drama-studio -> /opt/drama-studio/web，
配置用 root /opt/drama-studio/webroot。
"""
import os
import subprocess
import sys

# 目标：IP 访问的精确匹配 server 块 + 域名兜底
TARGETS = [
    "/etc/nginx/conf.d/gitea.conf",
    "/etc/nginx/conf.d/taiwei.conf",
]

# SPA 站点根：内含 drama-studio -> web 的软链
WEBROOT = "/opt/drama-studio/webroot"
WEBDIR = "/opt/drama-studio/web"
SITE_LINK = os.path.join(WEBROOT, "drama-studio")

BEGIN = "    # >>> drama-studio managed block (do not edit manually) >>>"
END = "    # <<< drama-studio managed block <<<"

SNIPPET = """{begin}
    # 静态资源（长缓存）
    location /drama-studio/assets/ {{
        root {webroot};
        expires 7d;
        add_header Cache-Control "public";
    }}

    # SPA 前端：root 指向含 drama-studio/ 目录的父级，fallback 走前端路由
    location /drama-studio/ {{
        root {webroot};
        index index.html;
        try_files $uri $uri/ /drama-studio/index.html;
    }}

    # 后端 API 反代（含 SSE 长连接）
    location /drama-studio/api/ {{
        proxy_pass http://127.0.0.1:18080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }}

    # OAuth 登录/回调（只反代后端这两个端点，别吞掉前端路由）
    location /drama-studio/auth/ {{
        proxy_pass http://127.0.0.1:18080/auth/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }}
{end}
""".format(begin=BEGIN, end=END, webroot=WEBROOT)


def ensure_webroot() -> str:
    """建 webroot/drama-studio 软链指向构建产物目录"""
    os.makedirs(WEBROOT, exist_ok=True)
    if not os.path.isdir(WEBDIR):
        return "SKIP: 构建产物目录不存在 " + WEBDIR
    if os.path.islink(SITE_LINK):
        if os.path.realpath(SITE_LINK) == os.path.realpath(WEBDIR):
            return "OK: 软链已存在"
    if os.path.exists(SITE_LINK):
        os.remove(SITE_LINK)
    os.symlink(WEBDIR, SITE_LINK)
    return "OK: 已建软链"


def inject(path: str) -> str:
    """向单个配置文件注入/更新配置块"""
    if not os.path.exists(path):
        return "SKIP: 不存在 " + path

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    if BEGIN in content and END in content:
        start = content.index(BEGIN)
        stop = content.index(END) + len(END) + 1
        content = content[:start] + SNIPPET + content[stop:]
        action = "已更新"
    else:
        anchor = "    location / {\n"
        if anchor not in content:
            return "SKIP: " + os.path.basename(path) + " 无 location / 锚点"
        content = content.replace(anchor, SNIPPET + "\n" + anchor, 1)
        action = "已插入"

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return "OK: " + action + " " + os.path.basename(path)


def main():
    changed = False

    r = ensure_webroot()
    print(r)
    if r.startswith("OK"):
        changed = True

    for path in TARGETS:
        r = inject(path)
        print(r)
        if r.startswith("OK"):
            changed = True

    return 0 if changed else 1


if __name__ == "__main__":
    sys.exit(main())
