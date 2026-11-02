// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal data class TestSlot(val index: Int, val count: Int) {
    companion object {
        fun parse(value: String): TestSlot {
            val fields = value.split('/')
            require(fields.size == 2) { "Test slot must be INDEX/COUNT: $value" }
            val index = fields[0].toIntOrNull()
            val count = fields[1].toIntOrNull()
            require(index != null && count != null && count > 0 && index in 0 until count) {
                "Invalid test slot: $value (indices start at zero)"
            }
            return TestSlot(index, count)
        }
    }

    fun includes(id: String): Boolean = id.take(8).toLong(16) % count == index.toLong()
}

/** Selects manifest rows by their SHA-256 ID, preserving the header and provenance. */
@CacheableTask
abstract class SelectTestSlot : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val slot: Property<String>

    @get:Input
    abstract val testInput: Property<String>

    @get:OutputFile
    abstract val selectedManifest: RegularFileProperty

    @TaskAction
    fun select() {
        val selection = TestSlot.parse(slot.get())
        val rows = manifest.get().asFile.readLines()
        require(rows.isNotEmpty() && rows.first().startsWith("id\t")) { "Missing manifest header" }
        val requested = testInput.get()
        val ids = rows.drop(1).map { it.substringBefore('\t') }
        val idPattern = Regex("[0-9a-f]{64}")
        require(ids.isNotEmpty() && ids.all { it.matches(idPattern) }) {
            "Manifest must contain SHA-256 test IDs"
        }
        require(requested.isEmpty() || requested in ids) { "Unknown test input: $requested" }
        val selected = rows.drop(1).filter {
            val id = it.substringBefore('\t')
            (requested.isEmpty() || requested == id) && selection.includes(id)
        }
        val output = selectedManifest.get().asFile
        output.parentFile.mkdirs()
        output.writeText((listOf(rows.first()) + selected).joinToString("\n", postfix = "\n"))
        logger.lifecycle("Selected ${selected.size} manifest rows for slot ${slot.get()}")
    }
}
