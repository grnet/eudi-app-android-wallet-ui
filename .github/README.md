This is a test fork of the [Android EUDI Wallet](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/),
aiming to ease local test deployments.

## Build

1. Build the modified version of the
   eudi-lib-android-wallet-document-manager library:

```
git clone https://github.com/grnet/eudi-lib-android-wallet-document-manager.git
cd eudi-lib-android-wallet-document-manager
git switch v0.13.0-SNAPSHOT1
./build-local.sh
```

2. Build the modified version of the
   eudi-lib-android-iso18013-data-transfer library:

```
git clone https://github.com/grnet/eudi-lib-android-iso18013-data-transfer.git
cd eudi-lib-android-iso18013-data-transfer
git switch v0.10.0-SNAPSHOT1
./build-local.sh
```

3. Build the modified version of the eudi-lib-jvm-openid4vci-kt library:

```
git clone https://github.com/grnet/eudi-lib-jvm-openid4vci-kt.git
cd eudi-lib-jvm-openid4vci-kt
git switch v0.9.3-SNAPSHOT1
./build-local.sh
```

4. Build the modified version of the eudi-wallet-core library:

```
git clone git@github.com:grnet/eudi-lib-android-wallet-core.git
cd eudi-lib-android-wallet-core
git switch v0.22.0-SNAPSHOT1
./build-local.sh
```

5. Clone the issuer fork
   (https://github.com/grnet/eudi-srv-web-issuing-eudiw-py) to some
   directory `ISSUER_DIR`. Install the issuer following the instructions
   in branch `local-deploy-snf-74864-draft-15` of that repository.

6. In this repository, switch to branch `okeanos-deploy-v6` and run
   `./setup-ri-issuer.sh ISSUER_DIR`

7. Build the wallet application.

8. Install the application to an Android device; ensure that the
   issuer is visible (e.g., both issuer and app devices are connected
   to the same network).
