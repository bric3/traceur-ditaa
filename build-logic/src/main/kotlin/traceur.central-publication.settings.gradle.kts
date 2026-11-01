// SPDX-License-Identifier: MPL-2.0
import nmcp.NmcpSettings
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

plugins {
    id("com.gradleup.nmcp.settings")
}

extensions.configure<NmcpSettings> {
    centralPortal {
        // Resolve settings providers before Nmcp isolates its project lifecycle callback.
        username.set(providers.gradleProperty("mavenCentralUsername").orNull)
        password.set(providers.gradleProperty("mavenCentralPassword").orNull)
        publishingType = "AUTOMATIC"
    }
}

gradle.lifecycle.beforeProject {
    pluginManager.withPlugin("maven-publish") {
        pluginManager.apply("signing")
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(
                providers.gradleProperty("signingKey").orNull,
                providers.gradleProperty("signingPassword").orNull,
            )
            setRequired(!version.toString().endsWith("-SNAPSHOT"))
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}
