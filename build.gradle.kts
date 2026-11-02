// SPDX-License-Identifier: MPL-2.0
plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("traceur.quality")
    id("traceur.test-slots")
}

group = "io.github.bric3.traceur"

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
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.tabletest.junit)
    testImplementation(libs.jsvg)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, tasks.named<Test>("testJava11"))
    executionData(layout.buildDirectory.file("jacoco/test.exec"), layout.buildDirectory.file("jacoco/testJava11.exec"))
    reports {
        xml.required = true
        csv.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, tasks.named<Test>("testJava11"))
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

val selectTestSlot = tasks.named<SelectTestSlot>("selectTestSlot") {
    manifest = layout.projectDirectory.file("corpus/manifest.tsv")
}

val corpusArguments = objects.newInstance<CorpusTestArguments>().apply {
    corpus = layout.projectDirectory.dir(providers.gradleProperty("corpus").orElse("build/corpus/inputs"))
    manifest = selectTestSlot.flatMap { it.selectedManifest }
    pngReference = layout.projectDirectory.file(providers.gradleProperty("pngReference").orElse("build/references/plantuml.jar"))
    svgReference = layout.projectDirectory.file(providers.gradleProperty("svgReference").orElse("build/references/ditaa.jar"))
    rasterizer = providers.gradleProperty("rasterizer").orElse("rsvg-convert")
    rendererClasspath.from(tools.runtimeClasspath)
    report = layout.buildDirectory.dir(providers.gradleProperty("testSlot").orElse("0/1")
        .zip(providers.gradleProperty("testInput").orElse("all")) { slot, input ->
            "comparison/${slot.replace('/', '-')}/$input"
        })
}

val rasterizerVersion = providers.of(RasterizerVersionSource::class) {
    parameters.executable = corpusArguments.rasterizer
}
val renderingFonts = files(
    fileTree("/usr/share/fonts"), fileTree("/usr/local/share/fonts"), fileTree("/etc/fonts"),
    fileTree("/System/Library/Fonts"), fileTree("/Library/Fonts"),
    fileTree(providers.systemProperty("user.home").map { "$it/Library/Fonts" }),
    fileTree(providers.systemProperty("user.home").map { "$it/.fonts" }),
    fileTree(providers.environmentVariable("XDG_DATA_HOME")
        .orElse(providers.systemProperty("user.home").map { "$it/.local/share" }).map { "$it/fonts" })
)

testing {
    suites {
        register<JvmTestSuite>("corpusTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(project())
                implementation(tools.output)
            }
            targets.all {
                testTask.configure {
                    description = "Compares the selected corpus inputs with the reference renderers."
                    jvmArgumentProviders.add(corpusArguments)
                    outputs.dir(corpusArguments.report).withPropertyName("comparisonReport")
                    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
                    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
                    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
                    systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", "4")
                    inputs.property("testSlot", providers.gradleProperty("testSlot").orElse("0/1"))
                    inputs.property("renderingOs", providers.systemProperty("os.name"))
                    inputs.property("renderingOsVersion", providers.systemProperty("os.version"))
                    inputs.property("renderingArchitecture", providers.systemProperty("os.arch"))
                    inputs.property("runnerImage", providers.environmentVariable("ImageVersion").orElse(""))
                    inputs.property("rasterizerVersion", rasterizerVersion)
                    inputs.files(renderingFonts).withPropertyName("renderingFonts")
                        .withPathSensitivity(PathSensitivity.NONE)
                    inputs.file(javaLauncher.map { it.metadata.installationPath.file("release") })
                        .withPropertyName("renderingJdk").withPathSensitivity(PathSensitivity.NONE)
                }
            }
        }
    }
}

tasks.register<JavaExec>("benchmarkColdStart") {
    description = "Measures fresh JVM rendering, including JVM startup and output encoding."
    classpath = tools.runtimeClasspath
    mainClass = "ColdStartBenchmark"
    providers.gradleProperty("pngReference").orNull?.let { args(it) }
    providers.gradleProperty("svgReference").orNull?.let { args(it) }
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
