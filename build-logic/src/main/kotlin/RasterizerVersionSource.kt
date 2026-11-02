// SPDX-License-Identifier: MPL-2.0
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

abstract class RasterizerVersionSource : ValueSource<String, RasterizerVersionSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val executable: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(parameters.executable.get(), "--version")
            standardOutput = output
        }
        return output.toString(Charsets.UTF_8).trim()
    }
}
