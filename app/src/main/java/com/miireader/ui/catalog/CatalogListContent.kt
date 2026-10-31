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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.miireader.ui.components.EpaperButton
import com.miireader.ui.components.EpaperCard
import com.miireader.ui.components.EpaperEmptyState
import com.miireader.ui.components.EpaperIconButton
import com.miireader.ui.components.EpaperLoadingIndicator
import com.miireader.ui.theme.DarkGray
import com.miireader.ui.theme.EpaperDimensions
import com.miireader.ui.theme.NearBlack

/**
 * The saved-catalog list: add or edit a feed by URL, open one, or remove one.
 *
 * The add/edit field is deliberately a fixed-height text field rather than a floating
 * overlay, so the layout never shifts under it — important on e-paper, where a shifting
 * layout forces a full-screen repaint. Editing reuses the same form (prefilled) instead of
 * opening a dialog for the same reason.
 */
@Composable
internal fun CatalogListContent(
    uiState: CatalogUiState,
    onSaveCatalog: (String?, String, String?, String?) -> Unit,
    onEditCatalog: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRemoveCatalog: (String) -> Unit,
    onOpenFeed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val editingCatalog = uiState.editingCatalog
    val editingId = editingCatalog?.id

    // Keyed on the edit target: switching catalogs (or leaving edit mode) refills the
    // form, while typing survives a recomposition or a rotation.
    var draftUrl by rememberSaveable(editingId) {
        mutableStateOf(editingCatalog?.url.orEmpty())
    }
    var draftUsername by rememberSaveable(editingId) {
        mutableStateOf(editingCatalog?.username.orEmpty())
    }
    // Not rememberSaveable: a typed password must not be serialized into the activity's
    // saved instance state.
    var draftPassword by remember(editingId) { mutableStateOf("") }

    val submitCatalog: () -> Unit = {
        if (draftUrl.isNotBlank() && !uiState.isAddingCatalog) {
            onSaveCatalog(
                editingId,
                draftUrl.trim(),
                draftUsername.trim().takeIf { it.isNotEmpty() },
                draftPassword.takeIf { draftUsername.isNotBlank() }
            )
            // An edit keeps the form until the save succeeds (so a rejected address does
            // not throw away the input); a new catalog is cleared immediately.
            if (editingId == null) {
                draftUrl = ""
                draftUsername = ""
                draftPassword = ""
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (editingCatalog != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = EpaperDimensions.PaddingLarge,
                        top = EpaperDimensions.PaddingLarge,
                        end = EpaperDimensions.PaddingLarge
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editing ${editingCatalog.displayTitle}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NearBlack,
                    modifier = Modifier.weight(1f)
                )
                EpaperButton(
                    text = "Cancel",
                    onClick = onCancelEdit,
                    enabled = !uiState.isAddingCatalog
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = EpaperDimensions.PaddingLarge,
                    top = EpaperDimensions.PaddingLarge,
                    end = EpaperDimensions.PaddingLarge
                ),
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
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(EpaperDimensions.PaddingMedium))

            EpaperButton(
                text = if (editingCatalog != null) "Save" else "Add",
                onClick = submitCatalog,
                enabled = !uiState.isAddingCatalog && draftUrl.isNotBlank()
            )
        }

        // Optional HTTP Basic sign-in. Kept on its own fixed row so adding credentials
        // never shifts the catalog list underneath it on an e-paper screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = EpaperDimensions.PaddingLarge,
                    end = EpaperDimensions.PaddingLarge,
                    bottom = EpaperDimensions.PaddingLarge
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draftUsername,
                onValueChange = { draftUsername = it },
                label = { Text("Username (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(EpaperDimensions.PaddingMedium))

            OutlinedTextField(
                value = draftPassword,
                onValueChange = { draftPassword = it },
                label = {
                    Text(
                        if (editingCatalog?.hasPassword == true) {
                            "Password (blank = keep)"
                        } else {
                            "Password (optional)"
                        }
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitCatalog() }),
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.isAddingCatalog || uiState.isLoadingFeed) {
            EpaperLoadingIndicator(
                text = when {
                    uiState.isAddingCatalog && editingId == null -> "Checking catalog..."
                    uiState.isAddingCatalog -> "Saving changes..."
                    else -> "Loading feed..."
                },
                modifier = Modifier.padding(
                    start = EpaperDimensions.PaddingLarge,
                    end = EpaperDimensions.PaddingLarge,
                    bottom = EpaperDimensions.PaddingSmall
                )
            )
        }

        if (uiState.savedCatalogs.isEmpty()) {
            EpaperEmptyState(
                title = "No catalogs yet",
                message = "Add an OPDS feed address above to browse and download books. " +
                    "If the catalog asks you to sign in, enter its username and password " +
                    "too. MiiReader reads OPDS 1 (Atom) and OPDS 2 (JSON) feeds.",
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
                        onEdit = { onEditCatalog(catalog.id) },
                        onRemove = { onRemoveCatalog(catalog.id) }
                    )
                }
            }
        }
    }
}

/** One saved catalog: title, address, and separate open, edit, and remove actions. */
@Composable
private fun CatalogRow(
    catalog: CatalogItemUi,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
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
                icon = Icons.Default.Edit,
                contentDescription = "Edit ${catalog.displayTitle}",
                onClick = onEdit
            )

            EpaperIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Remove ${catalog.displayTitle}",
                onClick = onRemove
            )
        }
    }
}
