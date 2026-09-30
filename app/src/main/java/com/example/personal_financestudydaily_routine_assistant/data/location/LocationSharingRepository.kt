package com.example.personal_financestudydaily_routine_assistant.data.location

import com.example.personal_financestudydaily_routine_assistant.BuildConfig
import com.example.personal_financestudydaily_routine_assistant.data.cloud.FirebaseCloudService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SharedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val updatedAtMillis: Long
)

data class LocationRequest(
    val requestId: String,
    val targetUserId: String,
    val targetName: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val status: String,
    val location: SharedLocation?
)

data class CreatedLocationRequest(
    val requestId: String,
    val shareUrl: String,
    val expiresAtMillis: Long
)

class LocationSharingRepository(
    private val cloud: FirebaseCloudService,
    baseUrl: String = BuildConfig.ASSISTANT_BASE_URL
) {
    private val apiBaseUrl = baseUrl.trimEnd('/')

    suspend fun createRequest(phoneNumber: String): CreatedLocationRequest {
        val response = request(
            method = "POST",
            path = "/api/location/requests",
            body = JSONObject().put("phoneNumber", phoneNumber)
        )
        return CreatedLocationRequest(
            requestId = response.getString("requestId"),
            shareUrl = response.getString("shareUrl"),
            expiresAtMillis = response.getLong("expiresAt")
        )
    }

    suspend fun getRequests(): List<LocationRequest> {
        val response = request("GET", "/api/location/requests")
        val requests = response.getJSONArray("requests")
        return List(requests.length()) { index ->
            val item = requests.getJSONObject(index)
            val locationJson = item.optJSONObject("location")
            LocationRequest(
                requestId = item.getString("requestId"),
                targetUserId = item.optString("targetUserId"),
                targetName = item.optString("targetName", "Location recipient"),
                createdAtMillis = item.getLong("createdAt"),
                expiresAtMillis = item.getLong("expiresAt"),
                status = item.getString("status"),
                location = locationJson?.let {
                    SharedLocation(
                        latitude = it.getDouble("latitude"),
                        longitude = it.getDouble("longitude"),
                        accuracyMeters = it.getDouble("accuracy"),
                        updatedAtMillis = it.getLong("updatedAt")
                    )
                }
            )
        }
    }

    suspend fun stopRequest(requestId: String) {
        request("POST", "/api/location/requests/${requestId.encodePathSegment()}/stop", JSONObject())
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val token = cloud.assistantIdToken(forceRefresh = true)
            ?: error("Sign in with Firebase to use location sharing.")
        val connection = URL("$apiBaseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (status !in 200..299) {
                val message = response.optString("error").ifBlank {
                    "Location sharing could not be completed."
                }
                throw LocationSharingException(
                    message = message,
                    code = response.optString("code")
                )
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encodePathSegment(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
}

class LocationSharingException(
    message: String,
    val code: String
) : Exception(message)

fun normalizeBangladeshPhone(value: String): String? {
    val compact = value.trim().replace(Regex("[\\s()-]"), "")
    val local = when {
        Regex("^01[3-9]\\d{8}$").matches(compact) -> compact
        Regex("^8801[3-9]\\d{8}$").matches(compact) -> "0${compact.drop(3)}"
        Regex("^\\+8801[3-9]\\d{8}$").matches(compact) -> "0${compact.drop(4)}"
        else -> return null
    }
    return "+880${local.drop(1)}"
}
