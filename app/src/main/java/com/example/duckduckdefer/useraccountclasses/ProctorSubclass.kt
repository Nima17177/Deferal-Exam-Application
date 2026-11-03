package com.example.duckduckdefer.useraccountclasses

class ProctorSubclass(
    thisUID: String = "0",
    thisEmail: String = "doej@mymacewan.ca",
    thisUserName: String = "User",
    thisRole: Int = 2,
    thisApprovalStatus: Int = 0,
) : FacultySubclass(
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
        thisRole = 2,
        thisApprovalStatus = 0
    )
}
