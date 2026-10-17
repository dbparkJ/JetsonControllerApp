package com.example.jetsoncontroller.ui.storage

import org.junit.Assert.assertNull
import org.junit.Test

class DirectServerDefaultsTest {

    @Test
    fun `no server environment is preselected in the profile form`() {
        assertNull(defaultServerEnvironmentChoice())
    }
}
