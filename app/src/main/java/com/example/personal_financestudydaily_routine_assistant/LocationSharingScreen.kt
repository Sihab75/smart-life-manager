package com.example.personal_financestudydaily_routine_assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personal_financestudydaily_routine_assistant.data.location.LocationRequest
import com.example.personal_financestudydaily_routine_assistant.data.location.CreatedLocationRequest
import com.example.personal_financestudydaily_routine_assistant.data.location.LocationSharingViewModel
import com.example.personal_financestudydaily_routine_assistant.data.location.normalizeBangladeshPhone
import kotlinx.coroutines.delay

@Composable
fun LocationSharingScreen(vm: LocationSharingViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var createdShareRequest by remember { mutableStateOf<CreatedLocationRequest?>(null) }
    var requestSubmitted by remember { mutableStateOf(false) }
    var ownPhoneNumber by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var verifyingPhone by remember { mutableStateOf(false) }
    var stopRequest by remember { mutableStateOf<LocationRequest?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMillis = System.currentTimeMillis()
            vm.refresh()
        }
    }

    val normalizedPhone = normalizeBangladeshPhone(phoneNumber)
    val verifiedPhone = vm.currentPhoneNumber()
    val normalizedVerifiedPhone = verifiedPhone?.let(::normalizeBangladeshPhone)
    val phoneError = when {
        phoneNumber.isBlank() && requestSubmitted -> "Enter a phone number."
        phoneNumber.isBlank() -> null
        normalizedPhone == null -> "Enter a valid Bangladesh mobile number."
        normalizedVerifiedPhone != null && normalizedVerifiedPhone == normalizedPhone ->
            "You cannot request your own location."
        else -> null
    }

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Location Sharing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Request someone's location with their permission.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ShareLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        if (verifiedPhone == null) {
            MetricCard("Verify your phone number", Modifier.fillMaxWidth()) {
                Text(
                    if (vm.isSignedIn()) {
                        "Verify a Bangladesh number on your existing Firebase account first. This helps prevent requests to your own number."
                    } else {
                        "Sign in from Settings with your existing Firebase account, then verify your Bangladesh phone number."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ownPhoneNumber,
                    onValueChange = { ownPhoneNumber = it; phoneFeedback = null },
                    label = { Text("Your phone number") },
                    placeholder = { Text("+880 1XXXXXXXXX") },
                    supportingText = {
                        if (ownPhoneNumber.isNotBlank() && normalizeBangladeshPhone(ownPhoneNumber) == null) {
                            Text("Enter a valid Bangladesh mobile number.")
                        }
                    },
                    isError = ownPhoneNumber.isNotBlank() && normalizeBangladeshPhone(ownPhoneNumber) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                verificationId?.let {
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it.filter(Char::isDigit).take(10) },
                        label = { Text("SMS verification code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            phoneFeedback = "Phone verification is unavailable in this screen."
                        } else if (verificationId == null) {
                            verifyingPhone = true
                            vm.startPhoneVerification(
                                activity = activity,
                                phoneInput = ownPhoneNumber,
                                onCodeSent = { id ->
                                    verificationId = id
                                    verifyingPhone = false
                                    phoneFeedback = "Verification code sent. Enter the code to finish verification."
                                },
                                onResult = { result ->
                                    verifyingPhone = false
                                    result.onSuccess {
                                        verificationId = null
                                        verificationCode = ""
                                        phoneFeedback = "Your phone number is verified."
                                    }.onFailure {
                                        phoneFeedback = it.message ?: "Phone verification failed."
                                    }
                                }
                            )
                        } else {
                            verifyingPhone = true
                            verificationId?.let { id ->
                                vm.confirmPhoneVerification(id, verificationCode) { result ->
                                    verifyingPhone = false
                                    result.onSuccess {
                                        verificationId = null
                                        verificationCode = ""
                                        phoneFeedback = "Your phone number is verified."
                                    }.onFailure {
                                        phoneFeedback = it.message ?: "Phone verification failed."
                                    }
                                }
                            }
                        }
                    },
                    enabled = vm.isSignedIn() && !verifyingPhone &&
                        (if (verificationId == null) normalizeBangladeshPhone(ownPhoneNumber) != null
                        else verificationCode.length >= 4),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            verifyingPhone -> "Verifying…"
                            verificationId == null -> "Send verification code"
                            else -> "Confirm code"
                        }
                    )
                }
                phoneFeedback?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            Text(
                "Verified phone: $verifiedPhone",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        MetricCard("Request location", Modifier.fillMaxWidth()) {
            Text(
                "A secure link is prepared for SMS; recipients with the app may also receive a review notification. They can use any browser and must choose to share.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    requestSubmitted = false
                    vm.clearMessage()
                },
                label = { Text("Phone number") },
                placeholder = { Text("+880 1XXXXXXXXX") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                supportingText = {
                    Text(phoneError ?: "Bangladesh numbers: 017XXXXXXXX or +88017XXXXXXXX")
                },
                isError = phoneError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    requestSubmitted = true
                    vm.sendRequest(phoneNumber) { result ->
                        result.onSuccess { created ->
                            createdShareRequest = created
                            val number = normalizedPhone
                            if (number != null) {
                                val requesterName = vm.currentDisplayName()?.takeIf(String::isNotBlank)
                                    ?: "A Smart Life Manager user"
                                val smsBody =
                                    "Location request from $requesterName. Review and choose whether to share your current location (expires in 15 minutes): ${created.shareUrl}"
                                val smsIntent = Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.fromParts("smsto", number, null)
                                ).putExtra(
                                    "sms_body",
                                    smsBody
                                )
                                try {
                                    context.startActivity(smsIntent)
                                } catch (_: ActivityNotFoundException) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, smsBody)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(shareIntent, "Send location request"))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "Request created, but no messaging app is available.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = normalizedVerifiedPhone != null && !state.isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.isSending) "Sending request…" else "Send request")
            }
            Text(
                if (normalizedVerifiedPhone == null) {
                    "Verify your phone number above before sending a request."
                } else {
                    "You will not see a location unless the recipient grants browser permission. The messaging app opens a draft; you still need to send the SMS."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        createdShareRequest?.let { created ->
            MetricCard("Consent link ready", Modifier.fillMaxWidth()) {
                if (created.expiresAtMillis > nowMillis) {
                    Text(
                        "If the SMS draft was not sent, copy this link and send it before it expires.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Location consent link", created.shareUrl))
                            Toast.makeText(context, "Consent link copied.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy consent link")
                    }
                } else {
                    Text("This consent link has expired. Create a new request to send another link.")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Requests & shared locations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = vm::refresh, enabled = !state.isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh location requests")
            }
        }
        if (state.isLoading && state.requests.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else if (state.requests.isEmpty()) {
            EmptyState("No location requests yet.")
        } else {
            state.requests.forEach { request ->
                LocationRequestCard(
                    request = request,
                    nowMillis = nowMillis,
                    onOpenMap = { location ->
                        val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No map app is installed.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStop = { stopRequest = request }
                )
            }
        }
    }

    stopRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { stopRequest = null },
            title = { Text("Stop location access?") },
            text = {
                Text("The latest location will be removed and this requester will no longer be able to view it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopRequest(request.requestId)
                    stopRequest = null
                }) { Text("Stop sharing") }
            },
            dismissButton = {
                TextButton(onClick = { stopRequest = null }) { Text("Keep active") }
            }
        )
    }
}

