#!/bin/bash
set -e
cd /home/hp506/server
rm -rf EquipmentBrrowingSystem
git clone --depth 1 https://ghproxy.net/https://github.com/Liangbiaoyong/EquipmentBrrowingSystem.git EquipmentBrrowingSystem
cd EquipmentBrrowingSystem
cp ../EquipmentBrrowingSystem.bak/.env ./
cp -r ../EquipmentBrrowingSystem.bak/certs ./ 2>/dev/null || true
echo "=== CLONE_DONE ==="
