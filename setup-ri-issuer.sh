#!/usr/bin/env bash

if [ "$1" == "" ]; then
    echo "Usage:"
    echo "setup-ri-issuer.sh ISSUER_REPO_PATH"
    exit
fi

mkdir -p network-logic/src/main/res/raw/
cp $1/cert.pem network-logic/src/main/res/raw/cert.pem
