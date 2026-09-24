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

## Building for the demo

    ./gradlew assembleDevRelease \
        -PissuerUrls=https://demo.eudiw.grnet.gr/issuer \
        -PwalletProviderUrl=https://demo.eudiw.grnet.gr/wallet-provider

Release signing reads a keystore from `sign` at the repository root, and its
alias and password from `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, as
upstream's does. Keep one keystore for every build: an APK signed with a
different key cannot be installed over the previous one.

## Worth knowing

**The issuance redirect is shared with the official app.** Both register
`eu.europa.ec.euidi://authorization`, the redirect the authorization server
sends the browser back to, so with both installed Android may ask which app
should handle it. The scheme is left as is because the authorization server
must accept that redirect exactly.

**Issuer trust comes from the EU Trusted Lists.** The `dev` flavour, like
`demo`, configures `configureEtsiTrust` with
`trustedlist.serviceproviders.eudiw.dev`. GRNET's IACA is not on those lists,
so how the app treats PIDs from our issuer is still to be checked on a device.

**The wallet provider does not check the app's identity yet.** Its platform
key attestation validation is disabled. If it is enabled, it must list this
build's package, `eu.europa.ec.euidi.grnet.dev`, and the SHA-256 digest of
**our** signing certificate, not upstream's.

**The wallet-core library is upstream's**, from Maven Central. The
`v0.29.0-grnet` build in `grnet/eudi-lib-android-wallet-core` is not needed:
its one change relaxes the HAIP rule that presentation responses be
encrypted, and our verifier asks for `direct_post.jwt` anyway.
