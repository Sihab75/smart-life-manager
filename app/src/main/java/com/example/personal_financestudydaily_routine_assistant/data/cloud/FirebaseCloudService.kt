package com.example.personal_financestudydaily_routine_assistant.data.cloud

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

data class CloudUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val phoneNumber: String?
)

class FirebaseCloudService(context: Context) {
    private val app = FirebaseApp.initializeApp(context.applicationContext)

    private val auth: FirebaseAuth?
        get() = app?.let { FirebaseAuth.getInstance(it) }
    private val firestore: FirebaseFirestore?
        get() = app?.let { FirebaseFirestore.getInstance(it) }
    private val storage: FirebaseStorage?
        get() = app?.let { FirebaseStorage.getInstance(it) }

    val currentUser: CloudUser?
        get() = auth?.currentUser?.toCloudUser()

    suspend fun assistantIdToken(forceRefresh: Boolean = false): String? =
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onVerified: (AuthCredential) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val firebaseAuth = auth ?: error("Firebase is not configured. Add google-services.json.")
        check(firebaseAuth.currentUser != null) { "Sign in from Settings before verifying your phone." }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onVerified(credential)
            override fun onVerificationFailed(error: com.google.firebase.FirebaseException) = onFailure(error)
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) = onCodeSent(verificationId)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    suspend fun linkPhoneCredential(credential: AuthCredential): Result<CloudUser> =
        runCatching {
            val user = auth?.currentUser ?: error("Sign in from Settings before verifying your phone.")
            val linkedUser = user.linkWithCredential(credential).await().user
                ?: error("Firebase did not return the verified account.")
            saveUserProfile(linkedUser)
            linkedUser.toCloudUser()
        }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<CloudUser> =
        runCatching {
            val firebaseAuth = auth ?: error("Firebase is not configured. Add google-services.json.")
            firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(account.idToken, null))
                .await().user?.also { saveUserProfile(it) }?.toCloudUser()
                ?: error("Firebase did not return a user.")
        }

    suspend fun signInWithEmail(email: String, password: String): Result<CloudUser> =
        runCatching {
            require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required." }
            val firebaseAuth = auth ?: error("Firebase is not configured. Add google-services.json.")
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user
                ?.also { saveUserProfile(it) }?.toCloudUser()
                ?: error("Firebase did not return a user.")
        }

    suspend fun registerWithEmail(email: String, password: String): Result<CloudUser> =
        runCatching {
            require(email.isNotBlank() && password.length >= 6) {
                "Use a valid email and a password with at least 6 characters."
            }
            val firebaseAuth = auth ?: error("Firebase is not configured. Add google-services.json.")
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?.also { saveUserProfile(it) }?.toCloudUser()
                ?: error("Firebase did not return a user.")
        }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun registerMessagingToken(): Result<Unit> =
        runCatching {
            val user = auth?.currentUser ?: return@runCatching
            val token = FirebaseMessaging.getInstance().token.await()
            firestore?.collection("users")?.document(user.uid)
                ?.set(mapOf("fcmToken" to token), SetOptions.merge())?.await()
        }

    suspend fun uploadFile(userPath: String, file: Uri): Result<String> =
        runCatching {
            val user = auth?.currentUser ?: error("Sign in before uploading files.")
            val reference = storage?.reference?.child("users/${user.uid}/$userPath")
                ?: error("Firebase is not configured. Add google-services.json.")
            reference.putFile(file).await()
            reference.downloadUrl.await().toString()
        }

    private suspend fun saveUserProfile(user: FirebaseUser) {
        firestore?.collection("users")?.document(user.uid)?.set(
            mapOf(
                "uid" to user.uid,
                "displayName" to user.displayName,
                "email" to user.email,
                "photoUrl" to user.photoUrl?.toString(),
                "phoneNumber" to user.phoneNumber,
                "lastLoginAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )?.await()
    }

    private fun FirebaseUser.toCloudUser() =
        CloudUser(uid, displayName, email, photoUrl?.toString(), phoneNumber)
}
