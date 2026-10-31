package com.miireader.data.opds

import android.util.Base64

/**
 * HTTP Basic credentials for a catalog that is not public.
 *
 * Some OPDS servers (BookFlow, Calibre-Web, Kavita, …) guard both their feeds and their
 * acquisition links behind HTTP Basic. The two fields are carried on the saved-catalog
 * row and copied onto queued downloads, so a retry after process death still
 * authenticates; they are never concatenated into the URL.
 */
data class CatalogCredentials(
    val username: String,
    val password: String
) {

    /** The value for an `Authorization` request header. */
    fun toAuthorizationHeader(): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    companion object {
        /**
         * Builds credentials only when the user actually supplied a username. A blank
         * username means "this catalog is public", not "anonymous Basic auth".
         *
         * The username is trimmed (leading/trailing spaces are always accidental); the
         * password is kept verbatim, because spaces can be significant.
         */
        fun ofOrNull(username: String?, password: String?): CatalogCredentials? {
            val name = username?.trim().orEmpty()
            if (name.isEmpty()) return null
            return CatalogCredentials(name, password.orEmpty())
        }
    }
}
