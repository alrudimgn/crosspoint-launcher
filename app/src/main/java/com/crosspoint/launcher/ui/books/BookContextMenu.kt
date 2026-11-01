package com.crosspoint.launcher.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspoint.launcher.model.AppLanguage
import com.crosspoint.launcher.model.Book
import com.crosspoint.launcher.ui.components.BodyText
import com.crosspoint.launcher.ui.icons.LucideIcon
import com.crosspoint.launcher.ui.theme.CrossPointTheme
import com.crosspoint.launcher.util.LocalAppLanguage

/**
 * Long-press context menu for a book: Open, Open with…, Rename, Delete. Rename and Delete show an
 * inline confirmation step. Styled for the e-ink B&W theme. Rendered as an in-compose overlay
 * to guarantee instantaneous touch response on Android 8.1 / e-ink touch screens.
 */
@Composable
fun BookContextMenu(
    book: Book,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    isInRecent: Boolean = false,
    onRemoveFromRecent: (() -> Unit)? = null
) {
    val colors = CrossPointTheme.colors
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN
    var mode by remember { mutableStateOf(Mode.MENU) }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .border(2.dp, colors.divider, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* swallow tap inside dialog */ }
                .padding(vertical = 8.dp)
        ) {
            // Title
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BodyText(book.title, weight = FontWeight.Bold, size = 16, maxLines = 2)
                if (book.author.isNotBlank()) BodyText(book.author, muted = true, size = 12)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

            when (mode) {
                Mode.MENU -> {
                    MenuRow("book_open", if (isRu) "Открыть" else "Open") { onOpen() }
                    MenuRow("library", if (isRu) "Открыть с помощью…" else "Open with…") { onOpenWith() }
                    if (isInRecent && onRemoveFromRecent != null) {
                        MenuRow("x", if (isRu) "Убрать из истории" else "Remove from history") { onRemoveFromRecent() }
                    }
                    MenuRow("file_text", if (isRu) "Переименовать" else "Rename") { mode = Mode.RENAME }
                    MenuRow("trash_2", if (isRu) "Удалить файл с устройства" else "Delete file from device") { mode = Mode.DELETE }
                }
                Mode.RENAME -> RenamePanel(
                    initial = book.title,
                    isRu = isRu,
                    onCancel = { mode = Mode.MENU },
                    onConfirm = onRename
                )
                Mode.DELETE -> ConfirmDeletePanel(
                    isRu = isRu,
                    onCancel = { mode = Mode.MENU },
                    onConfirm = onDelete
                )
            }
        }
    }
}

private enum class Mode { MENU, RENAME, DELETE }

@Composable
private fun MenuRow(icon: String, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon(icon, Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            BodyText(label, weight = FontWeight.Medium, size = 16)
        }
    }
}

@Composable
private fun RenamePanel(initial: String, isRu: Boolean, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = CrossPointTheme.colors
    var text by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText(if (isRu) "Новое название" else "New name", muted = true, size = 13, weight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth()
                    .border(1.5.dp, colors.divider, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        MenuRow("check", if (isRu) "Сохранить" else "Save", onClick = { onConfirm(text) })
        MenuRow("x", if (isRu) "Отмена" else "Cancel", onClick = onCancel)
    }
}

@Composable
private fun ConfirmDeletePanel(isRu: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = CrossPointTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            BodyText(
                if (isRu) "Удалить файл книги с устройства навсегда?" else "Delete this book file from device permanently?",
                weight = FontWeight.Medium,
                size = 16
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        MenuRow(
            icon = "trash_2",
            label = if (isRu) "Да, удалить файл" else "Yes, delete file",
            onClick = onConfirm
        )
        MenuRow(
            icon = "x",
            label = if (isRu) "Отмена" else "Cancel",
            onClick = onCancel
        )
    }
}
