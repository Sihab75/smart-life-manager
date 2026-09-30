package com.example.personal_financestudydaily_routine_assistant

import com.example.personal_financestudydaily_routine_assistant.data.location.normalizeBangladeshPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSharingPhoneTest {
    @Test
    fun normalizesSupportedBangladeshFormats() {
        assertEquals("+8801712345678", normalizeBangladeshPhone("01712345678"))
        assertEquals("+8801712345678", normalizeBangladeshPhone("+880 1712-345678"))
        assertEquals("+8801712345678", normalizeBangladeshPhone("8801712345678"))
    }

    @Test
    fun rejectsInvalidAndNonBangladeshNumbers() {
        assertNull(normalizeBangladeshPhone(""))
        assertNull(normalizeBangladeshPhone("0171234567"))
        assertNull(normalizeBangladeshPhone("01212345678"))
        assertNull(normalizeBangladeshPhone("+14155550123"))
    }
}