@Composable
private fun LocationRequestCard(
    request: LocationRequest,
    nowMillis: Long,
    onOpenMap: (com.example.personal_financestudydaily_routine_assistant.data.location.SharedLocation) -> Unit,
    onStop: () -> Unit
) {
    val location = request.location
    val isActive = request.status == "APPROVED" && location != null &&
        request.expiresAtMillis > nowMillis
    MetricCard(request.targetName, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isActive) Icons.Default.LocationOn else Icons.Default.ShareLocation,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isActive -> "Location sharing active"
                    request.status == "PENDING" && request.expiresAtMillis > nowMillis -> "Waiting for approval"
                    request.status == "APPROVED" && request.expiresAtMillis > nowMillis -> "Approved · waiting for location"
                    request.status == "DECLINED" -> "Declined · no location shared"
                    request.status == "REVOKED" -> "Sharing stopped"
                    else -> "Request expired"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "Expires ${if (request.expiresAtMillis > nowMillis) "in ${formatDuration(request.expiresAtMillis - nowMillis)}" else "now"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isActive) {
            Text(
                "Last updated ${formatAge(nowMillis - location.updatedAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Accuracy ±${location.accuracyMeters.toInt()} m" +
                    if (location.accuracyMeters > 100) " · GPS accuracy is limited" else "",
                color = if (location.accuracyMeters > 100) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenMap(location) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open map")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.StopCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop viewing")
                }
            }
        } else if (request.status == "APPROVED" && request.expiresAtMillis > nowMillis) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop viewing")
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000).toInt()
    val seconds = (milliseconds / 1_000 % 60).toInt()
    return "${minutes}m ${seconds}s"
}

private fun formatAge(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1_000).toInt()
    return when {
        seconds < 60 -> "$seconds seconds ago"
        seconds < 3600 -> "${seconds / 60} minutes ago"
        else -> "${seconds / 3600} hours ago"
    }
}
