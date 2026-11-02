// SPDX-License-Identifier: MPL-2.0
import dev.yumi.gradle.licenser.api.comment.HeaderComment

// Read only the SPDX notice so JBang directives and copyright lines remain intact.
class SpdxHeaderComment : HeaderComment {
    override fun readHeaderComment(source: String): HeaderComment.Result {
        val separator = extractLineSeparator(source)
        var at = 0
        while (at < source.length) {
            val end = source.indexOf('\n', at).let { if (it < 0) source.length else it }
            val line = source.substring(at, end).trimEnd('\r')
            val notice = when {
                line.startsWith("// SPDX-License-Identifier:") -> line.removePrefix("// ")
                line.startsWith("/* SPDX-License-Identifier:") && line.endsWith(" */") ->
                    line.removePrefix("/* ").removeSuffix(" */")
                else -> null
            }
            if (notice != null) return HeaderComment.Result(at, end, listOf(notice), separator)
            if (!line.startsWith("//") && line.isNotBlank()) break
            at = end + 1
        }
        return HeaderComment.Result(at.coerceAtMost(source.length), at.coerceAtMost(source.length), null, separator)
    }

    override fun writeHeaderComment(header: List<String>, separator: String): String =
        header.joinToString(separator) { "// $it" } + separator
}
