#!/bin/bash
# ============================================================
# 测试数据生成脚本 — 生成近30天的借用测试数据
# 用法: ./generate-test-data.sh [服务器地址]
# ============================================================
SERVER="${1:-http://localhost:8080}"
API="${SERVER}/api/v1"

# 登录获取token
echo "[INFO] 登录admin..."
TOKEN=$(curl -s -X POST "${API}/auth/local/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//;s/"//')

if [ -z "$TOKEN" ]; then echo "[ERROR] 登录失败"; exit 1; fi
echo "[OK] 已获取token"

# 获取设备列表
DEVICES=$(curl -s "${API}/devices?page=1&size=100" -H "Authorization: Bearer ${TOKEN}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['records'];print(','.join(str(x['id']) for x in d))" 2>/dev/null)
IFS=',' read -ra DEVICE_IDS <<< "$DEVICES"

if [ ${#DEVICE_IDS[@]} -eq 0 ]; then echo "[ERROR] 无设备数据，请先导入CSV"; exit 1; fi
echo "[OK] 找到 ${#DEVICE_IDS[@]} 个设备"

# 获取用户列表
USER_IDS=$(curl -s "${API}/admin/users?page=1&size=100" -H "Authorization: Bearer ${TOKEN}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data']['records'];print(','.join(str(x['id']) for x in d))" 2>/dev/null)
IFS=',' read -ra UIDS <<< "$USER_IDS"
echo "[OK] 找到 ${#UIDS[@]} 个用户"

# 生成过去30天的随机借用数据
TOTAL=0
for i in $(seq 1 50); do
  DEVICE_ID=${DEVICE_IDS[$((RANDOM % ${#DEVICE_IDS[@]}))]}
  USER_ID=${UIDS[$((RANDOM % ${#UIDS[@]}))]}
  DAYS_AGO=$((RANDOM % 30))
  START=$(date -d "$DAYS_AGO days ago" +%Y-%m-%dT%H:00:00 2>/dev/null || date -v-${DAYS_AGO}d +%Y-%m-%dT%H:00:00 2>/dev/null)
  END=$(date -d "$((DAYS_AGO - RANDOM % 7)) days ago" +%Y-%m-%dT%H:00:00 2>/dev/null || echo "2026-07-15T17:00:00")
  [ -z "$START" ] && START="2026-07-01T09:00:00"

  STATUSES=("PENDING_APPROVAL" "APPROVED" "BORROWING" "RETURNED" "REJECTED")
  STATUS=${STATUSES[$((RANDOM % 5))]}

  # 随机审批人（教师或管理员）
  APPROVER=${UIDS[$((RANDOM % ${#UIDS[@]}))]}

  BODY="{\"deviceId\":${DEVICE_ID},\"startTime\":\"${START}\",\"endTime\":\"${END}\",\"reason\":\"测试数据-${i}\",\"approverId\":${APPROVER}}"

  RESP=$(curl -s -X POST "${API}/borrows" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$BODY" 2>/dev/null)

  if echo "$RESP" | grep -q '"code":200'; then
    TOTAL=$((TOTAL + 1))
    echo "[$TOTAL/50] 已创建借用: device=${DEVICE_ID} user=${USER_ID}"
  fi
done

echo ""
echo "[DONE] 共生成 ${TOTAL} 条测试借用数据"
