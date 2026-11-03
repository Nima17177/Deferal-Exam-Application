package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension functions to convert Firebase callback-based APIs to Kotlin Coroutines
 */

suspend inline fun <reified T> DocumentSnapshot.toObjectSuspend(): T? = suspendCancellableCoroutine { continuation ->
    try {
        continuation.resume(this.toObject(T::class.java))
    } catch (e: Exception) {
        continuation.resumeWithException(e)
    }
}

suspend fun FirebaseAuth.signInWithEmailAndPasswordSuspend(email: String, password: String): AuthResult = 
    suspendCancellableCoroutine { continuation ->
        signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun FirebaseAuth.createUserWithEmailAndPasswordSuspend(email: String, password: String): AuthResult = 
    suspendCancellableCoroutine { continuation ->
        createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun FirebaseAuth.sendPasswordResetEmailSuspend(email: String): Unit = 
    suspendCancellableCoroutine { continuation ->
        sendPasswordResetEmail(email)
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.getSuspend(): DocumentSnapshot = 
    suspendCancellableCoroutine { continuation ->
        get()
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.Query.getSuspend(): QuerySnapshot = 
    suspendCancellableCoroutine { continuation ->
        get()
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.setSuspend(data: Any): Unit = 
    suspendCancellableCoroutine { continuation ->
        set(data)
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.setSuspend(data: Map<String, Any>, options: com.google.firebase.firestore.SetOptions): Unit = 
    suspendCancellableCoroutine { continuation ->
        set(data, options)
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.updateSuspend(updates: Map<String, Any>): Unit = 
    suspendCancellableCoroutine { continuation ->
        update(updates)
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.updateSuspend(field: String, value: Any): Unit = 
    suspendCancellableCoroutine { continuation ->
        update(field, value)
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.DocumentReference.deleteSuspend(): Unit = 
    suspendCancellableCoroutine { continuation ->
        delete()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

suspend fun com.google.firebase.firestore.WriteBatch.commitSuspend(): Unit = 
    suspendCancellableCoroutine { continuation ->
        commit()
            .addOnSuccessListener { continuation.resume(Unit) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

