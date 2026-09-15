package com.example.jetsoncontroller.data.server

import java.net.URI

enum class ServerEnvironment(val wireValue: String) {
    DEVELOPMENT("development"),
    TEST("test"),
    PRODUCTION("production")
}

data class ServerEndpointProfile(
    val profileId: String,
    val displayName: String,
    val environment: ServerEnvironment,
    val baseUrl: String,
    val employeeId: String,
    val projectId: String
) {
    fun validated(): ServerEndpointProfile {
        require(profileId.matches(ACCESS_ID)) { "Invalid server profile ID" }
        require(employeeId.matches(ACCESS_ID)) { "Invalid employee ID" }
        require(projectId.matches(ACCESS_ID)) { "Invalid project ID" }
        require(displayName.isNotBlank() && displayName.length <= 128) {
            "Invalid server profile name"
        }
        val uri = URI(baseUrl)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Server URL must use HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid server URL" }
        require(uri.query == null && uri.fragment == null) { "Invalid server URL" }
        val path = uri.path.orEmpty()
        require(path.isEmpty() || path == "/") { "Server URL must not contain a path" }
        val normalized = URI("https", null, uri.host.lowercase(), uri.port, "/", null, null)
            .toASCIIString()
        return copy(
            displayName = displayName.trim(),
            baseUrl = normalized,
            employeeId = employeeId.lowercase(),
            projectId = projectId.lowercase()
        )
    }

    companion object {
        private val ACCESS_ID = Regex("^[a-z0-9][a-z0-9_.-]{0,63}$")
    }
}
