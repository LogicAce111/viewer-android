package com.legion.viewer.data

import com.legion.viewer.model.ComicWork
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibrarySearchTest {
    @Test fun trimsUnicodeWhitespaceAndNormalizesEnglish() {
        assertEquals(listOf("合集", "vol2"), searchTerms(" \t合集　VOL2\n"))
        assertTrue(searchTerms(" \t　\n").isEmpty())
    }

    @Test fun allKeywordsMayMatchNameAndDirectorySeparately() = runBlocking {
        val index = NameDirectoryIndex(listOf(
            SearchEntry("one", "第2集.MP4", "科幻/系列A/第2集.MP4"),
            SearchEntry("two", "第2集.MP4", "纪录片/第2集.MP4"),
        ))
        assertEquals(listOf("one"), index.search(searchTerms("科幻 mp4")))
        assertTrue(index.search(searchTerms("科幻 不存在")).isEmpty())
    }

    @Test fun searchKeepsSourceNaturalOrderAndIncludesEverythingBeyondSixty() = runBlocking {
        val index = NameDirectoryIndex((1..145).map { SearchEntry(it, "第${it}集.mp4", "连续剧/第${it}集.mp4") })
        assertEquals((1..145).toList(), index.search(searchTerms("连续剧")))
        assertEquals(listOf(145), index.search(searchTerms("第145集")))
    }

    @Test fun sameNamesInDifferentDirectoriesRemainDistinct() = runBlocking {
        val index = NameDirectoryIndex(listOf(SearchEntry(1, "笔记.md", "工作/笔记.md"), SearchEntry(2, "笔记.md", "生活/笔记.md")))
        assertEquals(listOf(1, 2), index.search(searchTerms("笔记")))
        assertEquals(listOf(2), index.search(searchTerms("生活 笔记")))
    }

    @Test fun comicsMatchWholeBooksAndCollectionsNotImageFilenames() = runBlocking {
        val first = ComicWork("卷2", "作者/合集/卷2", emptyList())
        val second = ComicWork("卷10", "作者/合集/卷10", emptyList())
        val other = ComicWork("卷2", "其他作者/卷2", emptyList())
        val index = comicSearchIndex(listOf(first, second, other))
        assertEquals(listOf(first, second), index.search(searchTerms("合集")))
        assertEquals(listOf(first), index.search(searchTerms("合集 卷2")))
        assertTrue(index.search(searchTerms("001.jpg")).isEmpty())
    }

    @Test fun rootComicAndLiteralSymbolsCanBeFound() = runBlocking {
        val root = ComicWork("根目录", "", emptyList())
        val index = comicSearchIndex(listOf(root, ComicWork("[番外]", "合集/[番外]", emptyList())))
        assertEquals(listOf(root), index.search(searchTerms("根目录")))
        assertEquals(1, index.search(searchTerms("[番外]")).size)
    }

    @Test fun emptyQueryMatchesAllAndMissingQueryMatchesNothing() = runBlocking {
        val index = NameDirectoryIndex(listOf(SearchEntry(1, "A.mp3", "音乐/A.mp3")))
        assertEquals(listOf(1), index.search(emptyList()))
        assertTrue(index.search(searchTerms("正文里的文字")).isEmpty())
        assertTrue(NameDirectoryIndex<String>(emptyList()).search(searchTerms("任意")).isEmpty())
    }

    @Test fun playbackQueueContainsAllMatchesAndIsIndependentOfLaterChanges() {
        val results = (1..140).toMutableList()
        val queue = snapshotPlaybackQueue(results, 100)
        results.clear()
        assertEquals((1..140).toList(), queue)
        assertNull(snapshotPlaybackQueue(listOf(1, 2), 3))
        assertNull(snapshotPlaybackQueue(emptyList<Int>(), 1))
    }
}
