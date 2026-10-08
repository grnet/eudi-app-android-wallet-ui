/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

pluginManagement {
    val toolChainResolverVersion = extra["toolChainResolverVersion"] as String
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version toolChainResolverVersion
    }
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
        }
        mavenLocal()
        // GRNET fork: GRNET's wallet-core releases (versions *-grnet.N) come from
        // its own Maven repository, and only from there. See .github/README.md.
        exclusiveContent {
            forRepository {
                maven {
                    name = "grnetWalletCore"
                    url = uri(
                        providers.gradleProperty("grnetMavenUrl")
                            .getOrElse("https://grnet.github.io/eudi-lib-android-wallet-core/maven/")
                    )
                }
            }
            filter {
                listOf(
                    "eudi-lib-android-wallet-core",
                    "eudi-lib-android-wallet-document-manager",
                    "eudi-lib-android-iso18013-data-transfer",
                ).forEach { includeVersionByRegex("eu\\.europa\\.ec\\.eudi", it, ".*-grnet\\..*") }
            }
        }
        // GRNET fork: GRNET's releases of the OpenID4VCI library, which GRNET's wallet-core
        // depends on, come from that library's own Maven repository, and only from there.
        exclusiveContent {
            forRepository {
                maven {
                    name = "grnetOpenId4Vci"
                    url = uri(
                        providers.gradleProperty("grnetOpenId4VciMavenUrl")
                            .getOrElse("https://grnet.github.io/eudi-lib-jvm-openid4vci-kt/maven/")
                    )
                }
            }
            filter {
                includeVersionByRegex("eu\\.europa\\.ec\\.eudi", "eudi-lib-jvm-openid4vci-kt", ".*-grnet\\..*")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "EUDI Wallet"
include(":app")
include(":business-logic")
include(":ui-logic")
include(":network-logic")
include(":resources-logic")
include(":assembly-logic")
include(":startup-feature")
include(":test-logic")
include(":test-feature")
include(":common-feature")
include(":dashboard-feature")
include(":presentation-feature")
include(":proximity-feature")
include(":issuance-feature")
include(":analytics-logic")
include(":baseline-profile")
include(":authentication-logic")
include(":core-logic")
include(":storage-logic")
