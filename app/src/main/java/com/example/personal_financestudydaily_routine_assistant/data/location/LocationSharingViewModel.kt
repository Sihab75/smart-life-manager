package com.example.personal_financestudydaily_routine_assistant.data.location

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_financestudydaily_routine_assistant.data.cloud.FirebaseCloudService
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LocationSharingUiState(
    val requests: List<LocationRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class LocationSharingViewModel(application: Application) : AndroidViewModel(application) {
    private val cloud = FirebaseCloudService(application)
    private val repository = LocationSharingRepository(cloud)
    private val _state = MutableStateFlow(LocationSharingUiState())
    val state: StateFlow<LocationSharingUiState> = _state

    init {
        refresh()
    }

    fun currentPhoneNumber(): String? = cloud.currentUser?.phoneNumber
    fun currentDisplayName(): String? = cloud.currentUser?.displayName
    fun isSignedIn(): Boolean = cloud.currentUser != null

    fun startPhoneVerification(
        activity: Activity,
        phoneInput: String,
        onCodeSent: (String) -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        val phoneNumber = normalizeBangladeshPhone(phoneInput)
        if (phoneNumber == null) {
            onResult(Result.failure(IllegalArgumentException("Enter a valid Bangladesh mobile number.")))
            return
        }
        try {
            cloud.startPhoneVerification(
                activity = activity,
                phoneNumber = phoneNumber,
                onCodeSent = onCodeSent,
                onVerified = { credential -> linkPhoneCredential(credential, onResult) },
                onFailure = { onResult(Result.failure(it)) }
            )
        } catch (error: Exception) {
            onResult(Result.failure(error))
        }
    }

    fun confirmPhoneVerification(
        verificationId: String,
        code: String,
        onResult: (Result<String>) -> Unit
    ) {
        if (code.length !in 4..10) {
            onResult(Result.failure(IllegalArgumentException("Enter the SMS verification code.")))
            return
        }
        val credential = try {
            PhoneAuthProvider.getCredential(verificationId, code.trim())
        } catch (error: IllegalArgumentException) {
            onResult(Result.failure(error))
            return
        }
        linkPhoneCredential(credential, onResult)
    }

    private fun linkPhoneCredential(
        credential: AuthCredential,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val result = cloud.linkPhoneCredential(credential)
            if (result.isSuccess) {
                val phoneNumber = result.getOrNull()?.phoneNumber
                if (phoneNumber == null) {
                    onResult(Result.failure(IllegalStateException("Firebase did not return a verified phone number.")))
                } else {
                    _state.value = _state.value.copy(message = "Your phone number is verified.")
                    onResult(Result.success(phoneNumber))
                }
            } else {
                onResult(Result.failure(result.exceptionOrNull() ?: IllegalStateException("Phone verification failed.")))
            }
        }
    }

    fun refresh() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val requests = repository.getRequests()
                _state.value = _state.value.copy(requests = requests, isLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "Location requests could not be loaded."
                )
            }
        }
    }

    fun sendRequest(phoneInput: String, onCreated: (Result<CreatedLocationRequest>) -> Unit) {
        if (_state.value.isSending) return
        val phoneNumber = normalizeBangladeshPhone(phoneInput)
        if (phoneInput.isBlank()) {
            _state.value = _state.value.copy(error = "Enter a phone number.")
            return
        }
        if (phoneNumber == null) {
            _state.value = _state.value.copy(error = "Enter a valid Bangladesh mobile number.")
            return
        }
        val ownPhone = currentPhoneNumber()?.let(::normalizeBangladeshPhone)
        if (ownPhone == null) {
            _state.value = _state.value.copy(
                error = "Verify your own phone number with Firebase before sending a location request."
            )
            return
        }
        if (phoneNumber == ownPhone) {
            _state.value = _state.value.copy(error = "You cannot request your own location.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true, error = null, message = null)
            try {
                val created = repository.createRequest(phoneNumber)
                _state.value = _state.value.copy(
                    isSending = false,
                    message = "Request created. Send the SMS draft to deliver the consent link."
                )
                refresh()
                onCreated(Result.success(created))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when ((error as? LocationSharingException)?.code) {
                    "OWN_PHONE" -> "You cannot request your own location."
                    "INVALID_PHONE" -> "Enter a valid Bangladesh mobile number."
                    "DUPLICATE_PENDING" -> "A location request to this number is already pending."
                    "SERVICE_UNAVAILABLE" -> "Location sharing is temporarily unavailable. Try again later."
                    else -> error.message ?: "The location request could not be sent."
                }
                _state.value = _state.value.copy(isSending = false, error = message)
                onCreated(Result.failure(error))
            }
        }
    }

    fun stopRequest(requestId: String) {
        viewModelScope.launch {
            try {
                repository.stopRequest(requestId)
                _state.value = _state.value.copy(message = "Location access stopped.")
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    error = error.message ?: "Location sharing could not be stopped."
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
