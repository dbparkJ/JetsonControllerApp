package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit device test: the installed app carries the NAVER Maps client its map screens use.
 * NAVER checks the client against the package names registered in the NCP console when a map opens.
 */
class NaverMapConfigurationTest {
    @Test fun bundledConfigurationNamesANaverMapsClient() {
        assertTrue("The deployed app must contain its NAVER Maps client ID", BuildConfig.NAVER_MAP_CLIENT_ID.isNotBlank())
    }
}
