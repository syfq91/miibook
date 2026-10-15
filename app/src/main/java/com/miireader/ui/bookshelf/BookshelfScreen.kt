package com.miireader.ui.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.miireader.core.model.FilterMode
import com.miireader.core.model.SortOrder
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperDivider
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperLoadingIndicator
import com.miireader.ui.components.EpaperProgressBar
import com.miireader.ui.components.EpaperTopBar
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.HighContrastErrorBorder
import com.miireader.ui.theme.HighContrastErrorText
import com.miireader.ui.theme.InvertedBackground
import com.miireader.ui.theme.InvertedText
import com.miireader.ui.theme.LightBorderGray
import com.miireader.ui.theme.NearBlack

@Composable
fun BookshelfScreen(
    uiState: BookshelfUiState,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onFilterSelected: (FilterMode) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onBookClick: (String) -> Unit,
    onImportEpubClick: () -> Unit,
    onImportUriSelected: (Uri) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteBook: (String) -> Unit = {}
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Storage Access Framework picker for EPUB import. The picked content:// URI
    // is handed to the ViewModel, which copies it into app-private storage.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUriSelected(uri)
        }
    }

    val launchImport = {
        importLauncher.launch(arrayOf("application/epub+zip", "application/zip"))
    }

    // Override the plain click callback so both the empty-state button and the
    // bottom action bar trigger the real SAF picker instead of the placeholder.
    val handleImportClick = { launchImport() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        // E-Paper Top Header
        EpaperTopBar(
            title = "MiiReader",
            actions = {
                EpaperIconButton(
                    icon = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (uiState.isSearchActive) "Close search" else "Search books",
                    onClick = { onToggleSearch(!uiState.isSearchActive) }
                )
                EpaperIconButton(
                    icon = Icons.Default.LocalLibrary,
                    contentDescription = "OPDS Catalogs",
                    onClick = onNavigateToCatalog
                )
                EpaperIconButton(
                    icon = Icons.Default.FileDownload,
                    contentDescription = "Downloads",
                    onClick = onNavigateToDownloads
                )
                EpaperIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    onClick = onNavigateToSettings
                )
            }
        )

        // Error Banner if present
        if (uiState.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                border = BorderStroke(EpaperDimensions.BorderThin, HighContrastErrorBorder),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = HighContrastErrorText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    EpaperButton(
                        text = "Dismiss",
                        onClick = onClearError,
                        style = EpaperButtonStyle.TEXT
                    )
                }
            }
        }

        // Search Input Bar
        if (uiState.isSearchActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                border = BorderStroke(EpaperDimensions.BorderMedium, NearBlack),
                shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = NearBlack,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(NearBlack),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by title or author...",
                                    color = DarkGray,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        Text(
                            text = "[Clear]",
                            color = NearBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onSearchQueryChanged("") }
                        )
                    }
                }
            }
        }

        // Filter and Sort Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Tabs (All / Recent / Unread)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterMode.entries.forEach { mode ->
                    val isSelected = uiState.filterMode == mode
                    Surface(
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onFilterSelected(mode) }
                            ),
                        shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                        color = if (isSelected) InvertedBackground else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isSelected) EpaperDimensions.BorderMedium else EpaperDimensions.BorderThin,
                            NearBlack
                        )
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) InvertedText else NearBlack,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Sort Selector
            Box {
                Surface(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { sortMenuExpanded = true }
                        ),
                    shape = RoundedCornerShape(EpaperDimensions.CornerSmall),
                    border = BorderStroke(EpaperDimensions.BorderThin, NearBlack),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort: ${uiState.sortOrder.label}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = NearBlack
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.label,
                                    fontWeight = if (uiState.sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortOrderSelected(order)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        EpaperDivider(thickness = EpaperDimensions.BorderThin)

        // Main Bookshelf List Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EpaperLoadingIndicator(text = "Loading Library...")
                    }
                }
                uiState.isImporting -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EpaperLoadingIndicator(text = "Importing Book...")
                    }
                }
                uiState.books.isEmpty() -> {
                    EpaperEmptyState(
                        title = if (uiState.searchQuery.isNotEmpty()) "No Matching Books" else "Library is Empty",
                        message = if (uiState.searchQuery.isNotEmpty()) {
                            "No books found matching \"${uiState.searchQuery}\"."
                        } else {
                            "You have not imported any EPUB books yet. Tap below to import a local book or browse OPDS catalogs."
                        },
                        actionButtonText = if (uiState.searchQuery.isNotEmpty()) null else "Import EPUB",
                        onActionClick = handleImportClick
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.books,
                            key = { it.id } // Stable ID keys per plan.md Section 4.2
                        ) { book ->
                            BookRowItem(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onDelete = { onDeleteBook(book.id) }
                            )
                            EpaperDivider(thickness = EpaperDimensions.BorderThin)
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(EpaperDimensions.BorderThin, LightBorderGray),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.books.size} ${if (uiState.books.size == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkGray
                )

                EpaperButton(
                    text = if (uiState.isImporting) "Importing..." else "+ Import EPUB",
                    onClick = handleImportClick,
                    style = EpaperButtonStyle.PRIMARY
                )
            }
        }
    }
}

/**
 * Text-first book item row designed for e-paper displays.
 * Displays title, author, reading progress percentage, thin static progress bar, and open action.
 */
@Composable
fun BookRowItem(
    book: BookItemUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null, // No ripple animation
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NearBlack,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Reading Progress Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                EpaperProgressBar(
                    progress = book.progressPercentage,
                    modifier = Modifier.weight(1f),
                    height = 5.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                val percentInt = (book.progressPercentage * 100).toInt()
                Text(
                    text = "$percentInt%",
                    style = MaterialTheme.typography.labelMedium,
                    color = DarkGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        EpaperButton(
            text = "Open",
            onClick = onClick,
            style = EpaperButtonStyle.OUTLINE
        )

        Spacer(modifier = Modifier.width(8.dp))

        EpaperIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Delete book",
            onClick = onDelete
        )
    }
}
