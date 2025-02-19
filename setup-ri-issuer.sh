#!/usr/bin/env bash

if [ "$1" == "" ]; then
    echo "Usage:"
    echo "setup-ri-issuer.sh ISSUER_REPO_PATH"
    exit
fi

mkdir -p network-logic/src/main/res/raw/
cp $1/cert.pem network-logic/src/main/res/raw/cert.pem

IP=$($1/resolve-ip.sh)
sed -i -e 's/const val VCI_ISSUER_URL = ".*"/const val VCI_ISSUER_URL = "https:\/\/'${IP}':5000"/g' core-logic/src/dev/java/eu/europa/ec/corelogic/config/ConfigWalletCoreImpl.kt
