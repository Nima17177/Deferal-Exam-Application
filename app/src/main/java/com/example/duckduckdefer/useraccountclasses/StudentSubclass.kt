package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.firestore.FirebaseFirestore

class StudentSubclass(
    thisUID: String = "0",
    thisEmail: String = "doej@mymacewan.ca",
    thisUserName: String = "User",
    thisRole: Int = 3,
    thisApprovalStatus: Int = 0
) : UserMembersClass(
    thisUID = thisUID,
    thisEmail = thisEmail,
    thisUserName = thisUserName,
    thisRole = thisRole,
    thisApprovalStatus = thisApprovalStatus
) {

    constructor() : this(
        thisUID = "0",
        thisEmail = "doej@mymacewan.ca",
        thisUserName = "User",
        thisRole = 3,
        thisApprovalStatus = 0
    )

    fun getMyBookings(onComplete: (List<String>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("StudentBookings")
            .document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val list = document.get("bookings") as? List<String> ?: emptyList()
                    onComplete(list)
                } else {
                    onComplete(emptyList())
                }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    fun setDeferral() {}

    fun getDeferral() {}
}
