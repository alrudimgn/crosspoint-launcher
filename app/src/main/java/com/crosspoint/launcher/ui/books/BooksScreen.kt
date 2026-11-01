package com.crosspoint.launcher.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crosspoint.launcher.model.AppLanguage
import com.crosspoint.launcher.model.Book
import com.crosspoint.launcher.ui.components.BodyText
import com.crosspoint.launcher.ui.components.BookCover
import com.crosspoint.launcher.ui.components.HeaderIconButton
import com.crosspoint.launcher.ui.components.Pressable
import com.crosspoint.launcher.ui.components.ScreenHeader
import com.crosspoint.launcher.ui.components.ScrollList
import com.crosspoint.launcher.ui.home.StorageHint
import com.crosspoint.launcher.ui.home.rememberCover
import com.crosspoint.launcher.ui.icons.LucideIcon
import com.crosspoint.launcher.ui.theme.CrossPointTheme
import com.crosspoint.launcher.util.L10n
import com.crosspoint.launcher.util.LocalAppLanguage
import java.io.File

private data class FolderEntry(
    val name: String,
    val fullPath: String,
    val bookCount: Int
)

@Composable
fun BooksScreen(
    books: List<Book>,
    loading: Boolean,
    hasStorage: Boolean,
    onOpen: (String) -> Unit,
    onOpenChooser: (String) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onGrantStorage: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    title: String = "Library",
    rootFolder: String = ""
) {
    val m = CrossPointTheme.metrics
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val isLibrary = title != L10n.recentBooks(lang)
    var isFolderMode by rememberSaveable { mutableStateOf(isLibrary) }
    var currentFolder by rememberSaveable { mutableStateOf<String?>(null) }

    val effectiveRoot = remember(books, rootFolder) {
        if (rootFolder.isNotBlank() && File(rootFolder).isDirectory) {
            runCatching { File(rootFolder).canonicalPath }.getOrDefault(rootFolder)
        } else {
            val parentPaths = books.mapNotNull {
                val p = File(it.filePath).parentFile
                if (p != null) runCatching { p.canonicalPath }.getOrNull() else null
            }.distinct()
            findCommonFolder(parentPaths) ?: "/storage/emulated/0/Books"
        }
    }

    val activeFolder = currentFolder ?: effectiveRoot
    val canGoUp = isLibrary && isFolderMode && activeFolder != effectiveRoot && activeFolder.startsWith(effectiveRoot)

    val navigateUp = {
        if (canGoUp) {
            val parent = File(activeFolder).parentFile?.canonicalPath
            currentFolder = if (parent == null || !parent.startsWith(effectiveRoot)) effectiveRoot else parent
        } else {
            onBack()
        }
    }

    BackHandler(enabled = canGoUp) {
        navigateUp()
    }

    val (subfolders, directBooks) = remember(books, activeFolder, isFolderMode, isLibrary) {
        if (!isLibrary || !isFolderMode) {
            emptyList<FolderEntry>() to books
        } else {
            val normActive = if (activeFolder.endsWith("/")) activeFolder else "$activeFolder/"
            val folderCounts = mutableMapOf<String, Int>()
            val direct = mutableListOf<Book>()

            for (book in books) {
                val path = book.filePath
                if (path.startsWith(normActive)) {
                    val rel = path.substring(normActive.length)
                    val slashIdx = rel.indexOf('/')
                    if (slashIdx >= 0) {
                        val subName = rel.substring(0, slashIdx)
                        folderCounts[subName] = (folderCounts[subName] ?: 0) + 1
                    } else {
                        direct.add(book)
                    }
                }
            }

            val folders = folderCounts.entries
                .sortedBy { it.key.lowercase() }
                .map { FolderEntry(name = it.key, fullPath = "$activeFolder/${it.key}", bookCount = it.value) }

            val sortedDirect = direct.sortedBy { it.title.lowercase() }
            folders to sortedDirect
        }
    }

    val currentHeaderTitle = when {
        !isLibrary -> title
        !isFolderMode -> title
        activeFolder == effectiveRoot -> title
        else -> File(activeFolder).name
    }

    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        ScreenHeader(currentHeaderTitle, onBack = navigateUp) {
            val counterText = if (isLibrary && isFolderMode) {
                if (activeFolder == effectiveRoot) {
                    "${books.size}"
                } else {
                    val totalInTree = subfolders.sumOf { it.bookCount } + directBooks.size
                    "$totalInTree"
                }
            } else {
                "${books.size}"
            }
            BodyText(counterText, muted = true, size = 14)
            if (isLibrary) {
                HeaderIconButton(
                    if (isFolderMode) "rows_3" else "folder",
                    onClick = { isFolderMode = !isFolderMode },
                    iconSize = 20.dp
                )
            }
            HeaderIconButton("rotate_cw", onClick = onRefresh, iconSize = 20.dp)
        }

        when {
            !hasStorage -> Box(Modifier.padding(m.contentSidePadding)) { StorageHint(onGrantStorage) }
            books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BodyText(if (loading) L10n.scanning(lang) else L10n.noBooksFound(lang), muted = true, size = 15)
            }
            isFolderMode && isLibrary && subfolders.isEmpty() && directBooks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BodyText(if (isRu) "Папка пуста" else "Folder is empty", muted = true, size = 15)
                }
            }
            else -> ScrollList(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = m.contentSidePadding, vertical = 8.dp)
            ) {
                if (isFolderMode && isLibrary) {
                    if (canGoUp) {
                        item(key = "up:folder") {
                            val parentName = File(activeFolder).parentFile?.name ?: ".."
                            UpFolderRow(parentName, isRu, onClick = navigateUp)
                        }
                    }
                    items(subfolders, key = { "dir:" + it.fullPath }) { folder ->
                        FolderRow(folder, isRu) {
                            currentFolder = folder.fullPath
                        }
                    }
                    items(directBooks, key = { "book:" + it.filePath }) { book ->
                        BookRow(book, onOpen, onBookLongPress)
                    }
                } else {
                    items(books, key = { it.filePath }) { book ->
                        BookRow(book, onOpen, onBookLongPress)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpFolderRow(parentName: String, isRu: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon("arrow_left", Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            BodyText(".. ($parentName)", weight = FontWeight.Medium, size = 16)
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntry, isRu: Boolean, onClick: () -> Unit) {
    val colors = CrossPointTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon("folder", Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BodyText(folder.name, weight = FontWeight.SemiBold, size = 17, maxLines = 1)
                BodyText(
                    "${folder.bookCount} " + if (isRu) pluralBooksRu(folder.bookCount) else pluralBooksEn(folder.bookCount),
                    muted = true,
                    size = 13
                )
            }
            LucideIcon("chevron_right", Modifier.size(20.dp), tintOverride = colors.muted)
        }
    }
}

private fun pluralBooksRu(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..19 -> "книг"
        mod10 == 1 -> "книга"
        mod10 in 2..4 -> "книги"
        else -> "книг"
    }
}

private fun pluralBooksEn(count: Int): String = if (count == 1) "book" else "books"

private fun findCommonFolder(paths: List<String>): String? {
    if (paths.isEmpty()) return null
    if (paths.size == 1) return paths.first()
    val splitPaths = paths.map { it.split(File.separatorChar).filter { seg -> seg.isNotEmpty() } }
    val minLen = splitPaths.minOfOrNull { it.size } ?: return null
    val common = mutableListOf<String>()
    for (i in 0 until minLen) {
        val segment = splitPaths[0][i]
        if (splitPaths.all { it[i] == segment }) {
            common.add(segment)
        } else {
            break
        }
    }
    if (common.isEmpty()) return null
    return "/" + common.joinToString("/")
}

@Composable
private fun BookRow(book: Book, onOpen: (String) -> Unit, onLongPress: (Book) -> Unit) {
    val cover = rememberCover(book.coverPath)
    Pressable(
        onClick = { onOpen(book.filePath) },
        onLongClick = { onLongPress(book) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().height(112.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.title, book.author, cover, Modifier.height(96.dp).width(64.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyText(book.title, weight = FontWeight.SemiBold, size = 18, maxLines = 2)
                if (book.author.isNotBlank() && !book.author.equals("Unknown", ignoreCase = true)) {
                    BodyText(book.author, muted = true, size = 14, maxLines = 2)
                }
            }
        }
    }
}
