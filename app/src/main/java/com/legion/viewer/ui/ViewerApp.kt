@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.legion.viewer.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.legion.viewer.model.AppTheme
import com.legion.viewer.model.AppSettings
import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.MediaCategory
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.PlaybackState
import com.legion.viewer.model.ScanResult
import com.legion.viewer.ViewerApplication
import com.legion.viewer.R
import com.legion.viewer.data.MediaNaturalComparator

private data class NavItem(val title: String, val icon: ImageVector, val target: ViewerScreen)

@Composable
fun ViewerApp(model: ViewerViewModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val screen by model.screen.collectAsStateWithLifecycle()
    val scans by model.scans.collectAsStateWithLifecycle()
    val playback by model.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var lastHomeBackPress by remember { mutableLongStateOf(0L) }
    var pendingCategory by remember { mutableStateOf<MediaCategory?>(null) }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val category = pendingCategory
        if (uri != null && category != null) model.selectDirectory(category, uri)
        pendingCategory = null
    }
    val chooseDirectory: (MediaCategory) -> Unit = { category ->
        pendingCategory = category
        directoryPicker.launch(settings.sources[category]?.treeUri)
    }

    LaunchedEffect(screen) { lastHomeBackPress = 0L }
    BackHandler(enabled = screen != ViewerScreen.Player) {
        if (screen == ViewerScreen.Home) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHomeBackPress <= 2_000L) {
                activity?.finish()
            } else {
                lastHomeBackPress = now
                Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
            }
        } else {
            model.navigateUp()
        }
    }

    when (val current = screen) {
        ViewerScreen.Player -> PlayerScreen(model, onBack = model::navigateUp)
        is ViewerScreen.TextReader -> TextReaderScreen(model, current.item, model::navigateUp)
        is ViewerScreen.ComicReader -> ComicReaderScreen(model, current.work, model::navigateUp)
        else -> {
            val navigation = listOf(
                NavItem("主页", ViewerIcons.Home, ViewerScreen.Home),
                NavItem("视频", ViewerIcons.Video, ViewerScreen.Category(MediaCategory.Video)),
                NavItem("音乐", ViewerIcons.Music, ViewerScreen.Category(MediaCategory.Music)),
                NavItem("文本", ViewerIcons.Text, ViewerScreen.Category(MediaCategory.Text)),
                NavItem("漫画", ViewerIcons.Comics, ViewerScreen.Category(MediaCategory.Comics)),
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 700.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide) ViewerRail(navigation, screen, model::navigateTopLevel)
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        topBar = {
                            ViewerTopBar(
                                screen = screen,
                                onBack = if (screen == ViewerScreen.Home) null else model::navigateUp,
                                    onSettings = { model.navigate(ViewerScreen.Settings) },
                                onRefresh = (screen as? ViewerScreen.Category)?.let { { model.refresh(it.category) } },
                            )
                        },
                        bottomBar = {
                            if (!wide) {
                                Column {
                                    if (playback.current?.category == MediaCategory.Music) MiniPlayer(model)
                                    ViewerBottomBar(navigation, screen, model::navigateTopLevel)
                                }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            when (screen) {
                                ViewerScreen.Home -> HomeScreen(settings, chooseDirectory) { model.navigate(it) }
                                ViewerScreen.Settings -> SettingsScreen(
                                    settings = settings,
                                    onChoose = chooseDirectory,
                                    onClear = model::clearDirectory,
                                    onTheme = model::toggleTheme,
                                )
                                is ViewerScreen.Category -> {
                                    val categoryScreen = screen as ViewerScreen.Category
                                    CategoryScreen(
                                        category = categoryScreen.category,
                                        result = scans.getValue(categoryScreen.category),
                                        onChoose = { chooseDirectory(categoryScreen.category) },
                                        onRefresh = { model.refresh(categoryScreen.category) },
                                        onMedia = model::openMedia,
                                        onComic = model::openComic,
                                    )
                                }
                                else -> Unit
                            }
                            if (wide && playback.current?.category == MediaCategory.Music) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { MiniPlayer(model) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    screen: ViewerScreen,
    onBack: (() -> Unit)?,
    onSettings: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    val title = when (screen) {
        ViewerScreen.Home -> "Viewer"
        ViewerScreen.Settings -> "目录与外观"
        is ViewerScreen.Category -> screen.category.title
        else -> "Viewer"
    }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (screen == ViewerScreen.Home) {
                    Image(
                        painter = painterResource(R.drawable.viewer_brand_safe),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (screen == ViewerScreen.Home) Text("本地媒体中心", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(ViewerIcons.Back, "返回") } },
        actions = {
            if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(ViewerIcons.Refresh, "刷新") }
            if (screen != ViewerScreen.Settings) IconButton(onClick = onSettings) { Icon(ViewerIcons.Settings, "设置") }
        },
    )
}

@Composable
private fun ViewerBottomBar(items: List<NavItem>, screen: ViewerScreen, navigate: (ViewerScreen) -> Unit) {
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = screen == item.target,
                onClick = { navigate(item.target) },
                icon = { Icon(item.icon, item.title) },
                label = { Text(item.title) },
            )
        }
    }
}

