package com.rodrigos01.aipodcasts.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    init {
        clearIfAnonymous()
    }

    val currentUser: FirebaseUser?
        get() {
            clearIfAnonymous()
            return auth.currentUser
        }

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user?.isAnonymous == true) {
                firebaseAuth.signOut()
                trySend(null)
            } else {
                trySend(user)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun clearIfAnonymous() {
        if (auth.currentUser?.isAnonymous == true) {
            auth.signOut()
        }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user
    }

    suspend fun signInWithEmail(email: String, pass: String): FirebaseUser? {
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        return result.user
    }

    suspend fun signUpWithEmail(email: String, pass: String): FirebaseUser? {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        return result.user
    }

    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await()?.token
    }

    fun signOut() {
        auth.signOut()
    }
}
