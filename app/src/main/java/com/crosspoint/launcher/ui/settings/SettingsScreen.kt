package com.crosspoint.launcher.ui.settings

import com.crosspoint.launcher.ui.components.ScrollList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.crosspoint.launcher.LauncherViewModel
import com.crosspoint.launcher.model.AppInfo
import com.crosspoint.launcher.model.AppLanguage
import com.crosspoint.launcher.model.AppearanceMode
import com.crosspoint.launcher.model.CoverSlotMode
import com.crosspoint.launcher.model.HomeMenuItem
import com.crosspoint.launcher.model.LauncherIconStyle
import com.crosspoint.launcher.model.SearchGesture
import com.crosspoint.launcher.model.ThemeType
import com.crosspoint.launcher.ui.components.BodyText
import com.crosspoint.launcher.ui.components.ScreenHeader
import com.crosspoint.launcher.ui.components.HeaderIconButton
import com.crosspoint.launcher.ui.components.LauncherAppIcon
import com.crosspoint.launcher.ui.components.Pressable
import com.crosspoint.launcher.ui.components.defaultIconForApp
import com.crosspoint.launcher.ui.components.key
import com.crosspoint.launcher.ui.icons.LucideIconLibrary
import com.crosspoint.launcher.ui.icons.LucideIcon
import com.crosspoint.launcher.ui.home.LauncherColumnItem
import com.crosspoint.launcher.ui.home.orderedHomeItems
import com.crosspoint.launcher.ui.theme.CrossPointTheme
import com.crosspoint.launcher.BuildConfig
import com.crosspoint.launcher.util.ApkInstall
import com.crosspoint.launcher.util.AppUpdater
import com.crosspoint.launcher.util.AuroraInstaller
import com.crosspoint.launcher.util.L10n
import com.crosspoint.launcher.util.LocalAppLanguage
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.io.File
import com.crosspoint.launcher.util.Launching
import com.crosspoint.launcher.util.DeviceSettings
import com.crosspoint.launcher.util.SleepScreen
import com.crosspoint.launcher.model.SleepScreenMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    onOpenOpdsServers: () -> Unit,
    onPickWidget: () -> Unit,
    onClearWidget: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = CrossPointTheme.colors
    val m = CrossPointTheme.metrics
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN
    val settings by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()
    val readerApps by vm.readerApps.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val syncBusy by vm.syncBusy.collectAsState()
    val hasStorage by vm.hasStorage.collectAsState()
    val opdsServers by vm.opdsServers.collectAsState()
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var editingIconFor by remember { mutableStateOf<AppInfo?>(null) }
    var folderPickerOpen by remember { mutableStateOf(false) }

    editingIconFor?.let { app ->
        IconPickerScreen(
            app = app,
            selected = settings.appIconOverrides[app.key] ?: defaultIconForApp(app),
            onSelect = { icon ->
                vm.setAppIconOverride(app.key, icon)
                editingIconFor = null
            },
            onReset = {
                vm.setAppIconOverride(app.key, null)
                editingIconFor = null
            },
            onBack = { editingIconFor = null }
        )
        return
    }

    if (folderPickerOpen) {
        FolderPickerDialog(
            initialFolder = settings.libraryFolder.ifBlank { "/sdcard/Books" },
            onSelectFolder = { selected ->
                vm.setLibraryFolder(selected)
                folderPickerOpen = false
                android.widget.Toast.makeText(context, L10n.folderSelectedToast(selected.ifBlank { L10n.libraryRootDefault(lang) }, lang), android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { folderPickerOpen = false }
        )
    }

    var category by remember { mutableStateOf<String?>(null) }
    val storeInstalled = apps.any { it.packageName == AuroraInstaller.PACKAGE }
    val scope = rememberCoroutineScope()
    val books by vm.books.collectAsState()
    val recentBook = remember(books, settings.lastOpenedBookPath) {
        books.firstOrNull { it.filePath == settings.lastOpenedBookPath } ?: books.maxByOrNull { it.lastModified }
    }
    val imagePickerAvailable = remember { context.packageManager.resolveActivity(android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*"), 0) != null }
    val pickSleepImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.Default) { SleepScreen.applyUri(context, uri) } }
    }
    var storeStatus by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }

    fun downloadAndInstall(setStatus: (String?) -> Unit, download: suspend (onProgress: (Int) -> Unit) -> File) {
        if (installing) return
        if (!ApkInstall.ensureCanInstall(context)) { setStatus(if (isRu) "Разрешите установку и повторите" else "Allow installs, then tap again"); return }
        installing = true
        scope.launch {
            try {
                setStatus(if (isRu) "Загрузка…" else "Downloading…")
                val apk = download { setStatus(if (isRu) "Загрузка… ${it / 10 * 10}%" else "Downloading… ${it / 10 * 10}%") }
                setStatus(null)
                ApkInstall.install(context, apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(e.message ?: if (isRu) "Ошибка загрузки" else "Download failed")
            } finally {
                installing = false
            }
        }
    }
    fun getStore() = downloadAndInstall({ storeStatus = it }) { AuroraInstaller.download(context, it) }

    var updateStatus by remember { mutableStateOf<String?>(null) }
    fun onVersionTap() {
        updateStatus = L10n.updatesDisabled(lang)
        scope.launch {
            delay(2500)
            updateStatus = null
        }
    }

    val categories = listOf(
        "Language" to "globe",
        "Home Theme" to "layout_grid",
        "Appearance" to "sun",
        "App Search" to "search",
        "CrossPoint Sync" to "crosspoint",
        "Default Reader" to "book_open",
        "Home Screen Items" to "rows_3",
        "OPDS Catalogs" to "library",
        "Library" to "folder_open",
        (if (storeInstalled) "App Store" else "Get App Store") to "store",
        "Device Settings" to "settings"
    )

    fun categoryLabel(key: String): String = when (key) {
        "Language" -> L10n.languageCategory(lang)
        "Home Theme" -> L10n.homeThemeCategory(lang)
        "Appearance" -> L10n.appearanceCategory(lang)
        "App Search" -> L10n.appSearchCategory(lang)
        "CrossPoint Sync" -> L10n.syncCategory(lang)
        "Default Reader" -> L10n.defaultReaderCategory(lang)
        "Home Screen Items" -> L10n.homeItemsCategory(lang)
        "OPDS Catalogs" -> L10n.opdsCategory(lang)
        "Library" -> L10n.libraryCategory(lang)
        "Device Settings" -> L10n.deviceSettingsCategory(lang)
        "App Store", "Get App Store" -> L10n.appStoreCategory(storeInstalled, lang)
        else -> key
    }

    androidx.activity.compose.BackHandler(category != null) { category = null }
    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        ScreenHeader(if (category == null) L10n.settings(lang) else categoryLabel(category!!), { if (category == null) onBack() else category = null }) {
            if (category == null) Pressable(onClick = ::onVersionTap) {
                BodyText(updateStatus ?: "v${BuildConfig.VERSION_NAME}",
                    weight = FontWeight.Normal, size = 13, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }
        }
        val bookCount = vm.books.collectAsState().value.size
        if (category == null) {
            ScrollList(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = m.contentSidePadding)) {
                items(categories) { (catKey, icon) ->
                    val displayLabel = categoryLabel(catKey)
                    Pressable(onClick = {
                        when (catKey) {
                            "App Store" -> Launching.launchPackage(context, AuroraInstaller.PACKAGE)
                            "Get App Store" -> getStore()
                            else -> category = catKey
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon(icon, Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            BodyText(if (catKey == "Get App Store") storeStatus ?: displayLabel else displayLabel, size = 18, modifier = Modifier.weight(1f))
                            LucideIcon("chevron_right", Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else ScrollList(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = m.contentSidePadding), state = remember(category) { androidx.compose.foundation.lazy.LazyListState() }) {
            when (category) {
                "Language" -> {
                    items(AppLanguage.entries) { item ->
                        val sub = if (item == AppLanguage.RUSSIAN) "Русский язык интерфейса" else "English user interface"
                        SelectRow(item.label, sub, settings.language == item) {
                            vm.setLanguage(item)
                        }
                    }
                }
                "Home Theme" -> items(ThemeType.entries.filter { it != ThemeType.CAROUSEL }) { theme ->
                    SelectRow(L10n.theme(theme, lang), L10n.themeDescription(theme, lang), settings.theme == theme) { vm.setTheme(theme) }
                }
                "Appearance" -> {
                    items(AppearanceMode.entries) { mode -> SelectRow(L10n.appearance(mode, lang), "", settings.appearance == mode) { vm.setAppearance(mode) } }
                    items(CoverSlotMode.entries) { mode -> SelectRow(L10n.coverSlot(mode, lang), if (isRu) "Область обложки" else "Cover area", settings.coverSlotMode == mode) { vm.setCoverSlotMode(mode) } }
                    if (settings.coverSlotMode == CoverSlotMode.WIDGET) {
                        item { ActionRow(if (isRu) "Выбрать виджет" else "Choose Widget", "", "app_window", onPickWidget) }
                        item { ActionRow(if (isRu) "Очистить виджет" else "Clear Widget", "", "x", onClearWidget) }
                    }
                    item { BodyText(L10n.sleepScreen(lang), weight = FontWeight.Bold, size = 14, muted = true, modifier = Modifier.padding(top = 12.dp, start = 6.dp)) }
                    items(SleepScreenMode.entries.filter { it != SleepScreenMode.CUSTOM || imagePickerAvailable }) { mode ->
                        val sub = when (mode) {
                            SleepScreenMode.BOOK_COVER -> recentBook?.title ?: (if (isRu) "Обложка книги не найдена" else "No book cover found")
                            SleepScreenMode.CUSTOM -> if (isRu) "Выбрать изображение" else "Pick an image"
                            SleepScreenMode.OFF -> ""
                        }
                        SelectRow(L10n.sleepMode(mode, lang), sub, settings.sleepScreenMode == mode) {
                            vm.setSleepScreenMode(mode)
                            when (mode) {
                                SleepScreenMode.BOOK_COVER -> recentBook?.coverPath?.let { cover ->
                                    scope.launch { withContext(Dispatchers.Default) { SleepScreen.applyCover(context, cover) }; vm.setLastAppliedSleepCover(cover) }
                                }
                                SleepScreenMode.CUSTOM -> pickSleepImage.launch("image/*")
                                SleepScreenMode.OFF -> {}
                            }
                        }
                    }
                    item { ToggleRow(L10n.showDate(lang), "calendar", settings.showLauncherDate) { vm.setShowLauncherDate(!settings.showLauncherDate) } }
                    item { ToggleRow(L10n.showClock(lang), "clock", settings.showLauncherClock) { vm.setShowLauncherClock(!settings.showLauncherClock) } }
                    item { ToggleRow(L10n.showBattery(lang), "battery", settings.showLauncherBattery) { vm.setShowLauncherBattery(!settings.showLauncherBattery) } }
                    item { ToggleRow(L10n.showWifi(lang), "wifi", settings.showLauncherWifi) { vm.setShowLauncherWifi(!settings.showLauncherWifi) } }
                }
                "App Search" -> {
                    items(SearchGesture.entries) { gesture -> SelectRow(L10n.searchGesture(gesture, lang), "", settings.searchGesture == gesture) { vm.setSearchGesture(gesture) } }
                    item { ToggleRow(L10n.showKeyboard(lang), "search", settings.showKeyboardAutomatically) { vm.setShowKeyboardAutomatically(!settings.showKeyboardAutomatically) } }
                    item { ToggleRow(L10n.autoOpenMatch(lang), "check", settings.autoOpenSingleResult) { vm.setAutoOpenSingleResult(!settings.autoOpenSingleResult) } }
                }
                "CrossPoint Sync" -> {
                    item { ActionRow(if (isRu) "Аккаунт и статистика чтения" else "Account and Reading Stats", if (isRu) "Вход или регистрация" else "Log in or sign up", "globe") { context.startActivity(android.content.Intent(context, com.crosspoint.launcher.SyncAccountActivity::class.java)) } }
                    item { ActionRow(if (isRu) "Загрузить недостающие обложки" else "Fetch Missing Covers", if (isRu) "Добавить обложки к книгам" else "Adds books to your synced library", "download") { if (!syncBusy) vm.fetchMissingCovers() } }
                    item {
                        val sync = remember { com.crosspoint.launcher.data.SyncRepository(context) }
                        var syncClippings by remember { mutableStateOf(sync.syncClippings) }
                        ToggleRow(if (isRu) "Синхронизировать цитаты" else "Sync Clippings", "file_text", syncClippings) { syncClippings = !syncClippings; sync.syncClippings = syncClippings }
                    }
                    item { ActionRow(if (isRu) "Отключить" else "Disconnect", if (isRu) "Сохранить скачанные обложки" else "Keep downloaded covers", "x") { if (!syncBusy) vm.disconnectSync() } }
                    if (syncStatus.isNotBlank()) item { BodyText(syncStatus, muted = true, size = 14, maxLines = 4) }
                }
                "Default Reader" -> {
                    item { SelectRow(if (isRu) "Встроенный ридер" else "CrossPoint Reader", if (isRu) "Для EPUB и FB2" else "Built-in EPUB reader", settings.defaultReaderPackage == null) { vm.setDefaultReader(null) } }
                    items(readerApps) { app -> SelectRow(app.label, app.packageName, settings.defaultReaderPackage == app.packageName) { vm.setDefaultReader(app.packageName) } }
                }
                "Home Screen Items" -> {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BodyText(L10n.launcherIcons(lang), muted = true, size = 14)
                            Row(Modifier.fillMaxWidth()) {
                                LauncherIconStyle.entries.forEach { style ->
                                    Pressable(onClick = { vm.setLauncherIconStyle(style) }, modifier = Modifier.weight(1f)) {
                                        Column(Modifier.fillMaxWidth().height(56.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                                            RadioDot(settings.launcherIconStyle == style)
                                            BodyText(L10n.iconStyle(style, lang), size = 14)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val selected = orderedHomeItems(settings.effectiveHomeMenu(opdsServers.isNotEmpty()), vm.pinnedAppInfos(settings, apps), settings)
                    val entries = selected.map { it.key }
                    val available = HomeMenuItem.entries.map { LauncherColumnItem.MenuAction(it) } + apps.map { LauncherColumnItem.PinnedApp(it) }
                    val maxItems = com.crosspoint.launcher.data.LauncherSettings.MAX_HOME_ITEMS
                    item { BodyText("${entries.size} / $maxItems " + (if (isRu) "элементов выбрано" else "Items Selected"), weight = FontWeight.Medium, size = 16) }
                    item { BodyText(if (isRu) "Выберите до $maxItems элементов на главном экране. Нажмите на значок для настройки." else "Choose up to $maxItems Home shortcuts. Tap an app icon to change it.", muted = true, size = 14, maxLines = Int.MAX_VALUE) }
                    items(selected + available.filterNot { it.key in entries }, key = { it.key }) { entry ->
                        HomeItemSettingsRow(entry, settings, entries, { vm.setHomeItems(it) }, {
                            if (entry is LauncherColumnItem.PinnedApp) editingIconFor = entry.app
                        })
                    }
                }
                "OPDS Catalogs" -> item { ActionRow(if (isRu) "Серверы OPDS" else "OPDS Servers", "${opdsServers.size} " + (if (isRu) "настроено" else "configured"), "library", onOpenOpdsServers) }
                "Library" -> {
                    val rootDisplay = if (settings.libraryFolder.isNotBlank()) settings.libraryFolder else L10n.libraryRootDefault(lang)
                    item { ActionRow(L10n.libraryRootFolder(lang), rootDisplay, "folder") { folderPickerOpen = true } }
                    item { ActionRow(if (hasStorage) L10n.storageGranted(lang) else L10n.grantStorageAccess(lang), L10n.storageAccessDesc(lang), "folder_open") { Launching.openManageStorageSettings(context) } }
                    item { ActionRow(L10n.rescanLibrary(lang), L10n.booksFound(bookCount, lang), "rotate_cw") { vm.refreshBooks() } }
                }
                "Device Settings" -> {
                    item { ActionRow(L10n.restartOnboarding(lang), L10n.restartOnboardingDesc(lang), "rotate_cw") {
                        context.startActivity(android.content.Intent(context, com.crosspoint.launcher.PermissionSetupActivity::class.java))
                    } }
                    item { ActionRow(L10n.setAsDefaultLauncher(lang), L10n.setAsDefaultLauncherDesc(lang), "smartphone") { Launching.openHomeSettings(context) } }
                    item { ToggleRow(L10n.doubleTapRefresh(lang), "rotate_cw", settings.doubleTapRefresh) { vm.setDoubleTapRefresh(!settings.doubleTapRefresh) } }
                    items(DeviceSettings.available(context)) { panel ->
                        ActionRow(panel.label, "", panel.icon) { DeviceSettings.open(context, panel) }
                    }
                }
            }
        }
    }
}

/** Interactive folder picker for choosing the root library folder. */
@Composable
private fun FolderPickerDialog(
    initialFolder: String,
    onSelectFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = CrossPointTheme.colors
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN

    val sdcard = android.os.Environment.getExternalStorageDirectory()
    val initialFile = File(initialFolder).let { if (it.exists() && it.isDirectory) it else sdcard }
    var currentDir by remember { mutableStateOf(initialFile) }

    val presets = listOf(
        File(sdcard, "Books"),
        sdcard,
        File(sdcard, "Download"),
        File(sdcard, "Documents"),
        File(sdcard, "koreader")
    ).filter { it.exists() && it.isDirectory }

    val subdirs = remember(currentDir) {
        val files = currentDir.listFiles()?.filter {
            it.isDirectory && !it.name.startsWith(".") && it.canRead()
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
        files
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .border(2.dp, colors.divider, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BodyText(L10n.chooseFolderTitle(lang), weight = FontWeight.Bold, size = 18)

            // Current folder indicator
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(8.dp)) {
                BodyText(L10n.currentFolderLabel(lang), muted = true, size = 12)
                BodyText(currentDir.absolutePath, weight = FontWeight.SemiBold, size = 14, maxLines = 2)
            }

            // Quick presets
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BodyText(L10n.presetFolders(lang), muted = true, size = 12, weight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(3).forEach { preset ->
                        Pressable(onClick = { currentDir = preset }, modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier.border(1.dp, colors.divider, RoundedCornerShape(4.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BodyText(preset.name.ifEmpty { "sdcard" }, size = 12, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

            // Subdirectories list
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                ScrollList(Modifier.fillMaxWidth()) {
                    if (currentDir.parentFile != null && currentDir.absolutePath != "/sdcard" && currentDir.absolutePath != sdcard.absolutePath) {
                        item {
                            Pressable(onClick = { currentDir.parentFile?.let { currentDir = it } }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LucideIcon("chevron_up", Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    BodyText(L10n.upFolder(lang), weight = FontWeight.Bold, size = 14)
                                }
                            }
                        }
                    }
                    items(subdirs) { dir ->
                        Pressable(onClick = { currentDir = dir }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                LucideIcon("folder", Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                BodyText(dir.name, size = 14, maxLines = 1, modifier = Modifier.weight(1f))
                                LucideIcon("chevron_right", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pressable(onClick = { onSelectFolder(currentDir.absolutePath) }, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().background(colors.onBackground).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BodyText(L10n.selectFolder(lang), weight = FontWeight.Bold, size = 14, color = colors.background)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pressable(onClick = { onSelectFolder("") }, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().border(1.dp, colors.divider, RoundedCornerShape(4.dp)).padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BodyText(L10n.resetFolderToDefault(lang), size = 13)
                        }
                    }
                    Pressable(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().border(1.dp, colors.divider, RoundedCornerShape(4.dp)).padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BodyText(if (isRu) "Отмена" else "Cancel", size = 13)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPickerScreen(
    app: AppInfo,
    selected: String,
    onSelect: (String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val colors = CrossPointTheme.colors
    val m = CrossPointTheme.metrics
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) LucideIconLibrary
        else LucideIconLibrary.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(top = topPad)) {
        ScreenHeader(if (isRu) "Выбор иконки" else "Choose Icon", onBack) {
            Pressable(onClick = onReset) {
                Box(Modifier.height(48.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    BodyText(if (isRu) "По умолчанию" else "Default", weight = FontWeight.SemiBold, size = 14)
                }
            }
        }
        BodyText(app.label, muted = true, size = 14, modifier = Modifier.padding(horizontal = m.contentSidePadding, vertical = 8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = m.contentSidePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon("search", Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.onBackground, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.onBackground),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) BodyText(if (isRu) "Поиск иконок…" else "Search icons", muted = true, size = 16)
                    inner()
                }
            )
        }
        ScrollList(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = m.contentSidePadding,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered, key = { it }) { icon ->
                Pressable(onClick = { onSelect(icon) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LucideIcon(icon, Modifier.size(28.dp))
                        Spacer(Modifier.width(16.dp))
                        BodyText(icon.replace("_", " "), weight = FontWeight.Medium, size = 16, modifier = Modifier.weight(1f))
                        if (icon == selected) LucideIcon("check", Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                BodyText(title, weight = FontWeight.SemiBold, size = 16, maxLines = Int.MAX_VALUE)
                if (subtitle.isNotBlank()) BodyText(subtitle, muted = true, size = 12, maxLines = Int.MAX_VALUE)
            }
            Spacer(Modifier.width(12.dp))
            RadioDot(selected)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, icon: String, enabled: Boolean, onToggle: () -> Unit) {
    Pressable(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon(icon, Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            BodyText(title, weight = FontWeight.Medium, size = 16, maxLines = Int.MAX_VALUE, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            CheckBox(enabled)
        }
    }
}

@Composable
private fun HomeItemSettingsRow(
    item: LauncherColumnItem,
    settings: com.crosspoint.launcher.data.LauncherSettings,
    selectedEntries: List<String>,
    onSetItems: (List<String>) -> Unit,
    onChooseIcon: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RUSSIAN
    val entry = item.key
    val index = selectedEntries.indexOf(entry)
    Column(Modifier.fillMaxWidth()) {
        Pressable(
            onClick = {
                if (index < 0 && selectedEntries.size >= com.crosspoint.launcher.data.LauncherSettings.MAX_HOME_ITEMS) {
                    android.widget.Toast.makeText(context, L10n.homeItemsLimit(lang, com.crosspoint.launcher.data.LauncherSettings.MAX_HOME_ITEMS), android.widget.Toast.LENGTH_SHORT).show()
                } else onSetItems(if (index >= 0) selectedEntries - entry else selectedEntries + entry)
            },
            modifier = Modifier.fillMaxWidth().semantics {
                role = Role.Checkbox
                stateDescription = if (index >= 0) "selected" else "not selected"
            }
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item) {
                    is LauncherColumnItem.MenuAction -> {
                        LucideIcon(item.item.icon, Modifier.size(24.dp))
                    }
                    is LauncherColumnItem.PinnedApp -> {
                        Pressable(
                            onClick = onChooseIcon,
                            modifier = Modifier.semantics {
                                role = Role.Button
                                contentDescription = "Change icon for ${item.app.label}"
                            }
                        ) {
                            LauncherAppIcon(item.app, settings, size = 32.dp)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    BodyText(item.label(lang), weight = FontWeight.Medium, size = 16, maxLines = Int.MAX_VALUE)
                }
                Spacer(Modifier.width(12.dp))
                CheckBox(index >= 0)
            }
        }
        if (index >= 0 && selectedEntries.size > 1) {
            Row(Modifier.fillMaxWidth().padding(start = 62.dp, end = 6.dp)) {
                for (direction in listOf(-1, 1)) {
                    val target = index + direction
                    Box(Modifier.weight(1f)) {
                        if (target in selectedEntries.indices) {
                            Pressable(onClick = {
                                val next = selectedEntries.toMutableList()
                                java.util.Collections.swap(next, index, target)
                                onSetItems(next)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LucideIcon(if (direction < 0) "chevron_up" else "chevron_down", Modifier.size(20.dp))
                                    BodyText(if (direction < 0) L10n.moveUp(lang) else L10n.moveDown(lang), size = 14, maxLines = Int.MAX_VALUE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionRow(title: String, subtitle: String, icon: String, onClick: () -> Unit) {
    Pressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon(icon, Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BodyText(title, weight = FontWeight.SemiBold, size = 16, maxLines = Int.MAX_VALUE)
                if (subtitle.isNotBlank()) BodyText(subtitle, muted = true, size = 12, maxLines = Int.MAX_VALUE)
            }
            Spacer(Modifier.width(12.dp))
            LucideIcon("chevron_right", Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun RadioDot(selected: Boolean) {
    val colors = CrossPointTheme.colors
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(2.dp, colors.divider, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.onBackground)
            )
        }
    }
}

@Composable
internal fun CheckBox(checked: Boolean) {
    val colors = CrossPointTheme.colors
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(if (checked) Modifier.background(colors.onBackground) else Modifier)
            .border(1.5.dp, colors.onBackground, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) LucideIcon("check", Modifier.size(14.dp), tintOverride = colors.background)
    }
}
