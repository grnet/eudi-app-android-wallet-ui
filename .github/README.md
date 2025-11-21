This is a test fork of the [Android EUDI Wallet](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/),
aiming to ease local test deployments.

## Installation

1. Clone the issuer fork
   (https://github.com/grnet/eudi-srv-web-issuing-eudiw-py) to some
   directory `ISSUER_DIR`. Install the issuer following the instructions
   in branch `local-deploy-snf-74864-draft-15` of that repository.

2. In this repository, switch to branch `okeanos-deploy-v6` and run
   `./setup-ri-issuer.sh ISSUER_DIR`

4. Build the wallet application.

5. Install the application to an Android device; ensure that the
   issuer is visible (e.g., both issuer and app devices are connected
   to the same network).
