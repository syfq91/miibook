package com.miireader.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperTopBar

@Composable
fun CatalogScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        EpaperTopBar(
            title = "OPDS Catalogs",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationIconContentDescription = "Back to Bookshelf",
            onNavigationClick = onNavigateBack
        )

        EpaperEmptyState(
            title = "OPDS Catalog Browser",
            message = "OPDS catalog integration and downloads will be enabled in Phase 4. You will be able to add custom feeds and download public domain EPUBs.",
            actionButtonText = "Back to Bookshelf",
            onActionClick = onNavigateBack,
            modifier = Modifier.padding()
        )
    }
}
