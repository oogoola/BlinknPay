package com.example.blinknpay.models

/**
 * SignatoryRole defines the hierarchy and proximity identifiers for the BlinknPay Quorum.
 *
 * @property rpidPrefix The unique hex/string prefix propagated via Quad-Channel (BT/BLE/SSID/Ultrasound).
 * @property securityLevel 1 for members, 2 for signatories, 3 for ultimate override.
 * @property canInitiatePayout Whether this role can start a high-value transaction.
 */
enum class SignatoryRole(
    val rpidPrefix: String,
    val label: String,
    val securityLevel: Int,
    val canInitiatePayout: Boolean
) {
    CHAIR(
        rpidPrefix = "BP_RPID_01_CH",
        label = "Chairperson",
        securityLevel = 2,
        canInitiatePayout = true
    ),

    TREASURER(
        rpidPrefix = "BP_RPID_02_TR",
        label = "Treasurer",
        securityLevel = 2,
        canInitiatePayout = true
    ),

    SECRETARY_GENERAL(
        rpidPrefix = "BP_RPID_03_SG",
        label = "Secretary General",
        securityLevel = 2,
        canInitiatePayout = false
    ),

    MEMBER(
        rpidPrefix = "BP_RPID_00_MB",
        label = "Chama Member",
        securityLevel = 1,
        canInitiatePayout = false
    );

    companion object {
        /**
         * Validates an incoming scanned RPID against the known signatory prefixes.
         */
        fun fromRpid(scannedRpid: String): SignatoryRole? {
            return values().find { scannedRpid.startsWith(it.rpidPrefix) }
        }

        /**
         * Determines if a role has the clearance to view the hidden Authorization Card.
         */
        fun isSignatory(role: SignatoryRole): Boolean {
            return role.securityLevel >= 2
        }
    }
}