// SPDX-License-Identifier: MPL-2.0
plugins {
    `java-library`
    `maven-publish`
    jacoco
}

group = "io.github.bric3.traceur"

repositories { mavenCentral() }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    systemProperty("rasterizer", providers.gradleProperty("rasterizer").getOrElse("rsvg-convert"))
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.withType<Test>())
    executionData(layout.buildDirectory.file("jacoco/test.exec"), layout.buildDirectory.file("jacoco/testJava11.exec"))
    reports {
        xml.required = true
        csv.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.withType<Test>())
    executionData(layout.buildDirectory.file("jacoco/test.exec"), layout.buildDirectory.file("jacoco/testJava11.exec"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.99".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.97".toBigDecimal()
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
    manifest.attributes("Implementation-Version" to project.version.toString())
}

tasks.register<Test>("testJava11") {
    description = "Runs the library tests on Java 11."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(11) }
}

tasks.check {
    dependsOn("testJava11", tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification,
        gradle.includedBuild("build-logic").task(":check"))
}

val tools by sourceSets.creating
tools.compileClasspath += sourceSets.main.get().output
tools.runtimeClasspath += sourceSets.main.get().output
sourceSets.test {
    compileClasspath += tools.output
    runtimeClasspath += tools.output
}

tasks.register<JavaExec>("compareCorpus") {
    description = "Compares every corpus input with an isolated PlantUML binary."
    classpath = tools.runtimeClasspath
    mainClass = "CorpusComparison"
    systemProperty("java.awt.headless", "true")
    providers.gradleProperty("corpus").orNull?.let { args("--corpus", it) }
    providers.gradleProperty("oracle").orNull?.let { args("--oracle", it) }
    providers.gradleProperty("svgOracle").orNull?.let { args("--svg-oracle", it) }
    args("--rasterizer", providers.gradleProperty("rasterizer").getOrElse("rsvg-convert"))
    args("--report", layout.buildDirectory.dir("comparison").get().asFile.absolutePath)
}

tasks.register<JavaExec>("benchmarkColdStart") {
    description = "Measures fresh JVM rendering, including JVM startup and output encoding."
    classpath = tools.runtimeClasspath
    mainClass = "ColdStartBenchmark"
    providers.gradleProperty("oracle").orNull?.let { args(it) }
    providers.gradleProperty("svgOracle").orNull?.let { args(it) }
}

publishing {
    publications.create<MavenPublication>("library") {
        from(components["java"])
        pom {
            name = "Traceur Ditaa"
            description = "An independent, MPL-2.0 renderer for ditaa diagrams"
            url = "https://github.com/bric3/traceur-ditaa"
            scm {
                connection = "scm:git:https://github.com/bric3/traceur-ditaa.git"
                developerConnection = "scm:git:ssh://git@github.com/bric3/traceur-ditaa.git"
                url = "https://github.com/bric3/traceur-ditaa"
            }
            licenses {
                license {
                    name = "Mozilla Public License 2.0"
                    url = "https://www.mozilla.org/MPL/2.0/"
                }
            }
            developers {
                developer {
                    id = "bric3"
                    name = "Brice Dutheil"
                    url = "https://github.com/bric3"
                }
            }
        }
    }
}
