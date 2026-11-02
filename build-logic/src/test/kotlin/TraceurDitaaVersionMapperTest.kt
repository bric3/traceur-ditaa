// SPDX-License-Identifier: MPL-2.0
import com.javiersc.gradle.version.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.tabletest.junit.TableTest
import org.tabletest.junit.TypeConverter

class TraceurDitaaVersionMapperTest {
    @TableTest("""
        Snapshot | Version                    | Expected
        false    | 0.0.1.0+abcdef0             | 0.0.1
        false    | 0.0.1.1+abcdef0             | 0.0.2-SNAPSHOT
        false    | 0.0.1.0+DIRTY               | 0.0.2-SNAPSHOT
        false    | 0.0.2-rc.1.0+abcdef0        | 0.0.2-rc.1
        false    | 0.0.2-rc.1.1+abcdef0        | 0.0.2-SNAPSHOT
        false    | 0.0.2.1+abcdef0             | 0.0.3-SNAPSHOT
        false    | 1.0.9.12+abcdef0+DIRTY      | 1.0.10-SNAPSHOT
        true     | 0.0.1.0+abcdef0             | 0.0.2-SNAPSHOT
        true     | 0.0.2-rc.1.0+abcdef0        | 0.0.2-SNAPSHOT
        true     | 0.0.2.0+abcdef0             | 0.0.3-SNAPSHOT
    """)
    fun `development versions follow the next final release`(snapshot: Boolean, version: GradleVersion, expected: String) {
        assertEquals(expected, TraceurDitaaVersionMapper(snapshot).map(version))
    }

    @TableTest("""
        Tag           | Prerelease | Expected
        v0.0.2-rc.1   | true       | 0.0.2-rc.1
        v0.0.2        | false      | 0.0.2
        v0.0.2        |            | 0.0.2
    """)
    fun `release tags supply the exact version`(tag: String, prerelease: Boolean?, expected: String) {
        assertEquals(expected, TraceurDitaaVersionMapper.releaseVersion(tag, prerelease))
    }

    @TableTest("""
        Tag              | Prerelease
        v0.0.2-rc.1      | false
        v0.0.2           | true
        0.0.2            | false
        v0.0.2-SNAPSHOT  | true
        v0.0.2-snapshot  | true
        v0.0.2+abcdef0   | false
    """)
    fun `release tags and prerelease flags must agree`(tag: String, prerelease: Boolean) {
        assertThrows(IllegalArgumentException::class.java) {
            TraceurDitaaVersionMapper.releaseVersion(tag, prerelease)
        }
    }

    companion object {
        @JvmStatic
        @TypeConverter
        fun version(value: String): GradleVersion = GradleVersion(value)
    }
}
