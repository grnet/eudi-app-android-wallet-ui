#!/usr/bin/env bash
if [ "$1" == "" ]; then
    echo "Usage: patch_ip.sh IP"
    exit 1
fi

echo "Using IP: $1"
sed -i "s/192.168.134.214/$1/g" core-logic/src/dev/java/eu/europa/ec/corelogic/config/ConfigWalletCoreImpl.kt
