// SPDX-License-Identifier: MPL-2.0
import com.javiersc.gradle.version.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TraceurDitaaVersionMapperTest {
    @Test
    fun `development versions follow the next final release`() {
        val mapper = TraceurDitaaVersionMapper(snapshot = false)
        assertEquals("0.0.1", mapper.map(GradleVersion("0.0.1.0+abcdef0")))
        assertEquals("0.0.2-SNAPSHOT", mapper.map(GradleVersion("0.0.1.1+abcdef0")))
        assertEquals("0.0.2-SNAPSHOT", mapper.map(GradleVersion("0.0.1.0+DIRTY")))
        assertEquals("0.0.2-rc.1", mapper.map(GradleVersion("0.0.2-rc.1.0+abcdef0")))
        assertEquals("0.0.2-SNAPSHOT", mapper.map(GradleVersion("0.0.2-rc.1.1+abcdef0")))
        assertEquals("0.0.3-SNAPSHOT", mapper.map(GradleVersion("0.0.2.1+abcdef0")))
        assertEquals("1.0.10-SNAPSHOT", mapper.map(GradleVersion("1.0.9.12+abcdef0+DIRTY")))
    }

    @Test
    fun `main builds use snapshots at release tags`() {
        val mapper = TraceurDitaaVersionMapper(snapshot = true)
        assertEquals("0.0.2-SNAPSHOT", mapper.map(GradleVersion("0.0.1.0+abcdef0")))
        assertEquals("0.0.2-SNAPSHOT", mapper.map(GradleVersion("0.0.2-rc.1.0+abcdef0")))
        assertEquals("0.0.3-SNAPSHOT", mapper.map(GradleVersion("0.0.2.0+abcdef0")))
    }

    @Test
    fun `release tags supply the exact version`() {
        assertEquals("0.0.2-rc.1", TraceurDitaaVersionMapper.releaseVersion("v0.0.2-rc.1", true))
        assertEquals("0.0.2", TraceurDitaaVersionMapper.releaseVersion("v0.0.2", false))
        assertEquals("0.0.2", TraceurDitaaVersionMapper.releaseVersion("v0.0.2", null))
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("v0.0.2-rc.1", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("v0.0.2", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("0.0.2", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("v0.0.2-SNAPSHOT", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("v0.0.2-snapshot", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion("v0.0.2+abcdef0", false)
        }
    }
}
