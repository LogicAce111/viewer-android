package com.legion.viewer.data

import com.legion.viewer.model.ComicWork
import org.junit.Assert.*
import org.junit.Test

class ComicDirectoriesTest {
    // Directory grouping never reads cover/pages; these fixtures isolate book identity and paths.
    private fun book(path: String) = ComicWork(path.substringAfterLast('/').ifEmpty { "根目录" }, path, emptyList())

    @Test fun imageDirectoryIsTheBookBoundary() {
        assertEquals("合集/第1本", comicBookDirectory("合集/第1本/001.jpg"))
        assertEquals("第1本", comicBookDirectory("第1本/001.jpg"))
        assertEquals("", comicBookDirectory("001.jpg"))
        assertEquals("根目录", comicBookDirectory("根目录/001.jpg"))
    }

    @Test fun collectionContainsBooksWithoutFlatteningThemIntoPages() {
        val one = book("合集A/第1本")
        val two = book("合集A/第2本")
        val groups = groupComicWorks(listOf(one, two, book("合集B/第1本")))
        assertEquals(listOf("合集A", "合集B"), groups.map { it.relativePath })
        assertEquals(listOf(one, two), groups[0].works)
        assertSame(one, groups[0].works[0])
    }

    @Test fun deeperCollectionsWithSameNameRemainDistinct() {
        val groups = groupComicWorks(listOf(book("作者A/合集/第1本"), book("作者B/合集/第1本")))
        assertEquals(listOf("作者A/合集", "作者B/合集"), groups.map { it.relativePath })
    }

    @Test fun rootImagesAndDirectBooksStayInRootCollection() {
        val groups = groupComicWorks(listOf(book("第1本"), book(""), book("合集/第1本")))
        assertEquals("", groups.first().relativePath)
        assertEquals("根目录", groups.first().name)
        assertEquals(listOf("", "第1本"), groups.first().works.map { it.relativePath })
    }

    @Test fun foldersActuallyNamedRootDoNotCollideWithRootImages() {
        val groups = groupComicWorks(listOf(book(""), book("根目录"), book("根目录/第1本")))
        assertEquals(2, groups.size)
        assertEquals(listOf("", "根目录"), groups[0].works.map { it.relativePath })
        assertEquals("根目录（文件夹）", groups[1].name)
        assertEquals(listOf("根目录/第1本"), groups[1].works.map { it.relativePath })
    }

    @Test fun mixedParentImagesAndSubBooksAreSeparateWorks() {
        val groups = groupComicWorks(listOf(book("合集"), book("合集/第1本")))
        assertEquals(listOf("合集"), groups[0].works.map { it.relativePath })
        assertEquals(listOf("合集/第1本"), groups[1].works.map { it.relativePath })
    }

    @Test fun groupsAndBooksUseNaturalNumericOrder() {
        val groups = groupComicWorks(listOf(book("合集10/卷1"), book("合集2/卷10"), book("合集2/卷2")))
        assertEquals(listOf("合集2", "合集10"), groups.map { it.relativePath })
        assertEquals(listOf("卷2", "卷10"), groups.first().works.map { it.name })
    }

    @Test fun largeCollectionsKeepEveryBookAndAccurateCounts() {
        val works = (1..135).map { book("合集/卷$it") }
        val groups = groupComicWorks(works.reversed())
        assertEquals(135, groups.single().works.size)
        assertEquals(works, groups.single().works)
    }

    @Test fun emptyLibraryHasNoGroups() {
        assertTrue(groupComicWorks(emptyList()).isEmpty())
        assertTrue(buildComicWorks(emptyList()).isEmpty())
    }
}
