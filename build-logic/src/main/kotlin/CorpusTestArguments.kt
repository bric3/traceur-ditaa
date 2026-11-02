// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class CorpusTestArguments : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val corpus: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pngReference: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val svgReference: RegularFileProperty

    @get:Input
    abstract val rasterizer: Property<String>

    @get:Classpath
    abstract val rendererClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val report: DirectoryProperty

    override fun asArguments(): Iterable<String> = listOf(
        "-Dcorpus.directory=${corpus.get().asFile.absolutePath}",
        "-Dcorpus.manifest=${manifest.get().asFile.absolutePath}",
        "-Dcorpus.pngReference=${pngReference.get().asFile.absolutePath}",
        "-Dcorpus.svgReference=${svgReference.get().asFile.absolutePath}",
        "-Dcorpus.rasterizer=${rasterizer.get()}",
        "-Dcorpus.report=${report.get().asFile.absolutePath}",
        "-Dcorpus.rendererClasspath=${rendererClasspath.asPath}"
    )
}
