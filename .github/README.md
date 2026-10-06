# EUDI Wallet, GRNET fork

A fork of the [EUDI Wallet reference app](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui)
for GRNET's demo deployment at [demo.eudiw.grnet.gr](https://demo.eudiw.grnet.gr/).
Upstream's own README is [at the root](../README.md).

## Branches

| Branch | What |
| --- | --- |
| `grnet` | default: upstream `main` plus the changes below |
| `main` | tracks upstream `main`, unchanged |
| `deploy-okeanos-*`, `local-deploy*` | earlier builds for the okeanos VM deployment, kept for reference |

## What differs from upstream

**Its own package.** `applicationId` is `eu.europa.ec.euidi.grnet`, so the build
installs beside the official app. The `dev` flavour adds `.dev`, making it
`eu.europa.ec.euidi.grnet.dev`. The app is named "EUDI Wallet GR", short
enough to fit under a launcher icon. The code namespace is unchanged.

**The `dev` flavour's backend comes from build arguments** rather than being
hardcoded:

| Property | What | Default |
| --- | --- | --- |
| `issuerUrls` | issuers to offer, comma-separated, one entry per issuer | upstream's two dev issuers |
| `walletProviderUrl` | the wallet provider | upstream's dev wallet provider |

The defaults are upstream's own `dev` values, so a build without the properties
behaves exactly like upstream's. The `demo` flavour is untouched.

