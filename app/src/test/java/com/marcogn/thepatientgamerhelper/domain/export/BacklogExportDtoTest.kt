package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.testutil.sampleBacklogItem
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BacklogExportDtoTest {

    @Test
    fun `groups items under their own list, ordered by position`() {
        val lists = listOf(
            BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH),
            BacklogList(id = 2L, name = "In pausa", position = 1, createdAt = Instant.EPOCH),
        )
        val items = listOf(
            sampleBacklogItem(id = "b", listId = 1L, title = "Hades II", position = 1),
            sampleBacklogItem(id = "a", listId = 1L, title = "Hades", position = 0),
            sampleBacklogItem(id = "c", listId = 2L, title = "Celeste", position = 0),
        )

        val payload = buildBacklogExportPayload(lists, items, exportedAt = Instant.EPOCH)

        assertEquals(2, payload.liste.size)
        assertEquals(listOf("Hades", "Hades II"), payload.liste[0].elementi.map { it.titolo })
        assertEquals(listOf("Celeste"), payload.liste[1].elementi.map { it.titolo })
    }

    @Test
    fun `roundtrips through JSON`() {
        val lists = listOf(BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH))
        val items = listOf(
            sampleBacklogItem(
                id = "a",
                listId = 1L,
                title = "Hades",
                platforms = listOf("PC"),
                status = BacklogItemStatus.IN_CORSO,
                hltbMainStoryHours = 21.0,
            ),
        )
        val payload = buildBacklogExportPayload(lists, items, exportedAt = Instant.EPOCH)

        val decoded = payload.toJson().toBacklogExportPayload()

        assertEquals(payload, decoded)
    }

    @Test
    fun `converts back into importable lists, resolving the cover through the given callback`() {
        val lists = listOf(BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH))
        val items = listOf(sampleBacklogItem(id = "a", listId = 1L, title = "Hades").copy(coverImagePath = "/data/covers/original.jpg"))
        val payload = buildBacklogExportPayload(lists, items, exportedAt = Instant.EPOCH)

        val imported = payload.toImportedLists { fileName -> "/data/covers/restored-$fileName" }

        assertEquals(1, imported.size)
        assertEquals("Da giocare", imported[0].name)
        assertEquals("Hades", imported[0].items[0].title)
        assertEquals("/data/covers/restored-original.jpg", imported[0].items[0].coverImagePath)
    }

    @Test
    fun `an unresolvable cover callback yields a null cover path`() {
        val lists = listOf(BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH))
        val items = listOf(sampleBacklogItem(id = "a", listId = 1L))
        val payload = buildBacklogExportPayload(lists, items, exportedAt = Instant.EPOCH)

        val imported = payload.toImportedLists { null }

        assertNull(imported[0].items[0].coverImagePath)
    }

    @Test
    fun `unknown status or history event type falls back to a safe default instead of throwing`() {
        val json = """
            {"schemaVersion":1,"esportatoIl":"1970-01-01T00:00:00Z","liste":[{"nome":"L","elementi":[
            {"titolo":"G","piattaforme":[],"generi":[],"tag":[],"stato":"QUALCOSA_DI_NUOVO",
            "aggiuntoIl":"1970-01-01T00:00:00Z","dataInizio":null,"dataCompletamento":null,
            "notaAbbandono":null,"annoUscita":null,"sviluppatore":null,"hltbStoriaPrincipaleOre":null,
            "hltbStoriaPiuExtraOre":null,"hltbCompletistaOre":null,"copertina":null,"commenti":[],
            "storico":[{"tipo":"EVENTO_SCONOSCIUTO","timestamp":"1970-01-01T00:00:00Z","dettaglio":null}]}
            ]}]}
        """.trimIndent()

        val imported = json.toBacklogExportPayload().toImportedLists { null }

        assertEquals(BacklogItemStatus.DA_INIZIARE, imported[0].items[0].status)
        assertEquals(BacklogHistoryEventType.CREATO, imported[0].items[0].history[0].type)
    }
}
