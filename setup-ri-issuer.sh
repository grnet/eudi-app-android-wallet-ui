#!/usr/bin/env bash
set -e

if [ "$1" == "" ]; then
    echo "Usage:"
    echo "setup-ri-issuer.sh ISSUER_REPO_PATH [ISSUER_HOSTNAME]"
    echo "ISSUER_REPO_PATH     the local path of the checked out repo of the issuer fork"
    echo "ISSUER_HOSTNAME      the hostname of the issuer (if empty, the local IP is used)"
    exit
fi

echo "Copying certificate from: $1"
mkdir -p network-logic/src/main/res/raw/
cp $1/cert.pem network-logic/src/main/res/raw/cert.pem

echo "Setting issuer URL..."
if [ "$2" == "" ]; then
    ISSUER_HOST=$($1/resolve-ip.sh)
else
    ISSUER_HOST=$2
fi
sed -i -e 's/const val VCI_ISSUER_URL = ".*"/const val VCI_ISSUER_URL = "https:\/\/'${ISSUER_HOST}':5000"/g' core-logic/src/dev/java/eu/europa/ec/corelogic/config/ConfigWalletCoreImpl.kt
