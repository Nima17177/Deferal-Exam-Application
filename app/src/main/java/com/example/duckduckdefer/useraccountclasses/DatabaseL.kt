package com.example.duckduckdefer.useraccountclasses

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object DatabaseL {

    sealed class AuthResult {
        data class Success(val uid: String, val role: Int) : AuthResult()
        object DeactivatedAccount : AuthResult()
        object RejectedAccount : AuthResult()
        object PendingApproval : AuthResult()
        object InvalidCreds : AuthResult()
        object NetworkError : AuthResult()
        data class WeakPassword(val requirements: String) : AuthResult()
        object UserExists : AuthResult()
        object UnknownError : AuthResult()
    }

    suspend fun loginSuspend(email: String, password: String): AuthResult = try {
        val authResult = FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .await()
        
        val uid = authResult.user?.uid ?: throw Exception("UID is null")
        val snapshot = FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(uid)
            .get()
            .await()
        
        val user = snapshot.toObject(UserMembersClass::class.java)
        if (user != null) {
            when (user.thisApprovalStatus) {
                -1 -> AuthResult.DeactivatedAccount
                -2 -> AuthResult.RejectedAccount
                0 -> AuthResult.PendingApproval
                else -> AuthResult.Success(uid, user.thisRole)
            }
        } else {
            AuthResult.UnknownError
        }
    } catch (exception: Exception) {
        when (exception) {
            is FirebaseAuthInvalidCredentialsException -> AuthResult.InvalidCreds
            is FirebaseNetworkException -> AuthResult.NetworkError
            else -> AuthResult.UnknownError
        }
    }

    suspend fun signupSuspend(email: String, password: String): AuthResult = try {
        val authResult = FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .await()
        
        val uid = authResult.user?.uid ?: throw Exception("UID is null")
        AuthResult.Success(uid, -1)
    } catch (exception: Exception) {
        when (exception) {
            is FirebaseAuthInvalidCredentialsException -> AuthResult.InvalidCreds
            is FirebaseAuthUserCollisionException -> AuthResult.UserExists
            is FirebaseNetworkException -> AuthResult.NetworkError
            else -> {
                val exceptionMessage = exception.message.toString()
                if ((exceptionMessage.contains("PASSWORD_DOES_NOT_MEET_REQUIREMENTS")) || (exception is FirebaseAuthWeakPasswordException)) {
                    var retVal = ""
                    if (exceptionMessage.contains("Password must contain at least 6 characters")) retVal += "-"
                    if (exceptionMessage.contains("Password may contain at most 4096 characters")) retVal += "+"
                    if (exceptionMessage.contains("Password must contain a numeric character")) retVal += "1"
                    if (exceptionMessage.contains("Password must contain a non-alphanumeric character")) retVal += "1"
                    if (exceptionMessage.contains("Password must contain an upper case character")) retVal += "A"
                    if (exceptionMessage.contains("Password must contain a lower case character")) retVal += "a"
                    AuthResult.WeakPassword(retVal)
                } else {
                    AuthResult.UnknownError
                }
            }
        }
    }

    suspend fun registerSuspend(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: Int = 3
    ): Pair<Boolean, String?> = try {
        val result = signupSuspend(email, password)
        when (result) {
            is AuthResult.Success -> {
                val user = UserMembersClass()
                val success = user.setUserDataSuspend(result.uid, email, "$firstName $lastName", role)
                if (success) {
                    Pair(true, null)
                } else {
                    Pair(false, "Failed to save user profile data.")
                }
            }
            is AuthResult.WeakPassword -> Pair(false, "Password does not meet security requirements.")
            is AuthResult.UserExists -> Pair(false, "An account with this email already exists.")
            is AuthResult.NetworkError -> Pair(false, "Network error. Please check your connection.")
            else -> Pair(false, "An unexpected error occurred during registration.")
        }
    } catch (e: Exception) {
        Pair(false, "Registration failed: ${e.message}")
    }

    suspend fun resetPasswordSuspend(
        user: UserMembersClass = UserMembersClass(),
        email: String = "JohnDoe"
    ): Boolean = try {
        val emailR = when {
            user.thisUserName == "User" && email != "JohnDoe" -> email
            user.thisUserName != "User" && email == "JohnDoe" -> user.thisEmail
            else -> return false
        }
        
        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(emailR)
            .await()
        true
    } catch (e: Exception) {
        false
    }

    suspend fun appendPendingSuspend(uid: String): Boolean = try {
        val db = FirebaseFirestore.getInstance()
        db.collection("PendingAccounts")
            .document("0")
            .set(mapOf("uids" to FieldValue.arrayUnion(uid)), SetOptions.merge())
            .await()
        true
    } catch (e: Exception) {
        false
    }

    suspend fun removePendingSuspend(uid: String): Boolean = try {
        FirebaseFirestore.getInstance()
            .collection("PendingAccounts")
            .document("0")
            .update("uids", FieldValue.arrayRemove(uid))
            .await()
        true
    } catch (e: Exception) {
        false
    }

    // New coroutine-based search functions
    suspend fun searchStudentByEmailSuspend(query: String): UserMembersClass? {
        return try {
            val trimmed = query.trim()
            val collection = FirebaseFirestore.getInstance().collection("UserAccounts")
            
            var result = collection
                .whereEqualTo("thisEmail", trimmed)
                .whereEqualTo("thisRole", 3)
                .get()
                .await()
            
            if (!result.isEmpty) {
                val user = result.documents[0].toObject(UserMembersClass::class.java)
                user?.thisUID = result.documents[0].id
                user
            } else {
                result = collection
                    .whereEqualTo("thisUserName", trimmed)
                    .whereEqualTo("thisRole", 3)
                    .get()
                    .await()
                
                if (!result.isEmpty) {
                    val user = result.documents[0].toObject(UserMembersClass::class.java)
                    user?.thisUID = result.documents[0].id
                    user
                } else if (trimmed.lowercase() != trimmed) {
                    result = collection
                        .whereEqualTo("thisEmail", trimmed.lowercase())
                        .whereEqualTo("thisRole", 3)
                        .get()
                        .await()
                    if (!result.isEmpty) {
                        val user = result.documents[0].toObject(UserMembersClass::class.java)
                        user?.thisUID = result.documents[0].id
                        user
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseL", "Error searching student by email", e)
            null
        }
    }

    suspend fun searchPotentialProctorsSuspend(query: String): List<UserMembersClass> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val collection = db.collection("UserAccounts")
            val trimmedQuery = query.trim().lowercase()
            
            if (trimmedQuery.isEmpty()) {
                emptyList()
            } else {
                val snapshot = collection
                    .whereEqualTo("thisRole", 1) // Faculty role
                    .limit(100)
                    .get()
                    .await()
                
                snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }
                    }
                    .filter { user ->
                        user.thisApprovalStatus >= 0 &&
                        (user.thisEmail.lowercase().contains(trimmedQuery) ||
                         user.thisUserName.lowercase().contains(trimmedQuery))
                    }
                    .sortedBy { it.thisUserName }
                    .take(15)
            }
        } catch (e: Exception) {
            Log.e("DatabaseL", "Error searching potential proctors", e)
            emptyList()
        }
    }

    suspend fun searchUsersByRoleSuspend(query: String, role: Int): List<UserMembersClass> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val collection = db.collection("UserAccounts")
            val trimmedQuery = query.trim().lowercase()
            
            if (trimmedQuery.isEmpty()) {
                emptyList()
            } else {
                val snapshot = collection
                    .whereEqualTo("thisRole", role)
                    .limit(100)
                    .get()
                    .await()
                
                snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }
                    }
                    .filter { user ->
                        user.thisApprovalStatus >= 0 &&
                        (user.thisEmail.lowercase().contains(trimmedQuery) ||
                         user.thisUserName.lowercase().contains(trimmedQuery))
                    }
                    .sortedBy { it.thisUserName }
                    .take(15)
            }
        } catch (e: Exception) {
            Log.e("DatabaseL", "Error searching users by role", e)
            emptyList()
        }
    }

    // ============ Backward Compatibility - Old Callback Functions ============
    
    fun login(email: String, password: String, onComplete: (AuthResult) -> Unit) {
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid.toString()
                FirebaseFirestore.getInstance()
                    .collection("UserAccounts")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val user = snapshot.toObject(UserMembersClass::class.java)
                        if (user != null) {
                            when (user.thisApprovalStatus) {
                                -1 -> onComplete(AuthResult.DeactivatedAccount)
                                -2 -> onComplete(AuthResult.RejectedAccount)
                                0 -> onComplete(AuthResult.PendingApproval)
                                else -> onComplete(AuthResult.Success(uid, user.thisRole))
                            }
                        } else {
                            onComplete(AuthResult.UnknownError)
                        }
                    }
                    .addOnFailureListener { onComplete(AuthResult.UnknownError) }
            }
            .addOnFailureListener { exception ->
                when (exception) {
                    is FirebaseAuthInvalidCredentialsException -> onComplete(AuthResult.InvalidCreds)
                    is FirebaseNetworkException -> onComplete(AuthResult.NetworkError)
                    else -> onComplete(AuthResult.UnknownError)
                }
            }
    }

    fun signup(email: String, password: String, onComplete: (AuthResult) -> Unit) {
        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid.toString()
                onComplete(AuthResult.Success(uid, -1))
            }
            .addOnFailureListener { exception ->
                when (exception) {
                    is FirebaseAuthInvalidCredentialsException -> onComplete(AuthResult.InvalidCreds)
                    is FirebaseAuthUserCollisionException -> onComplete(AuthResult.UserExists)
                    is FirebaseNetworkException -> onComplete(AuthResult.NetworkError)
                    else -> onComplete(AuthResult.UnknownError)
                }
            }
    }

    fun appendPending(uid: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("PendingAccounts")
            .document("0")
            .set(mapOf("uids" to FieldValue.arrayUnion(uid)), SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun removePending(uid: String, onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("PendingAccounts")
            .document("0")
            .update("uids", FieldValue.arrayRemove(uid))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Additional callback-based search methods for UI compatibility
    fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: Int = 3,
        onComplete: (Boolean, String?) -> Unit
    ) {
        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: run {
                    onComplete(false, "Failed to get user ID")
                    return@addOnSuccessListener
                }
                
                val user = UserMembersClass()
                user.setUserData(uid, email, "$firstName $lastName", role) { success ->
                    if (success) {
                        appendPending(uid) { pendingSuccess ->
                            onComplete(true, null)
                        }
                    } else {
                        onComplete(false, "Failed to save user profile")
                    }
                }
            }
            .addOnFailureListener { exception ->
                when (exception) {
                    is FirebaseAuthUserCollisionException -> onComplete(false, "Email already in use")
                    is FirebaseAuthWeakPasswordException -> onComplete(false, "Password too weak")
                    else -> onComplete(false, exception.message)
                }
            }
    }

    fun searchUsersByRole(query: String, role: Int, onResult: (List<UserMembersClass>) -> Unit) {
        val collection = FirebaseFirestore.getInstance().collection("UserAccounts")
        val trimmedQuery = query.trim().lowercase()
        
        if (trimmedQuery.isEmpty()) {
            onResult(emptyList())
            return
        }

        // Fetch all users with the given role, then filter client-side
        collection
            .whereEqualTo("thisRole", role)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }
                    }
                    .filter { user ->
                        // Filter by approval status and search query
                        user.thisApprovalStatus >= 0 &&
                        (user.thisEmail.lowercase().contains(trimmedQuery) ||
                         user.thisUserName.lowercase().contains(trimmedQuery))
                    }
                    .sortedBy { it.thisUserName }
                    .take(15)
                
                onResult(results)
            }
            .addOnFailureListener { 
                onResult(emptyList())
            }
    }

    fun searchStudentByEmail(email: String, onResult: (UserMembersClass?) -> Unit) {
        val collection = FirebaseFirestore.getInstance().collection("UserAccounts")
        val trimmedEmail = email.trim().lowercase()

        collection
            .whereEqualTo("thisEmail", trimmedEmail)
            .whereEqualTo("thisRole", 3)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val user = result.documents[0].toObject(UserMembersClass::class.java)
                    user?.thisUID = result.documents[0].id
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun searchPotentialProctors(query: String, onResult: (List<UserMembersClass>) -> Unit) {
        val collection = FirebaseFirestore.getInstance().collection("UserAccounts")
        val trimmedQuery = query.trim().lowercase()
        
        if (trimmedQuery.isEmpty()) {
            onResult(emptyList())
            return
        }

        // Fetch all proctors and faculty, then filter client-side
        collection
            .whereEqualTo("thisRole", 1) // Faculty role
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(UserMembersClass::class.java)?.apply { thisUID = doc.id }
                    }
                    .filter { user ->
                        user.thisApprovalStatus >= 0 &&
                        (user.thisEmail.lowercase().contains(trimmedQuery) ||
                         user.thisUserName.lowercase().contains(trimmedQuery))
                    }
                    .sortedBy { it.thisUserName }
                    .take(15)
                
                onResult(results)
            }
            .addOnFailureListener { 
                onResult(emptyList())
            }
    }

    fun resetPassword(email: String, onComplete: (Boolean) -> Unit) {
        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}
