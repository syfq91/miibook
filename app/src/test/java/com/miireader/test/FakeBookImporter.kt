package com.miireader.test

import android.net.Uri
import com.miireader.data.repository.BookImporter
import com.miireader.data.repository.ImportResult

/**
 * Test double for [BookImporter]. Configure [result] to control the outcome of
 * [importBook], and inspect [importedUris] to assert which URIs were imported.
 */
class FakeBookImporter(
    var result: ImportResult = ImportResult.Failure("No result configured")
) : BookImporter {

    val importedUris = mutableListOf<Uri>()
    var importCallCount = 0
        private set

    /** Optional hook to run side effects (e.g. add a book to a fake repository). */
    var onImport: ((Uri) -> Unit)? = null

    override suspend fun importBook(uri: Uri): ImportResult {
        importCallCount++
        importedUris.add(uri)
        onImport?.invoke(uri)
        return result
    }
}