@Composable
private fun ViewerRail(items: List<NavItem>, screen: ViewerScreen, navigate: (ViewerScreen) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight()) {
        Spacer(Modifier.height(24.dp))
        items.forEach { item ->
            NavigationRailItem(
                selected = screen == item.target,
                onClick = { navigate(item.target) },
                icon = { Icon(item.icon, item.title) },
                label = { Text(item.title) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    settings: AppSettings,
    onChoose: (MediaCategory) -> Unit,
    navigate: (ViewerScreen) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("你的本地媒体", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "只呈现你亲自选择的目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(MediaCategory.entries) { category ->
            val source = settings.sources.getValue(category)
            Card(
                modifier = Modifier.fillMaxWidth().height(166.dp).clickable {
                    if (source.enabled) navigate(ViewerScreen.Category(category)) else onChoose(category)
                },
                border = CardDefaults.outlinedCardBorder(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(category.icon(), null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (source.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                if (source.enabled) "已连接" else "未配置",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        source.displayName.ifBlank { "选择一个本地目录" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (source.enabled) "浏览" else "选择目录",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(ViewerIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onChoose: (MediaCategory) -> Unit,
    onClear: (MediaCategory) -> Unit,
    onTheme: () -> Unit,
) {
    var pendingClear by remember { mutableStateOf<MediaCategory?>(null) }
    pendingClear?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("取消${category.title}目录？") },
            text = { Text("只会移除 Viewer 的目录入口，不会删除手机中的任何文件。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear(category)
                    pendingClear = null
                }) { Text("取消目录") }
            },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text("返回") } },
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("外观", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        item {
            Card {
                ListItem(
                    headlineContent = { Text("深色主题", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("立即切换并在下次启动时恢复") },
                    leadingContent = { Icon(if (settings.appTheme == AppTheme.Dark) ViewerIcons.DarkTheme else ViewerIcons.LightTheme, null) },
                    trailingContent = { Switch(checked = settings.appTheme == AppTheme.Dark, onCheckedChange = { onTheme() }) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
        item {
            Text(
                "媒体目录",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        items(MediaCategory.entries) { category ->
            val source = settings.sources.getValue(category)
            Card(border = CardDefaults.outlinedCardBorder()) {
                ListItem(
                    headlineContent = { Text(category.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(source.displayName.ifBlank { "未配置" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(category.icon(), null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onChoose(category) }) { Icon(ViewerIcons.Folder, "选择目录") }
                            if (source.enabled) IconButton(onClick = { pendingClear = category }) { Icon(ViewerIcons.Delete, "取消目录") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(ViewerIcons.Info, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "目录可以相同或互相嵌套，每个分类只读取自己的格式。Android 11 以上不能选择存储根目录、Download 根目录及 Android/data。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryScreen(
    category: MediaCategory,
    result: ScanResult,
    onChoose: () -> Unit,
    onRefresh: () -> Unit,
    onMedia: (MediaItem) -> Unit,
    onComic: (ComicWork) -> Unit,
) {
    when (result) {
        ScanResult.NotConfigured -> EmptyState(ViewerIcons.Folder, "尚未配置${category.title}目录", "只会读取你选择的目录。", "选择目录", onChoose)
        is ScanResult.Loading -> {
            val previous = result.previous
            if (previous == null) {
                EmptyState(ViewerIcons.Refresh, "正在读取目录", "已检查 ${result.progress.checkedFiles} 个文件", null, null, loading = true)
            } else {
                Box(Modifier.fillMaxSize()) {
                    CategoryContent(category, previous, onMedia, onComic)
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Column {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                "正在刷新 · 已检查 ${result.progress.checkedFiles} 个文件",
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
        is ScanResult.Failure -> EmptyState(ViewerIcons.BrokenImage, "目录读取失败", result.message, "重新选择", onChoose)
        is ScanResult.Success -> if (result.items.isEmpty()) {
            EmptyState(category.icon(), "这里还没有${category.title}内容", "已忽略 ${result.ignoredCount} 个不属于本分类的文件。", "刷新", onRefresh)
        } else CategoryContent(category, result, onMedia, onComic)
    }
}

@Composable
private fun CategoryContent(
    category: MediaCategory,
    result: ScanResult.Success,
    onMedia: (MediaItem) -> Unit,
    onComic: (ComicWork) -> Unit,
) {
    when (category) {
        MediaCategory.Comics -> {
            val works = remember(result.items) {
                result.items.groupBy { it.group }.map { (group, pages) -> ComicWork(group.substringAfterLast('/'), group, pages) }
            }
            ComicGrid(works, result.ignoredCount, result.skippedDirectories, onComic)
        }
        MediaCategory.Video -> MediaGrid(result.items, result.ignoredCount, result.skippedDirectories, onMedia)
        MediaCategory.Music, MediaCategory.Text -> MediaList(result.items, onMedia, result.ignoredCount, result.skippedDirectories)
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    action: String?,
    onAction: (() -> Unit)?,
    loading: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator() else Icon(icon, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MediaList(items: List<MediaItem>, onMedia: (MediaItem) -> Unit, ignored: Int, skipped: Int) {
    var limit by remember(items) { mutableIntStateOf(minOf(60, items.size)) }
    val groups = remember(items) { orderedMediaGroups(items) }
    val expandedGroups = remember(items) {
        mutableStateMapOf<String, Boolean>().apply {
            groups.forEach { (name, _) -> this[name] = true }
        }
    }
    val expandedItemCount = groups.sumOf { (name, groupItems) ->
        if (expandedGroups[name] != false) groupItems.size else 0
    }
    var remainingItems = limit
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (ignored > 0 || skipped > 0) item {
            ScanSummary(ignored, skipped)
        }
        groups.forEach { (group, groupItems) ->
            item(key = "media-group-$group") {
                DirectoryGroupHeader(
                    name = group,
                    count = groupItems.size,
                    expanded = expandedGroups[group] != false,
                    onToggle = { expandedGroups[group] = expandedGroups[group] == false },
                )
            }

            if (expandedGroups[group] != false && remainingItems > 0) {
                val visibleItems = groupItems.take(remainingItems)
                remainingItems -= visibleItems.size
                items(visibleItems, key = { it.uri.toString() }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onMedia(item) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                    item.relativePath,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(item.category.icon(), null, Modifier.padding(9.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = {
                                Icon(
                                    if (item.category == MediaCategory.Music) ViewerIcons.Play else ViewerIcons.ChevronRight,
                                    "打开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        if (limit < expandedItemCount) item {
            LaunchedEffect(limit, expandedItemCount) { limit = minOf(expandedItemCount, limit + 60) }
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, ignored: Int, skipped: Int, onMedia: (MediaItem) -> Unit) {
    var limit by remember(items) { mutableIntStateOf(minOf(60, items.size)) }
    val groups = remember(items) { orderedMediaGroups(items) }
    val expandedGroups = remember(items) {
        mutableStateMapOf<String, Boolean>().apply {
            groups.forEach { (name, _) -> this[name] = true }
        }
    }
    val expandedItemCount = groups.sumOf { (name, groupItems) ->
        if (expandedGroups[name] != false) groupItems.size else 0
    }
    var remainingItems = limit
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (ignored > 0 || skipped > 0) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ScanSummary(ignored, skipped)
        }

        groups.forEach { (groupName, groupItems) ->
            item(
                key = "video-group-$groupName",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                DirectoryGroupHeader(
                    name = groupName,
                    count = groupItems.size,
                    expanded = expandedGroups[groupName] != false,
                    onToggle = { expandedGroups[groupName] = expandedGroups[groupName] == false },
                )
            }

            if (expandedGroups[groupName] != false && remainingItems > 0) {
                val visibleItems = groupItems.take(remainingItems)
                remainingItems -= visibleItems.size
                items(visibleItems, key = { it.uri.toString() }) { item ->
                    Card(Modifier.clickable { onMedia(item) }) {
                        VideoThumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Text(
                            item.title,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (limit < expandedItemCount) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            LaunchedEffect(limit, expandedItemCount) { limit = minOf(expandedItemCount, limit + 60) }
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
    }
}

private fun orderedMediaGroups(items: List<MediaItem>): List<Pair<String, List<MediaItem>>> =
    items.groupBy(MediaItem::group)
        .toList()
        .sortedWith { left, right ->
            when {
                left.first == "根目录" && right.first != "根目录" -> -1
                left.first != "根目录" && right.first == "根目录" -> 1
                else -> MediaNaturalComparator.natural(left.first, right.first)
            }
        }

@Composable
private fun DirectoryGroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) ViewerIcons.ChevronDown else ViewerIcons.ChevronRight,
                contentDescription = if (expanded) "折叠" else "展开",
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "$name · $count",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ComicGrid(works: List<ComicWork>, ignored: Int, skipped: Int, onComic: (ComicWork) -> Unit) {
    var limit by remember(works) { mutableIntStateOf(minOf(60, works.size)) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ignored > 0 || skipped > 0) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ScanSummary(ignored, skipped)
        }
        items(works.take(limit), key = { it.relativePath }) { work ->
            Card(Modifier.clickable { onComic(work) }) {
                AsyncImage(
                    model = work.cover.uri,
                    contentDescription = work.name,
                    modifier = Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(12.dp)) {
                    Text(work.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text("${work.pages.size} 页 · ${work.relativePath}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (limit < works.size) item { LaunchedEffect(limit) { limit = minOf(works.size, limit + 60) } }
    }
}

@Composable
private fun ScanSummary(ignored: Int, skipped: Int) {
    val message = buildString {
        if (ignored > 0) append("已过滤 $ignored 个其他格式文件")
        if (skipped > 0) {
            if (isNotEmpty()) append(" · ")
            append("$skipped 个目录无法读取")
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ViewerIcons.Info, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(item.uri) {
        mutableStateOf<com.legion.viewer.data.VideoThumbnailData?>(null)
    }
    LaunchedEffect(item.uri, item.modifiedAt) {
        val repository = (context.applicationContext as ViewerApplication).container.thumbnails
        thumbnail = repository.load(item, 480, 270)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val data = thumbnail
        if (data == null) {
            Icon(ViewerIcons.Video, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.Image(
                data.bitmap.asImageBitmap(),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (data.durationMs > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        formatVideoDuration(data.durationMs),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun MiniPlayer(model: ViewerViewModel) {
    val snapshot by model.playback.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { model.navigate(ViewerScreen.Player) },
        tonalElevation = 5.dp,
        shadowElevation = 5.dp,
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(ViewerIcons.Music, null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.current?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (snapshot.state == PlaybackState.Playing) "正在播放" else "已暂停",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { model.container.playback.playPause() }) {
                    Icon(if (snapshot.state == PlaybackState.Playing) ViewerIcons.Pause else ViewerIcons.Play, "播放或暂停")
                }
            }
            LinearProgressIndicator(
                progress = { if (snapshot.durationMs > 0) (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            }
    }
}

private fun MediaCategory.icon(): ImageVector = when (this) {
    MediaCategory.Video -> ViewerIcons.Video
    MediaCategory.Music -> ViewerIcons.Music
    MediaCategory.Text -> ViewerIcons.Text
    MediaCategory.Comics -> ViewerIcons.Comics
}
