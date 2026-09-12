package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Тесты батчинга записи MasteryStore (TASK-091, пункт 1).
 *
 * Контракт:
 * - горячие мутации не пишут на диск синхронно — только кеш + отложенный дамп;
 * - чтение-после-записи идёт из кеша и валидно до flush();
 * - flush() блокирующе делает полный снимок durable;
 * - clear()/clearPack() поглощают отложенную запись и не воскрешают данные;
 * - повторное открытие того же файла дрейнит чужие отложенные записи.
 */
@RunWith(RobolectricTestRunner::class)
class MasteryStoreBatchingTest {

    private lateinit var store: MasteryStoreImpl
    private lateinit var file: File
    private val debounceMs = 150L

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "grammarmate").deleteRecursively()
        store = MasteryStoreImpl(context, debounceMs = debounceMs)
        file = File(context.filesDir, "grammarmate/mastery.yaml")
    }

    @Test
    fun hotMutations_deferDiskWrite_thenFlushWritesFullSnapshot() {
        repeat(3) { store.recordCardShowForPack("PACK", "lesson_01", "card_$it") }
        store.recordCardEncounterForPack("PACK", "lesson_01", "card_0")
        store.recordCardEncounterForPack("PACK", "lesson_01", "card_0")
        store.recordSelfProducedForPack("PACK", "lesson_01", totalEffortCards = 7)

        // Запись отложена: файла ещё нет, пока debounce-окно не истекло и flush() не вызван.
        assertFalse("Write must be deferred off the mutation path", file.exists())

        store.flush()

        assertTrue(file.exists())
        val reopened = MasteryStoreImpl(RuntimeEnvironment.getApplication(), debounceMs = debounceMs)
        val state = reopened.getForPack("PACK", "lesson_01")!!
        assertEquals(3, state.uniqueCardShows)
        assertEquals(3, state.totalCardShows)
        assertEquals(2, state.cardEncounterCounts["card_0"])
        assertEquals(7, state.effortAtLastReview)
        assertTrue(state.lastReviewMs > 0)
    }

    @Test
    fun getForPack_returnsFreshValue_beforeFlush() {
        store.recordCardShowForPack("PACK", "lesson_01", "card_1")
        store.recordSelfProducedForPack("PACK", "lesson_01", totalEffortCards = 42)

        val state = store.getForPack("PACK", "lesson_01")!!
        assertEquals(1, state.uniqueCardShows)
        assertEquals(42, state.effortAtLastReview)
    }

    @Test
    fun clear_afterMutation_doesNotResurrectDataViaDeferredWrite() {
        store.recordCardShowForPack("PACK", "lesson_01", "card_1")

        store.clear()

        assertFalse(file.exists())
        assertNull(store.getForPack("PACK", "lesson_01"))

        // Ждём больше debounce-окна: отложенный дамп не должен воскресить данные.
        Thread.sleep(debounceMs * 3)
        assertFalse("Deferred write must not resurrect cleared data", file.exists())
        assertNull(store.getForPack("PACK", "lesson_01"))
    }

    @Test
    fun clearPack_absorbsPendingWrite_andKeepsOtherPacks() {
        store.recordCardShowForPack("PACK_A", "lesson_01", "card_1")
        store.recordCardShowForPack("PACK_B", "lesson_01", "card_1")

        store.clearPack("PACK_A")
        store.flush()

        val reopened = MasteryStoreImpl(RuntimeEnvironment.getApplication(), debounceMs = debounceMs)
        assertNull(reopened.getForPack("PACK_A", "lesson_01"))
        assertEquals(1, reopened.getForPack("PACK_B", "lesson_01")!!.uniqueCardShows)
    }

    @Test
    fun reopen_drainsPendingWriteOfAnotherInstance() {
        store.recordCardShowForPack("PACK", "lesson_01", "card_1")
        store.recordCardShowForPack("PACK", "lesson_01", "card_2")

        // Без flush(): новое открытие того же файла само делает данные durable.
        val reopened = MasteryStoreImpl(RuntimeEnvironment.getApplication(), debounceMs = debounceMs)
        val state = reopened.getForPack("PACK", "lesson_01")!!
        assertEquals(2, state.uniqueCardShows)
        assertTrue(file.exists())
    }
}
