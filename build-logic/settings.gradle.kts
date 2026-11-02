// SPDX-License-Identifier: MPL-2.0
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter {
                includeGroup("com.javiersc.semver")
                includeGroup("dev.yumi.gradle.licenser")
                includeGroup("dev.yumi")
            }
        }
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
