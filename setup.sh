#!/usr/bin/env bash

ISSUER_DIR=../eudi-srv-web-issuing-eudiw-py
if [ ! -d "${ISSUER_DIR}" ]; then
    echo "Directory missing from one level above: ${ISSUER_DIR}"
    exit 1
fi

mkdir -p network-logic/src/main/res/raw
cp ${ISSUER_DIR}/api_docs/test_tokens/IACA-token/PIDIssuerCAUT01.pem network-logic/src/main/res/raw/pidissuercaut01.pem
cp ${ISSUER_DIR}/cert.pem network-logic/src/main/res/raw/
cp ${ISSUER_DIR}/key.pem network-logic/src/main/res/raw/
