// SPDX-License-Identifier: MPL-2.0
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class QualityConventionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `licensing preserves directives copyright and reference fixtures`() {
        write("settings.gradle.kts", "rootProject.name = \"quality-fixture\"\ndependencyResolutionManagement { repositories { mavenCentral() } }")
        write("build.gradle.kts", "plugins { id(\"traceur.quality\") }")
        write("gradle/libs.versions.toml", """
            [versions]
            prince-of-space = "2.3.0"
            tabletest-formatter = "1.1.2"
        """.trimIndent())
        val directives = "//usr/bin/env jbang \"\$0\" \"\$@\" ; exit \$?\n//JAVA 25\n"
        val copyright = "// Copyright (c) 2025 Brice Dutheil\n"
        val body = "class Example { String value = \"\\u0000\"; }\n"
        write("tools/Example.java", directives + body)
        write("src/main/java/Example.java", "// SPDX-License-Identifier: Apache-2.0\n" + copyright + body)
        val block = "/* SPDX-License-Identifier: MPL-2.0 */\n" + body
        write("src/test/java/Example.java", block)
        val fixture = "<svg>reference fixture without a source header</svg>"
        write("src/test/resources/reference.svg", fixture)
        val runner = GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
        runner.withArguments("checkLicenses", "--stacktrace").buildAndFail()
        runner.withArguments("applyLicenses", "--stacktrace").build()
        val tool = Files.readString(directory.resolve("tools/Example.java"))
        assertEquals(directives + "// SPDX-License-Identifier: MPL-2.0\n" + body, tool)
        assertTrue(Files.readString(directory.resolve("src/main/java/Example.java")).endsWith(copyright + body))
        assertEquals(block, Files.readString(directory.resolve("src/test/java/Example.java")))
        assertEquals(fixture, Files.readString(directory.resolve("src/test/resources/reference.svg")))
        runner.withArguments("applyLicenses", "--stacktrace").build()
        runner.withArguments("checkLicenses", "--configuration-cache", "--stacktrace").build()
        val second = runner.withArguments("checkLicenses", "--configuration-cache", "--stacktrace").build()
        assertTrue(second.output.contains("Reusing configuration cache."))
        assertEquals(tool, Files.readString(directory.resolve("tools/Example.java")))
        runner.withArguments("spotlessApply", "--stacktrace").build()
        val formatted = Files.readString(directory.resolve("tools/Example.java"))
        assertTrue(formatted.startsWith(directives))
        assertTrue(formatted.contains("\\u0000"))
        assertTrue('\u0000' !in formatted)
    }

    private fun write(name: String, content: String) {
        val file = directory.resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }
}
