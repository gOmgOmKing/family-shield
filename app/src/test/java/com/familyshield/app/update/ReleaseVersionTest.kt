package com.familyshield.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun reads_version_number_with_surrounding_whitespace() {
        assertEquals(7, ReleaseVersion.parse(" 7\n"))
    }

    @Test
    fun rejects_text_that_is_not_a_version_number() {
        assertNull(ReleaseVersion.parse("<html>Not Found</html>"))
        assertNull(ReleaseVersion.parse(""))
        assertNull(ReleaseVersion.parse("-3"))
        assertNull(ReleaseVersion.parse("0"))
    }

    @Test
    fun newer_only_when_strictly_greater() {
        assertEquals(true, ReleaseVersion.isNewer(remote = 3, installed = 2))
        assertEquals(false, ReleaseVersion.isNewer(remote = 2, installed = 2))
        assertEquals(false, ReleaseVersion.isNewer(remote = 1, installed = 2))
    }
}
