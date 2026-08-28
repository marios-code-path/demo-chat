package com.demo.chat.domain

/**
 * The outcome of a claim, a renew, or a release.
 *
 * [Denied] means one thing only. The store answered, and it named another
 * owner. [Lost] means the store answered and found no live claim. Every
 * infrastructure failure is an error, not a result.
 */
sealed class ClaimResult {
    object Granted : ClaimResult()
    data class Denied(val holder: String) : ClaimResult()
    object Lost : ClaimResult()
}
