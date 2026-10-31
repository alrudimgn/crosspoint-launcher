package com.crosspoint.launcher.util

import androidx.compose.runtime.compositionLocalOf
import com.crosspoint.launcher.model.AppLanguage
import com.crosspoint.launcher.model.AppearanceMode
import com.crosspoint.launcher.model.CoverSlotMode
import com.crosspoint.launcher.model.HomeMenuItem
import com.crosspoint.launcher.model.LauncherIconStyle
import com.crosspoint.launcher.model.SearchGesture
import com.crosspoint.launcher.model.SleepScreenMode
import com.crosspoint.launcher.model.ThemeType

val LocalAppLanguage = compositionLocalOf { AppLanguage.RUSSIAN }

object L10n {

    fun menuItem(item: HomeMenuItem, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (item) {
            HomeMenuItem.BROWSE_BOOKS -> "Библиотека"
            HomeMenuItem.RECENT_BOOKS -> "Недавние книги"
            HomeMenuItem.OPDS_BROWSER -> "Каталоги OPDS"
            HomeMenuItem.FILE_TRANSFER -> "Передача файлов"
            HomeMenuItem.APPS -> "Все приложения"
            HomeMenuItem.SETTINGS -> "Настройки"
        }
        AppLanguage.ENGLISH -> item.label
    }

    fun theme(type: ThemeType, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (type) {
            ThemeType.LYRA -> "Lyra (одна обложка)"
            ThemeType.LYRA_3_COVERS -> "Lyra (три обложки)"
            ThemeType.CAROUSEL -> "Карусель обложек"
            ThemeType.MINIMAL -> "Минимализм (список)"
        }
        AppLanguage.ENGLISH -> type.label
    }

    fun themeDescription(type: ThemeType, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (type) {
            ThemeType.LYRA -> "Одна главная обложка и двухколоночное меню"
            ThemeType.LYRA_3_COVERS -> "Три последние обложки в ряд"
            ThemeType.CAROUSEL -> "Прокручиваемая карусель обложек"
            ThemeType.MINIMAL -> "Без обложек — минималистичный список"
        }
        AppLanguage.ENGLISH -> type.description
    }

    fun appearance(mode: AppearanceMode, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (mode) {
            AppearanceMode.LIGHT -> "Светлая"
            AppearanceMode.DARK -> "Тёмная"
            AppearanceMode.SYSTEM -> "Как в системе"
        }
        AppLanguage.ENGLISH -> mode.label
    }

    fun iconStyle(style: LauncherIconStyle, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (style) {
            LauncherIconStyle.ICONS -> "Иконки"
            LauncherIconStyle.DOTS -> "Точки"
            LauncherIconStyle.SQUARES -> "Квадраты"
            LauncherIconStyle.CIRCLES -> "Круги"
        }
        AppLanguage.ENGLISH -> style.label
    }

    fun searchGesture(gesture: SearchGesture, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (gesture) {
            SearchGesture.OFF -> "Выключено"
            SearchGesture.SWIPE_UP -> "Свайп вверх"
            SearchGesture.SWIPE_DOWN -> "Свайп вниз"
            SearchGesture.BOTH -> "Свайп вверх или вниз"
        }
        AppLanguage.ENGLISH -> gesture.label
    }

    fun coverSlot(slot: CoverSlotMode, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (slot) {
            CoverSlotMode.BOOKS -> "Книги"
            CoverSlotMode.WIDGET -> "Виджет"
        }
        AppLanguage.ENGLISH -> slot.label
    }

    fun sleepMode(mode: SleepScreenMode, lang: AppLanguage): String = when (lang) {
        AppLanguage.RUSSIAN -> when (mode) {
            SleepScreenMode.OFF -> "Выключено"
            SleepScreenMode.BOOK_COVER -> "Обложка последней книги"
            SleepScreenMode.CUSTOM -> "Пользовательское изображение"
        }
        AppLanguage.ENGLISH -> mode.label
    }

