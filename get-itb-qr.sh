#!/usr/bin/env bash

curl https://dss.aegean.gr/aptitude/vci/offer\?credentialType\=urn:eu.europa.ec.eudi:pid:1\&sessionId\=12345 > vci_offer.json

cat vci_offer.json | jq -C
cat vci_offer.json | jq -r .qr | cut -c 23- | base64 --decode > qr.png
open qr.png
