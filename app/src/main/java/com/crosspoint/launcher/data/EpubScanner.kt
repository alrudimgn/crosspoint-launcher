package com.crosspoint.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.crosspoint.launcher.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Scans common storage folders for EPUB, FB2, PDF and DJVU files and extracts their cover image + metadata.
 * Covers are cached as downscaled grayscale-friendly JPEGs under cacheDir/covers.
 */
class EpubScanner(private val context: Context, private val sync: SyncRepository) {

    private val coverDir: File by lazy {
        File(context.cacheDir, "covers").apply { mkdirs() }
    }

    private fun defaultScanRoots(customRoot: String): List<File> {
        if (customRoot.isNotBlank()) {
            val root = File(customRoot)
            if (root.exists() && root.canRead()) {
                return listOf(root)
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        val booksDir = File(ext, "Books")
        if (booksDir.exists() && booksDir.canRead()) {
            return listOfNotNull(
                booksDir,
                File(ext, "Download"),
                File(ext, "Downloads"),
                File(ext, "Documents"),
                File(ext, "KOReader")
            ).distinctBy { it.absolutePath }
        }
        return listOfNotNull(
            File(ext, "Download"),
            File(ext, "Downloads"),
            File(ext, "Documents"),
            File(ext, "KOReader")
        ).distinctBy { it.absolutePath }
    }

    suspend fun scan(
        extraFolders: List<String>, 
        rootFolder: String = "",
        onBatch: (suspend (List<Book>) -> Unit)? = null
    ): List<Book> = withContext(Dispatchers.IO) {
        val roots = (defaultScanRoots(rootFolder) + extraFolders.map { File(it) })
            .filter { it.exists() && it.canRead() }
        val seen = HashSet<String>()
        val files = ArrayList<File>()
        val visited = HashMap<String, Int>()
        for (root in roots) {
            collectBooks(root, files, seen, visited, depth = 4)
        }
        files.sortByDescending { it.lastModified() }
        val result = ArrayList<Book>(files.size)
        var lastEmitted = System.currentTimeMillis()
        for (file in files) {
            coroutineContext.ensureActive()
            val book = runCatching {
                cachedParse(file).let { b ->
                    b.copy(coverPath = b.coverPath ?: sync.cachedCover(b))
                }
            }.getOrNull()
            if (book != null) {
                result.add(book)
            }
            if (onBatch != null && (result.size == 6 || result.size == 18 || result.size % 40 == 0 || (System.currentTimeMillis() - lastEmitted > 500 && result.isNotEmpty()))) {
                lastEmitted = System.currentTimeMillis()
                onBatch(result.toList())
            }
        }
        if (onBatch != null) {
            onBatch(result.toList())
        }
        result
    }

    private val ignoredDirNames = setOf(
        "android", "lost.dir", "dcim", "pictures", "music", "movies",
        "alarms", "ringtones", "notifications", "system volume information",
        ".git", ".idea", "cache", "caches", "mireader", "duokan"
    )

    private fun collectBooks(dir: File, out: MutableList<File>, seen: HashSet<String>, visited: MutableMap<String, Int>, depth: Int) {
        if (depth < 0) return
        val path = dir.safeCanonicalPath()
        if ((visited[path] ?: -1) >= depth) return
        visited[path] = depth
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                val lname = f.name.lowercase()
                if (lname.startsWith(".") || lname.endsWith(".sdr") || lname in ignoredDirNames) continue
                collectBooks(f, out, seen, visited, depth - 1)
            } else if (isSupportedBook(f)) {
                val key = f.safeCanonicalPath()
                if (seen.add(key)) out.add(File(key))
            }
        }
    }