**gov.gr branding, beside the EUDI Wallet logo**, as in the verifier UI at
[demo.eudiw.grnet.gr/verifier-ui](https://demo.eudiw.grnet.gr/verifier-ui/home),
so the build is easy to tell apart from the reference app. Nothing replaces
the EUDI logo:

- the home header shows the EUDI logo at 36dp and the gov.gr BETA logo beside
  it at 44dp, the larger of the two (`AppIconAndText`)
- the splash shows it beneath the EUDI mark (`SplashScreen`)
- the `dev` launcher icon follows the official Gov.gr Wallet icon's layout:
  the same steep diagonal, the EUDI mark on white where that icon shows ID
  cards, and the emblem of the Hellenic Republic, in white, on gov.gr blue in
  the same position
  (`resources-logic/src/dev/res/drawable/ic_launcher_*_grnet.xml`)

The drawables are converted from the verifier UI's `assets/logo_govgr_pos.svg`
with the paths, colours and fill rules unchanged, per the
[gov.gr brand guide](https://guide.services.gov.gr/docs/brand): no distortion,
cropping or recolouring.

The launcher's emblem is taken unmodified from the gov.gr design system's logo
for dark backgrounds,
[govgr-logo.svg](https://guide.services.gov.gr/assets/files/govgr-logo-fa78bc13be038eeb3bf10456fd8ece3b.svg):
white, with the cross painted solid. The logo for light backgrounds leaves the
cross unpainted, which on blue shows as a blue hole. It sits on gov.gr blue,
`#003476`. The icon's layers, bottom to top: white, the EUDI mark, the blue,
the emblem. At the official icon's position the emblem reaches slightly past
the adaptive icon's 66dp safe zone, though still inside a full circle mask.

The properties become `BuildConfig.ISSUER_URLS` and
`BuildConfig.WALLET_PROVIDER_URL`, set in
`build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt` and
read by `core-logic/src/dev/.../WalletCoreConfigImpl.kt`.

**TS12 card payments are accepted as transaction data**, for the WE BUILD PA2
payment demo: a relying party sends `transaction_data` of type
`urn:eudi:sca:payment:1` with the payment credential's query, the wallet shows
the payment before Share, and wallet-core binds its hash into the key binding
JWT. wallet-core declares only the QES types, so the app declares this one
(`core-logic/.../transactiondata/ScaPayment.kt`) and registers it in the `dev`
flavour beside them, which also lets the transaction log read payments back.

- The payload is read leniently: TS12's members this wallet does not show are
  ignored, but payee name, a numeric amount and currency are required, and a
  request without them is rejected as `invalid_transaction_data`.
- The request screen shows "Payment to approve" expanded, payee and amount
  first, with no eIDAS trust framework row (`TransactionDataTransformer`).
- The amount is shown with its ISO 4217 code in the currency's minor units,
  e.g. `38.00 EUR`, whatever the device's locale, and is never rounded.
- No `amr` claim yet: it has to report the factors actually used at unlock.

## Building for the demo

    ./gradlew assembleDevRelease \
        -PissuerUrls=https://demo.eudiw.grnet.gr/frontend \
        -PwalletProviderUrl=https://demo.eudiw.grnet.gr/wallet-provider

The issuer URL is the issuer's **frontend**, not the backend at `/issuer`. As
upstream designed it, the frontend is the credential issuer a wallet talks to:
it serves the signed metadata this app requires, and its metadata sends the
credential requests on to the backend. The backend's own metadata is unsigned,
so pointed there the app shows "Issuance blocked".

Release signing reads a keystore from `sign` at the repository root, and its
alias and password from `androidKeyAlias` and `androidKeyPassword` in
`local.properties`, or else from `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`,
as upstream's does. Both files are git-ignored. Keep one keystore for every
build: an APK signed with a different key cannot be installed over the previous
one.

The keystore is GRNET's, generated 2026-09-28: alias `grnet-eudi-wallet`, RSA
4096, valid to 2056, certificate SHA-256
`03:BC:66:5B:AC:9C:9F:24:BE:95:93:81:AC:24:2D:8A:47:EC:05:30:35:8D:53:AE:D4:EC:89:72:7D:23:AB:12`.
GitHub secrets cannot be read back, so the copy in the repository's secrets is
not a backup; keep the file and its password safe elsewhere.

`-PversionCode` sets the version code, 1 if not given. An APK installs over
another only if its version code is not lower.

## Releases

`.github/workflows/apk-build.yml` builds the `devDebug` APK, debuggable and not
minified, so stack traces from users' phones are readable. With
`-PsignDebugWithReleaseKey=true` it is signed with GRNET's keystore, like a
release, so each APK installs over the last. Only
`grnet` and `v*` tags are published, so the releases page holds nothing but
builds meant for use:

- a push to `grnet`, which is what merging a pull request is, replaces the
  release `latest-grnet`
- a `v*` tag makes a permanent release
- a manual run on any other branch attaches the APK to the run, under the
  run's artifacts, for testing a branch before it is merged. Artifacts need a
  GitHub login and are kept 90 days.

The release marked Latest is `latest-grnet` until the first `v*` tag; after
that it is the newest tag. So this link is always the build to install:
`https://github.com/grnet/eudi-app-android-wallet-ui/releases/latest/download/eudi-wallet-gr.apk`

Each release carries `eudi-wallet-gr.apk`, its SHA-256, and in its notes the
commit, the endpoints it was built for, the wallet-core version and where it
came from, and the signing certificate. The version code is the workflow's run
number, and the version name is `<year>.<month>.<run>-<commit>`.

The workflow needs these repository settings:

| Name | Kind | What |
| --- | --- | --- |
| `ANDROID_KEYSTORE_B64` | secret | the keystore `sign`, base64 |
| `ANDROID_KEY_ALIAS` | variable | its alias, `grnet-eudi-wallet`; not secret |
| `ANDROID_KEY_PASSWORD` | secret | its password, for the store and the key |
| `ISSUER_URLS` | variable | the `issuerUrls` build argument |
| `WALLET_PROVIDER_URL` | variable | the `walletProviderUrl` build argument |

The published APK is signed with a different key from the debug builds of
`install-debug.sh`, which use the default debug key, under the same package
name, so a phone holds one or the other: uninstall the one to install the other.

## Worth knowing

**The issuance redirect is shared with the official app.** Both register
`eu.europa.ec.euidi://authorization`, the redirect the authorization server
sends the browser back to, so with both installed Android may ask which app
should handle it. The scheme is left as is because the authorization server
must accept that redirect exactly.

**Issuers are trusted through the EU Trusted Lists, then GRNET's own CAs.**
Upstream's `dev` flavour trusts only the EU test lists at
`trustedlist.serviceproviders.eudiw.dev`, and requires an issuer's metadata to be
signed by a certificate on them. GRNET's CAs are not, so upstream's app blocks
our issuer with "Issuance blocked" before sending it anything. This flavour
tries the EU lists first and falls back to two anchors bundled in
`resources-logic/src/dev/res/raw/`:

| Anchor | Trusted for |
| --- | --- |
| `grnet_iaca.pem`, the IACA in `WEBUILD/pki` | PIDs, and the issuer's signed metadata |
| `webuild_trust_registry.pem`, the WE BUILD Trust Registry root (WP4 Group 5) | the issuer's signed metadata |

The issuer signs both its PIDs and its metadata with a document signer under
the IACA. The WE BUILD root covers access certificates issued by the Trust
Registry, such as the one onboarded for GRNET.

This applies to issuance only. Presentation, and the status list's signature,
still go through wallet-core's own trust, the EU lists alone; the status list is
`INFORM`, so it does not block.

wallet-core builds its trusted-list source internally and does not expose it, so
`core-logic/src/dev/.../GrnetTrust.kt` rebuilds the same pipeline from the same
ETSI library and hands the combined source to `configureIssuerTrust`. The EU
lists are downloaded twice as a result, once by each.

**When the IACA is reissued**, replace `grnet_iaca.pem` with the new
`ca/root-ca-grnet.pem` and release a new build: until then the app rejects
everything under the new root.

**The wallet provider does not check the app's identity yet.** Its platform
key attestation validation is disabled. If it is enabled, it must list this
build's package, `eu.europa.ec.euidi.grnet.dev`, and the SHA-256 digest of
**our** signing certificate, not upstream's.

**The wallet-core library is GRNET's release of it**, `0.31.0-grnet.1`, from
the Maven repository of
[grnet/eudi-lib-android-wallet-core](https://github.com/grnet/eudi-lib-android-wallet-core)
on GitHub Pages. It has no functional changes from upstream `0.31.0` yet; the
fork exists so GRNET changes can ship as releases. `settings.gradle.kts` takes
its three artifacts, in `*-grnet.N` versions, from that repository only, and
`eudiWalletCore` in `gradle/libs.versions.toml` pins the version. To build
against a local build of the library, pass `-PgrnetMavenUrl=file:///path/to/repo`;
the library's own README says how to produce one.
