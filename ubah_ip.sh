#!/bin/bash
if [ -z "$1" ]; then
  echo "Format: ubahip 192.168.1.XX"
  exit 1
fi
NEW_IP=$1
# Ganti IP apapun format 192.168.x.x di semua file java
sed -i "s/192\.168\.[0-9]\+\.[0-9]\+/$NEW_IP/g" ~/ezax-agent/app/src/main/java/com/ezax/agent/*.java
echo "✅ Semua IP di kodingan Android berhasil diubah menjadi $NEW_IP!"