    private fun isSupportedBook(file: File): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".epub") || name.endsWith(".fb2") || name.endsWith(".fb2.zip") ||
            name.endsWith(".pdf") || name.endsWith(".djvu") || name.endsWith(".djv")) {
            return true
        }
        if (name.endsWith(".zip")) {
            return runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().any {
                        val ename = it.name.lowercase()
                        (ename.endsWith(".fb2") || ename.endsWith(".epub")) && !ename.startsWith("__macosx")
                    }
                }
            }.getOrDefault(false)
        }
        return false
    }

    fun cachedParsePublic(file: File): Book = cachedParse(file)

    private fun cachedParse(file: File): Book {
        val cache = File(context.cacheDir, "epub_metadata").apply { mkdirs() }
        val entry = File(cache, sha1(file.safeCanonicalPath()) + ".json")
        runCatching {
            val json = JSONObject(entry.readText())
            val cover = json.optString("cover").ifBlank { null }
            val hasNoCover = json.optBoolean("hasNoCover", false)
            if (json.getLong("modified") == file.lastModified() && json.getLong("size") == file.length() &&
                ((cover != null && File(cover).isFile) || hasNoCover)) {
                return Book(file.safeCanonicalPath(), json.getString("title"), json.getString("author"), cover, file.lastModified())
            }
        }
        val book = parse(file)
        runCatching {
            val temp = File(entry.path + ".tmp")
            temp.writeText(JSONObject().apply {
                put("modified", file.lastModified()); put("size", file.length())
                put("title", book.title); put("author", book.author); put("cover", book.coverPath.orEmpty())
                put("hasNoCover", book.coverPath == null)
            }.toString())
            if (!temp.renameTo(entry)) temp.delete()
        }
        return book
    }

    private fun parse(file: File): Book {
        val name = file.name.lowercase()
        if (com.crosspoint.launcher.reader.Fb2Document.isFb2(file)) {
            return parseFb2(file)
        }
        if (name.endsWith(".pdf")) {
            return parsePdf(file)
        }
        if (name.endsWith(".djvu") || name.endsWith(".djv")) {
            return parseDjvu(file)
        }
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            var title = file.nameWithoutExtension
            var author = ""
            var coverHref: String? = null
            var coverId: String? = null
            val manifest = HashMap<String, String>()        // id -> href
            val manifestProps = HashMap<String, String>()   // id -> properties

            if (opfPath != null) {
                zip.getEntry(opfPath)?.let { entry ->
                    com.crosspoint.launcher.reader.EpubBook.readEntry(zip, entry.name).inputStream().use { input ->
                        val parser = newParser(input)
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "title" -> parser.nextText().trim().let { if (it.isNotEmpty()) title = it }
                                    "creator" -> parser.nextText().trim().let { if (it.isNotEmpty() && author.isEmpty()) author = it }
                                    "meta" -> {
                                        if (parser.getAttributeValue(null, "name") == "cover") {
                                            coverId = parser.getAttributeValue(null, "content")
                                        }
                                    }
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id")
                                        val href = parser.getAttributeValue(null, "href")
                                        val props = parser.getAttributeValue(null, "properties")
                                        if (id != null && href != null) {
                                            manifest[id] = href
                                            if (props != null) manifestProps[id] = props
                                            if (props != null && props.contains("cover-image")) coverHref = href
                                        }
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                }
                if (coverHref == null && coverId != null) coverHref = manifest[coverId]
                if (coverHref != null) coverHref = resolveRelative(opfPath, coverHref!!)
            }

            // Fallbacks: guess a cover entry by common naming.
            if (coverHref == null) {
                coverHref = guessCover(zip)
            }

            val coverFile = coverHref?.let { extractCover(zip, it, file) }
            return Book(
                filePath = file.safeCanonicalPath(),
                title = title,
                author = author,
                coverPath = coverFile?.absolutePath,
                lastModified = file.lastModified()
            )
        }
    }

    private fun parsePdf(file: File): Book {
        val title = file.nameWithoutExtension
        val coverFile = extractPdfCover(file)
        return Book(
            filePath = file.safeCanonicalPath(),
            title = title,
            author = "",
            coverPath = coverFile?.absolutePath,
            lastModified = file.lastModified()
        )
    }

    private fun extractPdfCover(file: File): File? {
        val hash = sha1(file.absolutePath + ":" + file.lastModified() + ":" + file.length())
        val out = File(coverDir, "$hash.jpg")
        if (out.exists() && out.length() > 0) return out
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
            pfd.use { fd ->
                val renderer = PdfRenderer(fd)
                renderer.use { r ->
                    if (r.pageCount == 0) return null
                    val page = r.openPage(0)
                    page.use { p ->
                        val aspect = p.width.toFloat() / p.height.toFloat().coerceAtLeast(1f)
                        val targetHeight = 800
                        val targetWidth = (targetHeight * aspect).toInt().coerceIn(300, 800)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val temp = File(out.path + ".tmp")
                        FileOutputStream(temp).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        }
                        bitmap.recycle()
                        if (temp.renameTo(out)) out else temp.takeIf { it.exists() }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to render PDF cover for ${file.name}: ${e.message}")
            null
        }
    }

    private fun parseDjvu(file: File): Book {
        val title = file.nameWithoutExtension
        val parent = file.parentFile
        val base = file.nameWithoutExtension
        val sidecar = listOfNotNull(
            parent?.let { File(it, "$base.jpg") },
            parent?.let { File(it, "$base.jpeg") },
            parent?.let { File(it, "$base.png") },
            parent?.let { File(it, "$base.cover.jpg") }
        ).firstOrNull { it.exists() && it.isFile }
        return Book(
            filePath = file.safeCanonicalPath(),
            title = title,
            author = "",
            coverPath = sidecar?.absolutePath,
            lastModified = file.lastModified()
        )
    }

    private fun parseFb2(file: File): Book {
        val meta = com.crosspoint.launcher.reader.Fb2Document.extractMetadata(file)
        val coverFile = meta.coverBytes?.let { bytes -> extractFb2Cover(bytes, file) }
        return Book(
            filePath = file.safeCanonicalPath(),
            title = meta.title,
            author = meta.author,
            coverPath = coverFile?.absolutePath,
            lastModified = file.lastModified()
        )
    }

    private fun extractFb2Cover(imageBytes: ByteArray, book: File): File? {
        val hash = sha1(book.absolutePath + ":" + book.lastModified() + ":" + book.length())
        val out = File(coverDir, "$hash.jpg")
        if (out.exists() && out.length() > 0) return out
        return try {
            if (CoverImages.save({ java.io.ByteArrayInputStream(imageBytes) }, out)) out else null
        } catch (e: Exception) {
            Log.w(TAG, "fb2 cover extract failed for ${book.name}: ${e.message}")
            null
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerPath = zip.getEntry("META-INF/container.xml")?.name
            ?: zip.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith("/META-INF/container.xml", ignoreCase = true) }
                .minByOrNull { it.length }
            ?: return null
        val container = zip.getEntry(containerPath) ?: return null
        val containerBase = containerPath.removeSuffix("META-INF/container.xml")
        com.crosspoint.launcher.reader.EpubBook.readEntry(zip, container.name).inputStream().use { input ->
            val parser = newParser(input)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val fullPath = parser.getAttributeValue(null, "full-path") ?: return null
                    return if (containerBase.isBlank() || fullPath.startsWith("/")) {
                        fullPath.trimStart('/')
                    } else {
                        containerBase + fullPath
                    }
                }
                event = parser.next()
            }
        }
        return null
    }

    private fun guessCover(zip: ZipFile): String? {
        val candidates = zip.entries().asSequence()
            .map { it.name }
            .filter { name ->
                val lower = name.lowercase()
                (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) &&
                    lower.contains("cover")
            }
            .toList()
        return candidates.minByOrNull { it.length }
    }

    private fun resolveRelative(opfPath: String, href: String): String =
        com.crosspoint.launcher.reader.EpubBook.resolve(opfPath, href)

    private fun extractCover(zip: ZipFile, href: String, book: File): File? {
        val entry = zip.getEntry(href) ?: zip.getEntry(href.removePrefix("/")) ?: return null
        val hash = sha1(book.absolutePath + ":" + book.lastModified() + ":" + book.length())
        val out = File(coverDir, "$hash.jpg")
        if (out.exists() && out.length() > 0) return out
        return try {
            if (entry.size > 16 * 1024 * 1024) return null
            if (CoverImages.save({ zip.getInputStream(entry) }, out)) out else null
        } catch (e: Exception) {
            Log.w(TAG, "cover extract failed for ${book.name}: ${e.message}")
            null
        }
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply { setInput(input, null) }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun File.safeCanonicalPath(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    companion object {
        private const val TAG = "EpubScanner"
    }
}
