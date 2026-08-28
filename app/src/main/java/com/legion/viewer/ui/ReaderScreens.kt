@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)

package com.legion.viewer.ui

import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.htmlEncode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import com.legion.viewer.model.ComicWork
import com.legion.viewer.model.MediaItem
import com.legion.viewer.model.ReaderAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import kotlin.math.abs

@Composable
fun TextReaderScreen(model: ViewerViewModel, item: MediaItem, onBack: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var content by remember(item.uri) { mutableStateOf<String?>(null) }
    var renderedHtml by remember(item.uri) { mutableStateOf<String?>(null) }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentRatio = remember(item.uri) { mutableFloatStateOf(0f) }
    var restoredRatio by remember { mutableFloatStateOf(0f) }
    var progressReady by remember(item.uri) { mutableStateOf(false) }
    var controlsVisible by remember(item.uri) { mutableStateOf(true) }
    val appearance = settings.reader

    LaunchedEffect(item.uri) {
        val saved = model.container.progress.get(item.category, item.uri)
        restoredRatio = saved?.scrollRatio ?: 0f
        currentRatio.floatValue = restoredRatio
        runCatching { readText(model, item) }
            .onSuccess {
                content = it
                progressReady = true
            }
            .onFailure { error = it.message ?: "无法读取文本" }
    }

    LaunchedEffect(item.uri, progressReady) {
        if (!progressReady) return@LaunchedEffect
        snapshotFlow { currentRatio.floatValue }
            .debounce(750)
            .distinctUntilChanged()
            .collectLatest { model.saveTextProgress(item, it) }
    }

    DisposableEffect(item.uri, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                model.saveTextProgress(item, currentRatio.floatValue)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
            model.saveTextProgress(item, currentRatio.floatValue)
        }
    }

    LaunchedEffect(item.uri, content, appearance) {
        renderedHtml = if (item.extension == "md") {
            content?.let { text ->
                withContext(Dispatchers.Default) { readerHtml(item, text, appearance) }
            }
        } else {
            null
        }
    }

    LaunchedEffect(webView, renderedHtml) {
        val view = webView
        val html = renderedHtml
        if (view != null && html != null) {
            restoredRatio = currentRatio.floatValue.takeIf { it > 0f } ?: restoredRatio
            view.setBackgroundColor(if (appearance.dark) AndroidColor.rgb(14, 17, 22) else AndroidColor.rgb(250, 248, 242))
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(ViewerIcons.Back, "返回") } },
                actions = {
                    IconButton(onClick = { controlsVisible = !controlsVisible }) {
                        Icon(ViewerIcons.Tune, if (controlsVisible) "隐藏阅读设置" else "显示阅读设置")
                    }
                    IconButton(onClick = { model.setReader(appearance.copy(dark = !appearance.dark)) }) {
                        Icon(if (appearance.dark) ViewerIcons.LightTheme else ViewerIcons.DarkTheme, "阅读主题")
                    }
                },
            )
        },
        bottomBar = {
            if (controlsVisible) ReaderControls(appearance) { model.setReader(it) }
        },
    ) { padding ->
        val text = content
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            text == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("正在打开文本…") }
            item.extension == "md" -> Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            setBackgroundColor(if (appearance.dark) AndroidColor.rgb(14, 17, 22) else AndroidColor.rgb(250, 248, 242))
                            this.settings.javaScriptEnabled = false
                            this.settings.allowFileAccess = false
                            this.settings.allowContentAccess = false
                            this.settings.blockNetworkLoads = true
                            this.settings.domStorageEnabled = false
                            isHorizontalScrollBarEnabled = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                override fun onPageFinished(view: WebView, url: String?) {
                                    view.post {
                                        val range = (view.contentHeight * pageScale(view) - view.height).coerceAtLeast(0f)
                                        view.scrollTo(0, (range * restoredRatio).toInt())
                                    }
                                }
                            }
                            setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                val target = view as WebView
                                val range = (target.contentHeight * pageScale(target) - target.height).coerceAtLeast(1f)
                                currentRatio.floatValue = (scrollY / range).coerceIn(0f, 1f)
                            }
                        }
                    },
                    update = { },
                    modifier = Modifier.fillMaxSize(),
                )
                ReaderScrollbar(
                    ratio = currentRatio.floatValue,
                    onSeek = { ratio ->
                        currentRatio.floatValue = ratio
                        webView?.let { view ->
                            val range = (view.contentHeight * pageScale(view) - view.height).coerceAtLeast(0f)
                            view.scrollTo(0, (range * ratio).toInt())
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
            else -> PlainTextContent(
                content = text,
                appearance = appearance,
                restoredRatio = restoredRatio,
                onRatioChange = { currentRatio.floatValue = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PlainTextContent(
    content: String,
    appearance: ReaderAppearance,
    restoredRatio: Float,
    onRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chunks = remember(content) { chunkPlainText(content) }
    val listState = rememberLazyListState()
    var restored by remember(content) { mutableStateOf(false) }
    var scrollRatio by remember(content) { mutableFloatStateOf(restoredRatio) }
    var requestedRatio by remember(content) { mutableFloatStateOf(-1f) }
    val background = if (appearance.dark) Color(0xFF0E1116) else Color(0xFFFAF8F2)
    val foreground = if (appearance.dark) Color(0xFFE8EAF0) else Color(0xFF20242C)

    LaunchedEffect(chunks, restoredRatio) {
        if (!restored && chunks.isNotEmpty()) {
            val rawPosition = restoredRatio.coerceIn(0f, 1f) * chunks.size
            val index = rawPosition.toInt().coerceIn(chunks.indices)
            listState.scrollToItem(index)
            val fraction = (rawPosition - index).coerceIn(0f, 1f)
            if (fraction > 0f) {
                val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                if (itemSize > 0) listState.scrollToItem(index, (itemSize * fraction).toInt())
            }
            scrollRatio = restoredRatio.coerceIn(0f, 1f)
            restored = true
        }
    }
    LaunchedEffect(requestedRatio) {
        if (requestedRatio < 0f || chunks.isEmpty()) return@LaunchedEffect
        val rawPosition = requestedRatio.coerceIn(0f, 1f) * chunks.size
        val index = rawPosition.toInt().coerceIn(chunks.indices)
        val fraction = (rawPosition - index).coerceIn(0f, 1f)
        listState.scrollToItem(index)
        val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
        if (itemSize > 0 && fraction > 0f) {
            listState.scrollToItem(index, (itemSize * fraction).toInt())
        }
    }
    LaunchedEffect(listState, chunks.size, restored) {
        if (!restored || chunks.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val withinItem = if (itemInfo == null || itemInfo.size <= 0) {
                0f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / itemInfo.size).coerceIn(0f, 1f)
            }
            ((index + withinItem) / chunks.size).coerceIn(0f, 1f)
        }
            .distinctUntilChanged()
            .collectLatest {
                scrollRatio = it
                onRatioChange(it)
            }
    }

    Box(modifier.background(background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(chunks, key = { it.start }) { chunk ->
                Text(
                    text = content.substring(chunk.start, chunk.end),
                    color = foreground,
                    fontSize = appearance.fontSize.sp,
                    lineHeight = (appearance.fontSize * appearance.lineHeight).sp,
                    modifier = Modifier.fillMaxWidth(appearance.contentWidth),
                )
            }
            item { Spacer(Modifier.padding(bottom = 84.dp)) }
        }
        ReaderScrollbar(
            ratio = scrollRatio,
            onSeek = {
                scrollRatio = it
                requestedRatio = it
                onRatioChange(it)
            },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun ReaderScrollbar(
    ratio: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var trackHeight by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var emphasized by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { READER_SCROLLBAR_THUMB_HEIGHT.toPx() }
    val thumbWidth by animateDpAsState(
        targetValue = if (emphasized) 10.dp else 4.dp,
        animationSpec = tween(150),
        label = "reader-scrollbar-width",
    )
    val scrollbarColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(interactionVersion, dragging) {
        if (emphasized && !dragging) {
            delay(3_000)
            emphasized = false
        }
    }

    Box(
        modifier
            .width(28.dp)
            .onSizeChanged { trackHeight = it.height.coerceAtLeast(1) }
            .pointerInput(trackHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    emphasized = true

                    fun seekAt(y: Float) {
                        val available = (trackHeight - thumbHeightPx).coerceAtLeast(1f)
                        onSeek(((y - thumbHeightPx / 2f) / available).coerceIn(0f, 1f))
                    }

                    seekAt(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        seekAt(change.position.y)
                        change.consume()
                    }
                    dragging = false
                    interactionVersion += 1
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(2.dp)
                .background(scrollbarColor.copy(alpha = .18f), RoundedCornerShape(99.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationY = ratio.coerceIn(0f, 1f) * (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                }
                .width(thumbWidth)
                .height(READER_SCROLLBAR_THUMB_HEIGHT)
                .background(scrollbarColor.copy(alpha = if (emphasized) .95f else .68f), RoundedCornerShape(99.dp)),
        )
    }
}

private data class TextChunk(val start: Int, val end: Int)

private fun chunkPlainText(content: String): List<TextChunk> {
    if (content.isEmpty()) return listOf(TextChunk(0, 0))
    val chunks = ArrayList<TextChunk>((content.length / PLAIN_TEXT_CHUNK_SIZE) + 1)
    var start = 0
    while (start < content.length) {
        val targetEnd = (start + PLAIN_TEXT_CHUNK_SIZE).coerceAtMost(content.length)
        var end = targetEnd
        if (targetEnd < content.length) {
            val lineEnd = content.lastIndexOf('\n', targetEnd - 1)
            if (lineEnd >= start + PLAIN_TEXT_CHUNK_SIZE / 2) end = lineEnd + 1
        }
        chunks += TextChunk(start, end)
        start = end
    }
    return chunks
}

@Composable
private fun ReaderControls(appearance: ReaderAppearance, onChange: (ReaderAppearance) -> Unit) {
    var draft by remember { mutableStateOf(appearance) }
    LaunchedEffect(appearance) { draft = appearance }
    Surface(shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                ViewerSlider(
                    value = draft.fontSize,
                    onValueChange = { draft = draft.copy(fontSize = it) },
                    onValueChangeFinished = { onChange(draft) },
                    valueRange = 14f..32f,
                    modifier = Modifier.weight(1f),
                )
                Text("${draft.fontSize.toInt()}sp")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("行距")
                ViewerSlider(
                    value = draft.lineHeight,
                    onValueChange = { draft = draft.copy(lineHeight = it) },
                    onValueChangeFinished = { onChange(draft) },
                    valueRange = 1.2f..2.6f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text("宽度")
                ViewerSlider(
                    value = draft.contentWidth,
                    onValueChange = { draft = draft.copy(contentWidth = it) },
                    onValueChangeFinished = { onChange(draft) },
                    valueRange = .55f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ComicReaderScreen(model: ViewerViewModel, work: ComicWork, onBack: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val targetWidthPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val currentPage by remember(listState, work.pages.size) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .minByOrNull { info -> abs(info.offset + info.size / 2 - viewportCenter) }
                ?.index
                ?.coerceIn(work.pages.indices)
                ?: 0
        }
    }
    var restored by remember(work.relativePath) { mutableStateOf(false) }
    var comicWidthDraft by remember(work.relativePath) { mutableFloatStateOf(settings.comicWidth) }
    var controlsVisible by remember(work.relativePath) { mutableStateOf(true) }

    LaunchedEffect(settings.comicWidth) { comicWidthDraft = settings.comicWidth }

    LaunchedEffect(work.relativePath) {
        val saved = model.container.progress.get(work.cover.category, work.cover.uri)
        if (saved != null && saved.pageIndex in work.pages.indices) {
            listState.scrollToItem(saved.pageIndex, saved.pageOffset)
        }
        restored = true
    }
    LaunchedEffect(listState, work.relativePath, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(500)
            .collectLatest { (index, offset) ->
                model.container.progress.saveComic(work.cover, index.coerceIn(work.pages.indices), offset)
            }
    }
    LaunchedEffect(listState, work.relativePath, targetWidthPx) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                emptyList()
            } else {
                val first = (visible.first().index - 1).coerceAtLeast(0)
                val last = (visible.last().index + COMIC_PREFETCH_AHEAD)
                    .coerceAtMost(work.pages.lastIndex)
                (first..last).filter { index -> visible.none { it.index == index } }
            }
        }
            .distinctUntilChanged()
            .collectLatest { indexes ->
                val requests = indexes.map { index ->
                    imageLoader.enqueue(comicImageRequest(context, work.pages[index], targetWidthPx))
                }
                try {
                    awaitCancellation()
                } finally {
                    requests.forEach { it.dispose() }
                }
            }
    }
    DisposableEffect(work.relativePath) {
        onDispose {
            scope.launch {
                model.container.progress.saveComic(
                    work.cover,
                    listState.firstVisibleItemIndex.coerceIn(work.pages.indices),
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF080A0E),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(work.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${currentPage + 1} / ${work.pages.size}", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(ViewerIcons.Back, "返回") } },
                actions = {
                    IconButton(onClick = { controlsVisible = !controlsVisible }) {
                        Icon(ViewerIcons.Tune, if (controlsVisible) "隐藏漫画设置" else "显示漫画设置")
                    }
                },
            )
        },
        bottomBar = {
            if (controlsVisible) {
                Surface(shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("宽度")
                        ViewerSlider(
                            value = comicWidthDraft,
                            onValueChange = { comicWidthDraft = it },
                            onValueChangeFinished = { model.setComicWidth(comicWidthDraft) },
                            valueRange = .6f..1f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("${(comicWidthDraft * 100).toInt()}%")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF080A0E)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            work.pages.forEachIndexed { index, page ->
                item(key = page.uri.toString()) {
                    var pageAspectRatio by remember(page.uri) { mutableFloatStateOf(DEFAULT_COMIC_PAGE_RATIO) }
                    val pageRequest = remember(page.uri, targetWidthPx) {
                        comicImageRequest(context, page, targetWidthPx)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = pageRequest,
                            contentDescription = "第 ${index + 1} 页",
                            modifier = Modifier
                                .fillMaxWidth(comicWidthDraft)
                                .aspectRatio(pageAspectRatio),
                            contentScale = ContentScale.FillWidth,
                            onSuccess = { state ->
                                val width = state.result.image.width
                                val height = state.result.image.height
                                if (width > 0 && height > 0) {
                                    pageAspectRatio = width.toFloat() / height.toFloat()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun comicImageRequest(context: android.content.Context, page: MediaItem, targetWidthPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(page.uri)
        .size(Dimension.Pixels(targetWidthPx), Dimension.Undefined)
        .precision(Precision.INEXACT)
        .build()

private const val COMIC_PREFETCH_AHEAD = 4
private const val DEFAULT_COMIC_PAGE_RATIO = 0.70f
private const val PLAIN_TEXT_CHUNK_SIZE = 4_096
private val READER_SCROLLBAR_THUMB_HEIGHT = 48.dp

private suspend fun readText(model: ViewerViewModel, item: MediaItem): String = withContext(Dispatchers.IO) {
    val bytes = model.getApplication<android.app.Application>().contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
        ?: error("无法打开文本文件")
    decodeText(bytes)
}

@Suppress("DEPRECATION")
private fun pageScale(webView: WebView): Float = webView.scale

private fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("GB18030"))
    }
}

private fun readerHtml(item: MediaItem, content: String, appearance: ReaderAppearance): String {
    val body = if (item.extension == "md") {
        val node = Parser.builder().build().parse(content)
        HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build().render(node)
    } else {
        "<div class=plain>${content.htmlEncode()}</div>"
    }
    val background = if (appearance.dark) "#0e1116" else "#faf8f2"
    val foreground = if (appearance.dark) "#e8eaf0" else "#20242c"
    val secondary = if (appearance.dark) "#9fb2d0" else "#435d83"
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        html,body{margin:0;background:$background;color:$foreground;font-family:system-ui,sans-serif;}
        body{font-size:${appearance.fontSize}px;line-height:${appearance.lineHeight};}
        main{width:${(appearance.contentWidth * 100).toInt()}%;max-width:980px;margin:0 auto;padding:36px 0 120px;overflow-wrap:anywhere;}
        .plain{white-space:pre-wrap;} h1,h2,h3{line-height:1.3;} a{color:$secondary;text-decoration:none;pointer-events:none;}
        img{max-width:100%;} pre,code{white-space:pre-wrap;background:rgba(127,127,127,.12);border-radius:6px;}
        blockquote{margin-left:0;padding-left:18px;border-left:4px solid $secondary;color:$secondary;}
        </style></head><body><main>$body</main></body></html>
    """.trimIndent()
}
