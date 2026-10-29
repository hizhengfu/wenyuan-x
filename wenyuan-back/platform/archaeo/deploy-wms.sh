#!/usr/bin/env bash
# asw-wms 增量发布：extractBootJar 构建 → rsync 增量同步 → 远程重启 → 健康检查
#
# 本文件是 scripts/deploy-wms.sh的实配副本（bin/ 已被 .gitignore 忽略，真实 IP 不进仓库）。
# 用法（项目根目录或任意位置执行均可）：
#   platform/asw-wms/bin/deploy-wms.sh            构建 + 同步 + 重启 + 健康检查
#   platform/asw-wms/bin/deploy-wms.sh--dry-run   只列出将传输的文件，不动服务器
#   platform/asw-wms/bin/deploy-wms.sh--no-build  跳过构建，直接同步现有 build/deploy
#
# 替换式发布：全程【不删除】服务器上任何文件（rsync 不带 --delete），只新增/覆盖。
# 瘦 jar 以固定名 REMOTE_APP_JAR 替换上传：升版后文件名不变、启动入口恒定，
# 旧版瘦 jar 不会残留，也无需在服务器上做软链或改启动脚本。
# 原理与注意事项见 doc/deploy-incremental.md（rsync -c 按校验和比对，
# 日常发版通常只传瘦 jar 约 171KB；driver-bundle 等大件仅首次传输）。

set -euo pipefail

# ───────────────────────── 配置区（请填写）─────────────────────────
SSH_USER="root"                  # SSH 用户名
SSH_HOST="47.96.96.110"                      # 服务器地址（必填），例：192.168.1.100
SSH_PORT="22"                    # SSH 端口
SSH_KEY=""                       # 私钥路径，留空用 ssh 默认（~/.ssh/id_rsa 等）
JUMP_HOST=""                     # 跳板机，留空不用，例：jumpuser@10.0.0.1
REMOTE_DIR="/data/web/asw-wms"       # 部署目录（替换式同步：只覆盖发布产物，目录内其他文件一概不动）
REMOTE_APP_JAR=""         # 远端瘦 jar 固定文件名（启动入口恒定：java -jar app.jar）；留空则保留带版本号的原名
RESTART_CMD="systemctl restart wwy"                   # 重启命令，留空则跳过并提示手动重启。示例：
                                 #   systemctl restart wenwuyun
                                 #   /opt/wenwuyun/restart.sh
HEALTH_URL=""                    # 健康检查地址（服务器本机视角），留空跳过。例：http://127.0.0.1:8080/api/ping
HEALTH_RETRY=15                  # 健康检查重试次数（间隔 2s）

PLATFORM="asw-wms"           # 平台名；复制本脚本给其他平台（如 szaj）时改这里
# ──────────────────────────────────────────────────────────────────

cd "$(dirname "$0")/../../.."    # 本脚本在 platform/asw-wms/bin/，上三级切到项目根目录

DEPLOY_DIR="platform/${PLATFORM}/build/deploy"
DRY_RUN=false
BUILD=true
for arg in "$@"; do
    case "$arg" in
        --dry-run)  DRY_RUN=true ;;
        --no-build) BUILD=false ;;
        *) echo "未知参数: ${arg}（可用：--dry-run --no-build）" >&2; exit 1 ;;
    esac
done

[ -n "$SSH_HOST" ] || { echo "✗ 请先填写脚本顶部配置区的 SSH_HOST" >&2; exit 1; }

SSH_OPTS=(-p "$SSH_PORT")
[ -n "$SSH_KEY" ]   && SSH_OPTS+=(-i "$SSH_KEY")
[ -n "$JUMP_HOST" ] && SSH_OPTS+=(-J "$JUMP_HOST")
RSYNC_SSH="ssh ${SSH_OPTS[*]}"
REMOTE="${SSH_USER}@${SSH_HOST}"

# 进度显示：rsync 3.x（brew install rsync）支持整体进度条（总百分比+速度+ETA），
# 系统自带 openrsync 不支持，退化为逐文件进度
if rsync --version 2>/dev/null | grep -q 'version 3\.'; then
    PROGRESS=(--info=progress2)
