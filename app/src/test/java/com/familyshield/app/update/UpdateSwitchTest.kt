package com.familyshield.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSwitchTest {

    @Test
    fun on_when_first_real_line_says_on() {
        assertTrue(UpdateSwitch.isOn("# 설명\n# 켜기 또는 끄기\n켜기\n"))
    }

    @Test
    fun off_when_first_real_line_says_off() {
        assertFalse(UpdateSwitch.isOn("# 설명\n끄기\n"))
    }

    @Test
    fun ignores_spaces_around_the_word() {
        assertTrue(UpdateSwitch.isOn("\n   켜기   \n"))
    }

    @Test
    fun anything_unclear_counts_as_off() {
        assertFalse(UpdateSwitch.isOn(""))
        assertFalse(UpdateSwitch.isOn("# 켜기\n"))
        assertFalse(UpdateSwitch.isOn("켜기로 할까?\n"))
        assertFalse(UpdateSwitch.isOn("<html>켜기</html>\n"))
    }

    @Test
    fun accepts_english_on_off() {
        assertTrue(UpdateSwitch.isOn("ON\n"))
        assertEquals(false, UpdateSwitch.isOn("off\n"))
    }
}
