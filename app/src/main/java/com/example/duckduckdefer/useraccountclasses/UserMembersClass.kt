package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.tasks.await

open class UserMembersClass(
    var thisUID: String = "0", // User ID in Authentication Database, and Document ID in Firestore User Accounts Database
    var thisEmail: String = "doej@mymacewan.ca",
    var thisUserName: String = "User",
    var thisRole: Int = 2, // 0 = Admin, 1 = Faculty, 2 = Proctor, 3 = Student
    var thisApprovalStatus: Int = 0, // 0 = Pending, 1 = Approved, -1 = Deactivated, -2 = Rejected
    var profilePictureUrl: String = "", // URL to profile picture in Firebase Storage
    var biography: String = "", // User biography (max 500 characters)
    var contactEmails: List<String> = emptyList(), // Additional contact emails
    var phoneNumbers: List<String> = emptyList(), // Phone numbers
    var fcmToken: String = "", // FCM token for push notifications
    var pushNotificationsEnabled: Boolean = true,
    var emailNotificationsEnabled: Boolean = true,
    var notificationSound: String = "default", // "default" or "quack"
    var themePreference: Int = 0, // 0 = System, 1 = Light, 2 = Dark
    var fontSizePreference: Int = 1, // 0 = Small, 1 = Medium, 2 = Large
    var contrastPreference: Int = 0, // 0 = Normal, 1 = High Contrast
    var points: Int = 0,
    var level: Int = 1,
    var experience: Int = 0,
    var badges: List<String> = emptyList(),
    var dailyStreak: Int = 0,
    var lastLoginDate: String = "", // Format: YYYY-MM-DD
    var currentTitle: String = "Novice",
    var unlockedTitles: List<String> = listOf("Novice"),
    var unlockedThemes: List<Int> = listOf(0, 1, 2), // Default system, light, dark
    var unlockedSounds: List<String> = listOf("default")
) {

    companion object {
        /**
         * Factory method to create the appropriate subclass based on role.
         */
        fun create(role: Int): UserMembersClass {
            return when (role) {
                0 -> AdminSubclass()
                1 -> FacultySubclass()
                2 -> ProctorSubclass()
                else -> StudentSubclass()
            }
        }

        /**
         * Award XP to a user by UID.
         */
        fun awardExperience(uid: String, amount: Int, onLevelUp: (Int) -> Unit = {}, onComplete: (Boolean) -> Unit = {}) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("UserAccounts").document(uid)
            
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                if (!snapshot.exists()) return@runTransaction false
                
                val currentExp = snapshot.getLong("experience")?.toInt() ?: 0
                val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
                val currentPoints = snapshot.getLong("points")?.toInt() ?: 0
                val currentBadges = (snapshot.get("badges") as? List<String>) ?: emptyList()
                val currentTitles = (snapshot.get("unlockedTitles") as? List<String>) ?: listOf("Novice")
                
                val newExp = currentExp + amount
                val newPoints = currentPoints + amount
                val newLevel = (newExp / 100) + 1
                
                val updates = mutableMapOf<String, Any>(
                    "experience" to newExp,
                    "points" to newPoints,
                    "level" to newLevel
                )
                
                if (newLevel > currentLevel) {
                    val levelBadge = "Level $newLevel"
                    if (!currentBadges.contains(levelBadge)) {
                        updates["badges"] = currentBadges + levelBadge
                    }
                    
                    // Logic for unlocking titles
                    val newTitles = currentTitles.toMutableList()
                    when (newLevel) {
                        5 -> if (!newTitles.contains("Proctor-in-Training")) newTitles.add("Proctor-in-Training")
                        10 -> if (!newTitles.contains("Exam Veteran")) newTitles.add("Exam Veteran")
                        20 -> if (!newTitles.contains("Deferral Master")) newTitles.add("Deferral Master")
                        50 -> if (!newTitles.contains("Duck Lord")) newTitles.add("Duck Lord")
                    }
                    if (newTitles.size > currentTitles.size) {
                        updates["unlockedTitles"] = newTitles
                    }

                    onLevelUp(newLevel)
                }
                
                transaction.update(userRef, updates)
                true
            }.addOnSuccessListener { onComplete(true) }
             .addOnFailureListener { onComplete(false) }
        }
    }

    // Get the account approval status.
    fun getApprovalStatus(): Int {
        return thisApprovalStatus
    }

    // Load user information from firebase.
    // Parameters: UID (ID of user account's document).
    fun getUserData(uid: String, onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(UserMembersClass::class.java)
                if (user != null) {
                    thisUID = uid
                    thisEmail = user.thisEmail
                    thisUserName = user.thisUserName
                    thisRole = user.thisRole
                    thisApprovalStatus = user.thisApprovalStatus
                    profilePictureUrl = user.profilePictureUrl
                    biography = user.biography
                    contactEmails = user.contactEmails
                    phoneNumbers = user.phoneNumbers
                    fcmToken = user.fcmToken
                    pushNotificationsEnabled = user.pushNotificationsEnabled
                    emailNotificationsEnabled = user.emailNotificationsEnabled
                    notificationSound = user.notificationSound
                    themePreference = user.themePreference
                    fontSizePreference = user.fontSizePreference
                    contrastPreference = user.contrastPreference
                    points = user.points
                    level = user.level
                    experience = user.experience
                    badges = user.badges
                    dailyStreak = user.dailyStreak
                    lastLoginDate = user.lastLoginDate
                    currentTitle = user.currentTitle
                    unlockedTitles = user.unlockedTitles
                    unlockedThemes = user.unlockedThemes
                    unlockedSounds = user.unlockedSounds
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun setUserData(uid: String, email: String, userName: String, role: Int, onComplete: (Boolean) -> Unit) {
        // Creating new entry in database
        var approvalStatus = 0
        if (role == 3) {
            approvalStatus = 1
        } else {
            DatabaseL.appendPending(uid) { success -> }
        }
        val hashMap = hashMapOf(
            "thisEmail" to email,
            "thisUserName" to userName,
            "thisRole" to role,
            "thisApprovalStatus" to approvalStatus,
            "pushNotificationsEnabled" to true,
            "emailNotificationsEnabled" to true,
            "notificationSound" to "default",
            "themePreference" to 0,
            "fontSizePreference" to 1,
            "contrastPreference" to 0,
            "points" to 0,
            "level" to 1,
            "experience" to 0,
            "badges" to emptyList<String>(),
            "dailyStreak" to 0,
            "lastLoginDate" to "",
            "currentTitle" to "Novice",
            "unlockedTitles" to listOf("Novice"),
            "unlockedThemes" to listOf(0, 1, 2),
            "unlockedSounds" to listOf("default")
        )
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(uid)
            .set(hashMap)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
        // Updating members of this object
        thisUID = uid
        thisEmail = email
        thisUserName = userName
        thisRole = role
        thisApprovalStatus = approvalStatus
        pushNotificationsEnabled = true
        emailNotificationsEnabled = true
        notificationSound = "default"
        themePreference = 0
        fontSizePreference = 1
        contrastPreference = 0
        points = 0
        level = 1
        experience = 0
        badges = emptyList()
        dailyStreak = 0
        lastLoginDate = ""
        currentTitle = "Novice"
        unlockedTitles = listOf("Novice")
        unlockedThemes = listOf(0, 1, 2)
        unlockedSounds = listOf("default")
    }

    fun updateUserData(onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(thisUID)
            .update(
                mapOf(
                    "thisEmail" to thisEmail,
                    "thisUserName" to thisUserName,
                    "thisRole" to thisRole,
                    "thisApprovalStatus" to thisApprovalStatus,
                    "pushNotificationsEnabled" to pushNotificationsEnabled,
                    "emailNotificationsEnabled" to emailNotificationsEnabled,
                    "notificationSound" to notificationSound,
                    "themePreference" to themePreference,
                    "fontSizePreference" to fontSizePreference,
                    "contrastPreference" to contrastPreference,
                    "points" to points,
                    "level" to level,
                    "experience" to experience,
                    "badges" to badges,
                    "dailyStreak" to dailyStreak,
                    "lastLoginDate" to lastLoginDate,
                    "currentTitle" to currentTitle,
                    "unlockedTitles" to unlockedTitles,
                    "unlockedThemes" to unlockedThemes,
                    "unlockedSounds" to unlockedSounds
                )
            )
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateApprovalStatus(onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(thisUID)
            .update("thisApprovalStatus", thisApprovalStatus)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun purchaseTheme(themeId: Int, cost: Int, onComplete: (Boolean, String) -> Unit) {
        purchaseItem(cost, themeId = themeId, onComplete = onComplete)
    }

    fun purchaseTitle(title: String, cost: Int, onComplete: (Boolean, String) -> Unit) {
        purchaseItem(cost, title = title, onComplete = onComplete)
    }

    fun purchaseSound(soundName: String, cost: Int, onComplete: (Boolean, String) -> Unit) {
        purchaseItem(cost, soundName = soundName, onComplete = onComplete)
    }

    /**
     * Equip a sound as the active notification sound.
     * The sound must already be in unlockedSounds.
     */
    fun equipSound(soundName: String, onComplete: (Boolean, String) -> Unit) {
        if (!unlockedSounds.contains(soundName)) {
            onComplete(false, "You haven't unlocked this sound yet!")
            return
        }
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(thisUID)
            .update("notificationSound", soundName)
            .addOnSuccessListener {
                notificationSound = soundName
                onComplete(true, "\"${soundName}\" is now your notification sound! 🔔")
            }
            .addOnFailureListener {
                onComplete(false, "Failed to equip sound.")
            }
    }

    private fun purchaseItem(cost: Int, themeId: Int? = null, title: String? = null, soundName: String? = null, onComplete: (Boolean, String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("UserAccounts").document(thisUID)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) return@runTransaction "User not found"
            
            val currentPoints = snapshot.getLong("points")?.toInt() ?: 0
            if (currentPoints < cost) return@runTransaction "Not enough points!"
            
            val updates = mutableMapOf<String, Any>()
            
            if (themeId != null) {
                val currentThemes = (snapshot.get("unlockedThemes") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() ?: (it as? Int) } ?: listOf(0, 1, 2)
                if (currentThemes.contains(themeId)) return@runTransaction "Theme already owned!"
                updates["unlockedThemes"] = currentThemes + themeId
            }
            
            if (title != null) {
                val currentTitles = (snapshot.get("unlockedTitles") as? List<*>)?.filterIsInstance<String>() ?: listOf("Novice")
                if (currentTitles.contains(title)) return@runTransaction "Title already owned!"
                updates["unlockedTitles"] = currentTitles + title
            }

            if (soundName != null) {
                val currentSounds = (snapshot.get("unlockedSounds") as? List<*>)?.filterIsInstance<String>() ?: listOf("default")
                if (currentSounds.contains(soundName)) return@runTransaction "Sound already owned!"
                updates["unlockedSounds"] = currentSounds + soundName
            }
            
            updates["points"] = currentPoints - cost
            transaction.update(userRef, updates)
            "Success"
        }.addOnSuccessListener { result ->
            if (result == "Success") {
                onComplete(true, "Purchase successful!")
            } else {
                onComplete(false, result)
            }
        }.addOnFailureListener {
            onComplete(false, it.message ?: "Transaction failed")
        }
    }

    fun checkDailyStreak(onComplete: (Boolean, Int) -> Unit) {
        val today = LocalDate.now()
        if (lastLoginDate.isEmpty()) {
            dailyStreak = 1
            lastLoginDate = today.toString()
            updateUserData { onComplete(it, dailyStreak) }
            return
        }

        val lastDate = LocalDate.parse(lastLoginDate)
        val daysBetween = ChronoUnit.DAYS.between(lastDate, today)

        if (daysBetween == 1L) {
            dailyStreak += 1
            lastLoginDate = today.toString()
            
            // Milestone badges for streaks
            if (dailyStreak == 7 && !badges.contains("7-Day Streak")) {
                badges = badges + "7-Day Streak"
            } else if (dailyStreak == 30 && !badges.contains("Monthly Habit")) {
                badges = badges + "Monthly Habit"
            }
            
            addExperience(10 * dailyStreak) { // Bonus XP for streak
                onComplete(it, dailyStreak)
            }
        } else if (daysBetween > 1L) {
            dailyStreak = 1
            lastLoginDate = today.toString()
            updateUserData { onComplete(it, dailyStreak) }
        } else {
            // Already logged in today
            onComplete(true, dailyStreak)
        }
    }

    fun addExperience(amount: Int, onComplete: (Boolean) -> Unit) {
        experience += amount
        points += amount 
        val newLevel = (experience / 100) + 1
        if (newLevel > level) {
            level = newLevel
            if (!badges.contains("Level $level")) {
                badges = badges + "Level $level"
            }
        }
        updateUserData(onComplete)
    }

    fun addBadge(badge: String, onComplete: (Boolean) -> Unit) {
        if (!badges.contains(badge)) {
            badges = badges + badge
            updateUserData(onComplete)
        } else {
            onComplete(true)
        }
    }

    fun checkNotifications() {

    }
    
    // ============ Coroutine-based suspend functions ============
    
    suspend fun getUserDataSuspend(uid: String): Boolean = try {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(uid)
            .get()
            .await()
        
        val user = snapshot.toObject(UserMembersClass::class.java)
        if (user != null) {
            thisUID = uid
            thisEmail = user.thisEmail
            thisUserName = user.thisUserName
            thisRole = user.thisRole
            thisApprovalStatus = user.thisApprovalStatus
            profilePictureUrl = user.profilePictureUrl
            biography = user.biography
            contactEmails = user.contactEmails
            phoneNumbers = user.phoneNumbers
            fcmToken = user.fcmToken
            pushNotificationsEnabled = user.pushNotificationsEnabled
            emailNotificationsEnabled = user.emailNotificationsEnabled
            themePreference = user.themePreference
            fontSizePreference = user.fontSizePreference
            contrastPreference = user.contrastPreference
            points = user.points
            level = user.level
            experience = user.experience
            badges = user.badges
            dailyStreak = user.dailyStreak
            lastLoginDate = user.lastLoginDate
            currentTitle = user.currentTitle
            unlockedTitles = user.unlockedTitles
            unlockedThemes = user.unlockedThemes
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    suspend fun setUserDataSuspend(uid: String, email: String, userName: String, role: Int): Boolean = try {
        var approvalStatus = 0
        if (role == 3) {
            approvalStatus = 1
        } else {
            DatabaseL.appendPendingSuspend(uid)
        }
        
        val hashMap = hashMapOf(
            "thisEmail" to email,
            "thisUserName" to userName,
            "thisRole" to role,
            "thisApprovalStatus" to approvalStatus,
            "pushNotificationsEnabled" to true,
            "emailNotificationsEnabled" to true,
            "themePreference" to 0,
            "fontSizePreference" to 1,
            "contrastPreference" to 0,
            "points" to 0,
            "level" to 1,
            "experience" to 0,
            "badges" to emptyList<String>(),
            "dailyStreak" to 0,
            "lastLoginDate" to "",
            "currentTitle" to "Novice",
            "unlockedTitles" to listOf("Novice"),
            "unlockedThemes" to listOf(0, 1, 2)
        )
        
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(uid)
            .set(hashMap)
            .await()
        
        thisUID = uid
        thisEmail = email
        thisUserName = userName
        thisRole = role
        thisApprovalStatus = approvalStatus
        true
    } catch (e: Exception) {
        false
    }

    suspend fun updateUserDataSuspend(): Boolean = try {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(thisUID)
            .update(mapOf(
                "thisEmail" to thisEmail,
                "thisUserName" to thisUserName,
                "thisRole" to thisRole,
                "thisApprovalStatus" to thisApprovalStatus,
                "experience" to experience,
                "level" to level,
                "points" to points,
                "badges" to badges,
                "dailyStreak" to dailyStreak,
                "lastLoginDate" to lastLoginDate
            ))
            .await()
        true
    } catch (e: Exception) {
        false
    }

    suspend fun checkDailyStreakSuspend(): Pair<Boolean, Int> = try {
        val today = LocalDate.now()
        if (lastLoginDate.isEmpty()) {
            dailyStreak = 1
            lastLoginDate = today.toString()
            val success = updateUserDataSuspend()
            Pair(success, dailyStreak)
        } else {
            val lastDate = LocalDate.parse(lastLoginDate)
            val daysBetween = ChronoUnit.DAYS.between(lastDate, today)

            when {
                daysBetween == 1L -> {
                    dailyStreak += 1
                    lastLoginDate = today.toString()
                    
                    if (dailyStreak == 7 && !badges.contains("7-Day Streak")) {
                        badges = badges + "7-Day Streak"
                    } else if (dailyStreak == 30 && !badges.contains("Monthly Habit")) {
                        badges = badges + "Monthly Habit"
                    }
                    
                    val successExp = addExperienceSuspend(10 * dailyStreak)
                    Pair(successExp, dailyStreak)
                }
                daysBetween > 1L -> {
                    dailyStreak = 1
                    lastLoginDate = today.toString()
                    val success = updateUserDataSuspend()
                    Pair(success, dailyStreak)
                }
                else -> Pair(true, dailyStreak)
            }
        }
    } catch (e: Exception) {
        Pair(false, dailyStreak)
    }

    suspend fun addExperienceSuspend(amount: Int): Boolean = try {
        experience += amount
        points += amount
        val newLevel = (experience / 100) + 1
        if (newLevel > level) {
            level = newLevel
            if (!badges.contains("Level $level")) {
                badges = badges + "Level $level"
            }
        }
        updateUserDataSuspend()
    } catch (e: Exception) {
        false
    }

    suspend fun addBadgeSuspend(badge: String): Boolean = try {
        if (!badges.contains(badge)) {
            badges = badges + badge
            updateUserDataSuspend()
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }
}