else
    PROGRESS=(--progress)
fi

# 1. 构建（显示 Gradle 任务进度）
if $BUILD; then
    echo "▶ 构建 :platform-${PLATFORM}:extractBootJar ..."
    ./gradlew ":platform-${PLATFORM}:extractBootJar"
fi
[ -d "$DEPLOY_DIR/lib" ] || { echo "✗ ${DEPLOY_DIR} 不存在，请先构建（去掉 --no-build）" >&2; exit 1; }
THIN_JAR=$(basename "$(ls "$DEPLOY_DIR"/*.jar)")
REMOTE_JAR_NAME="${REMOTE_APP_JAR:-$THIN_JAR}"
echo "▶ 本次发布：${THIN_JAR}（lib $(ls "$DEPLOY_DIR/lib" | wc -l | tr -d ' ') 个 jar）→ ${REMOTE}:${REMOTE_DIR}/，入口 ${REMOTE_JAR_NAME}"

# 2. 确保远端目录存在（空目录，无破坏性）
ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$REMOTE_DIR'"

# 3. dry-run：只比对不传输
if $DRY_RUN; then
    echo "▶ dry-run 差异列表（校验和比对，可能略慢；替换式同步，不会删除远端任何文件）："
    rsync -rcin --exclude='/*.jar' -e "$RSYNC_SSH" "$DEPLOY_DIR/" "$REMOTE:$REMOTE_DIR/"
    rsync -cin -e "$RSYNC_SSH" "$DEPLOY_DIR/$THIN_JAR" "$REMOTE:$REMOTE_DIR/$REMOTE_JAR_NAME"
    echo "✔ dry-run 结束，服务器未改动"
    exit 0
fi

# 4. 增量同步（-c 按内容比对；--partial 断点续传；不带 --delete，远端文件只增/覆盖不删除）
#    先同步 lib/ 依赖，最后替换瘦 jar：新入口落地时其 Class-Path 引用的依赖已全部就位
echo "▶ rsync 增量同步 lib/ ..."
rsync -rcv --partial "${PROGRESS[@]}" --exclude='/*.jar' -e "$RSYNC_SSH" "$DEPLOY_DIR/" "$REMOTE:$REMOTE_DIR/"
echo "▶ 替换瘦 jar：${THIN_JAR} → ${REMOTE_JAR_NAME} ..."
rsync -cv --partial -e "$RSYNC_SSH" "$DEPLOY_DIR/$THIN_JAR" "$REMOTE:$REMOTE_DIR/$REMOTE_JAR_NAME"

# 5. 重启
if [ -n "$RESTART_CMD" ]; then
    # 变量展开紧跟全角字符必须加花括号定界：单字节 locale 下 bash 会把全角字符首字节并入变量名（unbound variable）
    echo "▶ 重启应用：${RESTART_CMD}（启动入口应指向 ${REMOTE_DIR}/${REMOTE_JAR_NAME}）"
    ssh "${SSH_OPTS[@]}" "$REMOTE" "$RESTART_CMD"
else
    echo "⚠ 未配置 RESTART_CMD，请手动重启（启动入口：java -jar ${REMOTE_DIR}/${REMOTE_JAR_NAME}）"
fi

# 6. 健康检查
if [ -n "$HEALTH_URL" ]; then
    printf "▶ 健康检查 %s " "$HEALTH_URL"
    for _ in $(seq 1 "$HEALTH_RETRY"); do
        if ssh "${SSH_OPTS[@]}" "$REMOTE" "curl -sf -o /dev/null '$HEALTH_URL'"; then
            printf "\n✔ 发布完成，应用健康\n"
            exit 0
        fi
        printf "."
        sleep 2
    done
    printf "\n✗ 健康检查超时（%s×2s），请上服务器查看日志\n" "$HEALTH_RETRY" >&2
    exit 1
fi
echo "✔ 发布完成"