    fun noOpenBook(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Нет открытых книг" else "No open book"
    fun startReading(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Начать чтение" else "Start reading"
    fun recentBooks(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Недавние книги" else "Recent books"
    fun recentReading(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Недавнее чтение" else "Recent reading"
    fun pinAppsHint(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Закрепите приложения из меню" else "Pin apps from the drawer"
    fun grantStorage(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Разрешить доступ к памяти" else "Grant storage access"
    fun storageHint(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Разрешить доступ к файлам для чтения библиотеки" else "Allow file access to scan your library"
    fun setAsDefaultHome(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Сделать CrossPoint основным лаунчером" else "Set CrossPoint as your home app"
    fun setAsDefaultDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Нажмите «Выбрать», чтобы сделать лаунчером по умолчанию" else "Tap Set to make it your default launcher"
    fun setButton(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Выбрать" else "Set"

    // Settings
    fun settings(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Настройки" else "Settings"
    fun languageCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Язык / Language" else "Language"
    fun homeThemeCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Тема экрана" else "Home Theme"
    fun appearanceCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Внешний вид" else "Appearance"
    fun appSearchCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Поиск приложений" else "App Search"
    fun syncCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Синхронизация" else "CrossPoint Sync"
    fun defaultReaderCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Читалка по умолчанию" else "Default Reader"
    fun homeItemsCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Элементы рабочего стола" else "Home Screen Items"
    fun opdsCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Каталоги OPDS" else "OPDS Catalogs"
    fun libraryCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Библиотека" else "Library"
    fun deviceSettingsCategory(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Настройки устройства" else "Device Settings"
    fun appStoreCategory(installed: Boolean, lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) {
        if (installed) "Магазин приложений" else "Установить магазин приложений"
    } else {
        if (installed) "App Store" else "Get App Store"
    }

    // Library category
    fun storageGranted(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Доступ к памяти предоставлен" else "Storage Access Granted"
    fun grantStorageAccess(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Предоставить доступ к памяти" else "Grant Storage Access"
    fun storageAccessDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Чтение книг (EPUB, FB2, PDF, DJVU)" else "Read local books (EPUB, FB2, PDF, DJVU)"
    fun rescanLibrary(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Пересканировать библиотеку" else "Rescan Library"
    fun booksFound(count: Int, lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "$count книг найдено" else "$count books found"
    fun libraryRootFolder(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Корневая папка библиотеки" else "Root Library Folder"
    fun libraryRootDefault(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "По умолчанию (/sdcard/Books, ...)" else "Default (/sdcard/Books, ...)"
    fun selectFolder(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Выбрать эту папку" else "Select this folder"
    fun resetFolderToDefault(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Сбросить по умолчанию" else "Reset to default"
    fun folderSelectedToast(folder: String, lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Папка библиотеки: $folder" else "Library folder: $folder"
    fun currentFolderLabel(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Текущая папка: " else "Current folder: "
    fun chooseFolderTitle(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Выбор папки библиотеки" else "Select Library Folder"
    fun upFolder(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) ".. (Наверх)" else ".. (Up)"
    fun presetFolders(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Быстрый выбор:" else "Presets:"

    // Home items
    fun homeItemsLimit(lang: AppLanguage, max: Int) = if (lang == AppLanguage.RUSSIAN) "На главном экране может быть до $max элементов" else "Home can show up to $max items"
    fun moveUp(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Выше" else "Move Up"
    fun moveDown(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Ниже" else "Move Down"

    // Updates
    fun updatesDisabled(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Пользовательская сборка (обновления отключены)" else "Custom build (updates disabled)"

    // Books screen
    fun library(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Библиотека" else "Library"
    fun scanning(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Сканирование…" else "Scanning…"
    fun noBooksFound(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Книги не найдены" else "No books found"
    fun deleteBook(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Удалить книгу" else "Delete Book"
    fun openWith(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Открыть с помощью…" else "Open with…"

    // App drawer
    fun allApps(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Все приложения" else "All Apps"
    fun searchApps(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Поиск приложений…" else "Search apps…"
    fun pinToHome(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Закрепить на рабочем столе" else "Pin to Home"
    fun unpinFromHome(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Открепить от рабочего стола" else "Unpin from Home"
    fun appInfo(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "О приложении" else "App Info"
    fun uninstall(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Удалить" else "Uninstall"

    // Device settings
    fun restartOnboarding(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Перезапустить настройку" else "Restart Onboarding"
    fun restartOnboardingDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Повторить настройку памяти, Wi-Fi и лаунчера" else "Re-run storage, Wi-Fi and launcher setup"
    fun setAsDefaultLauncher(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Сделать лаунчером по умолчанию" else "Set as Default Launcher"
    fun setAsDefaultLauncherDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Открыть системные настройки главного экрана" else "Open system home settings"
    fun doubleTapRefresh(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Двойное касание шапки для обновления e-ink" else "Double-Tap Header to Refresh"

    // Search settings
    fun autoOpenMatch(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Открывать единственное совпадение" else "Auto-Open Single Match"
    fun autoOpenMatchDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Запускать приложение сразу, если найден один результат" else "Launch automatically if only one app matches"
    fun showKeyboard(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Показывать клавиатуру сразу" else "Show Keyboard on Open"
    fun showKeyboardDesc(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Фокусировать строку поиска при открытии" else "Focus the search field immediately"

    // Appearance
    fun launcherIcons(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Стиль значков приложений" else "Launcher Icon Style"
    fun hideStatusBar(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Скрыть строку состояния" else "Hide Status Bar"
    fun showDate(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Показывать дату" else "Show Date"
    fun showClock(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Показывать часы" else "Show Clock"
    fun showBattery(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Показывать заряд батареи" else "Show Battery"
    fun showWifi(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Показывать Wi-Fi" else "Show Wi-Fi"
    fun sleepScreen(lang: AppLanguage) = if (lang == AppLanguage.RUSSIAN) "Экран блокировки (Sleep Screen)" else "Sleep Screen"
}
