package com.miireader.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperButtonStyle
import com.miireader.ui.components.EpaperCard
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.NearBlack

/**
 * The saved-catalog list: add a feed by URL, open one, or remove one.
 *
 * The add field is deliberately a fixed-height text field rather than a floating overlay,
 * so the layout never shifts under it — important on e-paper, where a shifting layout
 * forces a full-screen repaint.
 */
@Composable
internal fun CatalogListContent(
    uiState: CatalogUiState,
    onAddCatalog: (String) -> Unit,
    onRemoveCatalog: (String) -> Unit,
    onOpenFeed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftUrl by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EpaperDimensions.PaddingLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                label = { Text("Catalog URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (draftUrl.isNotBlank()) {
                            onAddCatalog(draftUrl.trim())
                            draftUrl = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(EpaperDimensions.PaddingMedium))

            EpaperButton(
                text = "Add",
                onClick = {
                    if (draftUrl.isNotBlank()) {
                        onAddCatalog(draftUrl.trim())
                        draftUrl = ""
                    }
                },
                enabled = !uiState.isAddingCatalog && draftUrl.isNotBlank()
            )
        }

        if (uiState.savedCatalogs.isEmpty()) {
            EpaperEmptyState(
                title = "No catalogs yet",
                message = "Add a public OPDS feed address above to browse and download books. " +
                    "MiiReader reads OPDS 1 (Atom) and OPDS 2 (JSON) feeds.",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(EpaperDimensions.PaddingMedium),
                contentPadding = PaddingValues(
                    horizontal = EpaperDimensions.PaddingLarge,
                    vertical = EpaperDimensions.PaddingSmall
                )
            ) {
                items(
                    items = uiState.savedCatalogs,
                    key = { it.id }
                ) { catalog ->
                    CatalogRow(
                        catalog = catalog,
                        onOpen = { onOpenFeed(catalog.url) },
                        onRemove = { onRemoveCatalog(catalog.id) }
                    )
                }
            }
        }
    }
}

/** One saved catalog: title, address, and separate open and remove actions. */
@Composable
private fun CatalogRow(
    catalog: CatalogItemUi,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    EpaperCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EpaperDimensions.PaddingLarge)
                .heightIn(min = EpaperDimensions.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = catalog.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = NearBlack
                )
                Text(
                    text = catalog.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkGray
                )
            }

            EpaperIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Remove ${catalog.displayTitle}",
                onClick = onRemove
            )
        }
    }
}
