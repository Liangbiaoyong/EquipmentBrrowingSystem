#!/bin/bash
# ============================================================
# 设备资产批量导入脚本
# 用法: ./import-devices.sh <文件路径> [服务器地址]
#
# 示例:
#   ./import-devices.sh ./20260707-在账资产查询.csv
#   ./import-devices.sh ./资产.xlsx http://192.168.1.100:8080
# ============================================================
set -euo pipefail

# ─── 参数 ───
FILE="$1"
SERVER="${2:-http://localhost:8080}"
API_URL="${SERVER}/api/v1"

if [ ! -f "$FILE" ]; then
    echo "[ERROR] 文件不存在: $FILE"
    exit 1
fi

FILENAME=$(basename "$FILE")
EXT="${FILENAME##*.}"
case "$EXT" in
    csv|CSV|xlsx|XLSX) ;;
    *) echo "[ERROR] 不支持的文件格式: .$EXT (仅支持 .csv / .xlsx)"; exit 1 ;;
esac

echo "============================================"
echo "  设备资产批量导入"
echo "============================================"
echo "  文件:    $FILE"
echo "  大小:    $(du -h "$FILE" | cut -f1)"
echo "  服务器:  $SERVER"
echo "============================================"

# ─── Step 1: 登录获取 JWT ───
# 使用本地管理员账户登录（如没有本地账户则需要 CAS 登录）
read -rp "用户名: " USERNAME
read -rsp "密码: " PASSWORD
echo ""

LOGIN_RESP=$(curl -s -X POST "${API_URL}/auth/local/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//;s/"//')

if [ -z "$TOKEN" ]; then
    echo "[ERROR] 登录失败，请检查用户名和密码"
    echo "$LOGIN_RESP"
    exit 1
fi
echo "[OK] 登录成功"

# ─── Step 2: 上传文件导入 ───
echo ""
echo "[INFO] 开始导入..."
START_TIME=$(date +%s)

RESP=$(curl -s -X POST "${API_URL}/devices/import" \
    -H "Authorization: Bearer ${TOKEN}" \
    -F "file=@${FILE}")

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# ─── Step 3: 输出结果 ───
echo ""
echo "============================================"
echo "  导入结果（耗时 ${DURATION}s）"
echo "============================================"

# 用 Python/awk 提取 JSON 字段（不依赖 jq）
extract_field() {
    echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d.get('$1','?'))" 2>/dev/null || echo "?"
}

echo "  总行数:     $(extract_field totalRows)"
echo "  新增:       $(extract_field successCount)"
echo "  更新:       $(extract_field updateCount)"
echo "  失败:       $(extract_field failCount)"
echo "  自动分类:   $(extract_field autoCategoryCount)"
echo "  未分类:     $(extract_field uncategorizedCount)"
echo "  批次号:     $(extract_field batchId)"
echo ""

ERROR_COUNT=$(extract_field failCount)
if [ "$ERROR_COUNT" != "0" ] && [ "$ERROR_COUNT" != "?" ]; then
    echo "[WARN] 存在导入错误，详情:"
    echo "$RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
for e in d.get('errors',[]):
    print(f\"  行{e['row']}: [{e['assetNo']}] {e['name']} — {e['reason']}\")
" 2>/dev/null
fi

echo ""
echo "[DONE] 导入完成"
