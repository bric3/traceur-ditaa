// SPDX-License-Identifier: MPL-2.0
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TestSlotsConventionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `slots partition the manifest and reuse the configuration cache`() {
        Files.writeString(directory.resolve("settings.gradle.kts"), "rootProject.name = \"slot-fixture\"")
        Files.writeString(directory.resolve("build.gradle.kts"), """
            plugins { id("traceur.test-slots") }
            tasks.named<SelectTestSlot>("selectTestSlot") {
                manifest = layout.projectDirectory.file("manifest.tsv")
            }
        """.trimIndent())
        val rows = (0..15).map { it.toString(16).padStart(8, '0') + "0".repeat(56) + "\tprovenance-$it" }
        Files.writeString(directory.resolve("manifest.tsv"), (listOf("id\tsource") + rows).joinToString("\n", postfix = "\n"))
        val runner = GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
        fun run(vararg args: String) = runner.withArguments("selectTestSlot", "--configuration-cache", "--build-cache", *args).build()
        fun selected() = Files.readAllLines(directory.resolve("build/test-slots/manifest.tsv")).drop(1)
        run()
        assertEquals(rows, selected())
        val partitioned = (0..2).flatMap {
            run("-PtestSlot=$it/3")
            selected()
        }
        assertEquals(rows.sorted(), partitioned.sorted())
        val cached = run("-PtestSlot=2/3")
        assertTrue(cached.output.contains("Reusing configuration cache."))
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":selectTestSlot")!!.outcome)
        Files.delete(directory.resolve("build/test-slots/manifest.tsv"))
        val restored = run("-PtestSlot=2/3")
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":selectTestSlot")!!.outcome)
        assertEquals(rows.filterIndexed { index, _ -> index % 3 == 2 }, selected())
        run("-PtestInput=${rows[5].substringBefore('\t')}")
        assertEquals(listOf(rows[5]), selected())
        runner.withArguments("selectTestSlot", "-PtestSlot=4/4").buildAndFail()
        runner.withArguments("selectTestSlot", "-PtestInput=missing").buildAndFail()
        run("-PtestSlot=16/17")
        assertTrue(selected().isEmpty())
        Files.writeString(directory.resolve("manifest.tsv"), (listOf("id\tsource") + rows.reversed()).joinToString("\n", postfix = "\n"))
        run("-PtestSlot=1/3")
        assertEquals(rows.filterIndexed { index, _ -> index % 3 == 1 }.toSet(), selected().toSet())
        Files.writeString(directory.resolve("manifest.tsv"), "id\tsource\ninvalid\tprovenance\n")
        runner.withArguments("selectTestSlot").buildAndFail()
    }
}
