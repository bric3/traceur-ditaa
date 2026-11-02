// SPDX-License-Identifier: MPL-2.0
plugins {
    `kotlin-dsl`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// Gradle's embedded Kotlin compiler supports JVM targets through Java 24.
kotlin.compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
tasks.withType<JavaCompile>().configureEach { options.release = 24 }

dependencies {
    implementation(libs.nmcp)
    implementation(libs.semver.plugin)
    implementation(libs.spotless.plugin)
    implementation(libs.yumi.licenser.plugin)
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.tabletest.junit)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}
