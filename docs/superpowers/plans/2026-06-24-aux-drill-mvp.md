# Подводящие упражнения (Aux Drill) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new isolated "Подводящие" (lead-in) drill mode that mass-drills the auxiliary/first verb of compound tenses (avere/essere/stare) using the existing `it_verb_groups_all.csv` pool, fully separate from Verb Drill.

**Architecture:** New isolated layer — own model, store, catalog, ViewModel, screen, route. Reuses only `LessonStore.getVerbDrillFiles` / `hasVerbDrill` and `VerbDrillCsvParser.parse` to read the SAME pool file. No changes to existing Verb Drill code (regression safety).

**Tech Stack:** Kotlin, Jetpack Compose, AndroidViewModel, YAML (snakeyaml), JUnit + Robolectric/fakes for tests.

**Spec:** `docs/superpowers/specs/2026-06-24-aux-drill-mvp-design.md`

---

## File Structure

**Create:**
- `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCard.kt` — data model (card, progress, session, ui state).
- `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCatalog.kt` — static catalog of (verb×tense → lesson) pairs.
- `app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt` — interface + impl for progress + last session (own yaml files).
- `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt` — ViewModel with filter + session logic.
- `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillScreen.kt` — menu screen + training screen.
- `app/src/test/java/com/alexpo/grammermate/data/AuxDrillCatalogTest.kt`
- `app/src/test/java/com/alexpo/grammermate/data/AuxDrillStoreTest.kt`
- `app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt`
- `app/src/test/java/com/alexpo/grammermate/testharness/FakeAuxDrillStore.kt`

**Modify:**
- `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt` — add `Routes.AUX_DRILL` + composable + navigation wiring.
- `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt` — add `AuxDrillEntryTile` + show it.
- `app/src/main/java/com/alexpo/grammermate/AppContainer.kt` — add `auxDrillStore(packId)` accessor.
- `app/src/main/java/com/alexpo/grammermate/data/StoreFactory.kt` — add `getAuxDrillStore(packId)` cache.
- `app/src/main/res/values/strings-app.xml` + `values-ru/strings-app.xml` — aux drill labels.

---

## Task 1: AuxDrillCard data model

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCard.kt`

This task defines the isolated data model. No test needed for pure data classes — verified by compilation and by downstream tasks' tests.

- [ ] **Step 1: Write the model file**

Create `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCard.kt`:

```kotlin
package com.alexpo.grammermate.data

/**
 * Card in the aux (lead-in) drill pool. Mapped on-the-fly from parsed
 * VerbDrillCsvParser output — the underlying pool file is shared with Verb
 * Drill, but this model is independent to keep the aux layer isolated.
 */
data class AuxDrillCard(
    val id: String,
    val promptRu: String,
    val answer: String,
    val verb: String? = null,
    val tense: String? = null,
    val group: String? = null,
    val person: String? = null,
    val rank: Int? = null
)

/** Per-(verb|tense) progress for aux drill. Stored under its own yaml file. */
data class AuxDrillComboProgress(
    val verb: String,
    val tense: String,
    val totalCards: Int,
    val everShownCardIds: Set<String> = emptySet(),
    val todayShownCardIds: Set<String> = emptySet(),
    val lastDate: String = ""
)

data class AuxDrillSessionState(
    val cards: List<AuxDrillCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val isComplete: Boolean = false
)

data class AuxDrillUiState(
    val availablePairs: List<AuxDrillPair> = emptyList(),
    val selectedPair: AuxDrillPair? = null,
    val totalCards: Int = 0,
    val everShownCount: Int = 0,
    val todayShownCount: Int = 0,
    val session: AuxDrillSessionState? = null,
    val allDoneToday: Boolean = false,
    val isLoading: Boolean = true,
    val loadedLanguageId: String? = null
)
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin` (Windows: `gradlew :app:compileDebugKotlin`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/AuxDrillCard.kt
git commit -m "feat(aux-drill): add isolated AuxDrillCard data model"
```

---

## Task 2: AuxDrillCatalog (verb×tense → lesson)

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCatalog.kt`
- Create: `app/src/test/java/com/alexpo/grammermate/data/AuxDrillCatalogTest.kt`

The catalog is the "brain" of the mode: which (verb, tense) pairs are offered and which lesson each leads to.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/alexpo/grammermate/data/AuxDrillCatalogTest.kt`:

```kotlin
package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxDrillCatalogTest {

    @Test
    fun allPairs_useOnlyCoreAuxVerbs() {
        val verbs = AuxDrillCatalog.ALL.map { it.verb }.toSet()
        assertEquals(setOf("avere", "essere", "stare"), verbs)
    }

    @Test
    fun allPairs_targetValidPoolTenses() {
        val tenses = AuxDrillCatalog.ALL.map { it.tense }.toSet()
        val expected = setOf(
            "Presente", "Imperfetto", "Futuro Semplice",
            "Condizionale Presente", "Congiuntivo Presente"
        )
        assertEquals(expected, tenses)
    }

    @Test
    fun allPairs_haveLessonTarget() {
        AuxDrillCatalog.ALL.forEach { pair ->
            assertTrue("pair $pair has blank lesson", pair.lessonId.isNotBlank())
            assertTrue("pair $pair has blank topic", pair.lessonTopic.isNotBlank())
        }
    }

    @Test
    fun catalog_hasTwelvePairs() {
        assertEquals(12, AuxDrillCatalog.ALL.size)
    }

    @Test
    fun pairsGroupByCategory() {
        val categories = AuxDrillCatalog.ALL.map { it.category }.toSet()
        assertTrue("Настоящее" in categories)
        assertTrue("Прошедшее" in categories)
    }

    @Test
    fun averePresente_leadsToB03() {
        val pair = AuxDrillCatalog.ALL.first {
            it.verb == "avere" && it.tense == "Presente"
        }
        assertEquals("B03", pair.lessonId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.AuxDrillCatalogTest"` (Windows: `gradlew ...`)
Expected: FAIL — unresolved reference `AuxDrillCatalog`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/alexpo/grammermate/data/AuxDrillCatalog.kt`:

```kotlin
package com.alexpo.grammermate.data

/**
 * One lead-in drill pair: mass-drill [verb] in [tense], which prepares the
 * student for [lessonId].
 */
data class AuxDrillPair(
    val category: String,
    val verb: String,
    val tense: String,
    val lessonId: String,
    val lessonTopic: String
)

/**
 * Static catalog of aux (lead-in) drill pairs. Source of truth for the menu
 * screen. MVP scope: only avere/essere/stare in tenses fully covered by the
 * pool (it_verb_groups_all.csv). Each pair yields ~35 pool cards
 * (7 persons × 5 collocations).
 */
object AuxDrillCatalog {

    val ALL: List<AuxDrillPair> = listOf(
        // Настоящее
        AuxDrillPair("Настоящее", "avere", "Presente", "B03", "Passato Prossimo con Avere"),
        AuxDrillPair("Настоящее", "essere", "Presente", "B04", "Passato Prossimo con Essere"),
        AuxDrillPair("Настоящее", "stare", "Presente", "B17", "Stare + gerundio"),
        // Прошедшее
        AuxDrillPair("Прошедшее", "avere", "Imperfetto", "B06", "Trapassato Prossimo"),
        AuxDrillPair("Прошедшее", "essere", "Imperfetto", "B06", "Trapassato Prossimo"),
        AuxDrillPair("Прошедшее", "stare", "Imperfetto", "C12", "Stare + gerundio nel passato"),
        // Будущее
        AuxDrillPair("Будущее", "avere", "Futuro Semplice", "B08", "Futuro Anteriore"),
        AuxDrillPair("Будущее", "essere", "Futuro Semplice", "B08", "Futuro Anteriore"),
        // Условное
        AuxDrillPair("Условное", "avere", "Condizionale Presente", "B10", "Condizionale Composto"),
        AuxDrillPair("Условное", "essere", "Condizionale Presente", "B10", "Condizionale Composto"),
        // Сослагательное
        AuxDrillPair("Сослагательное", "avere", "Congiuntivo Presente", "B25", "Congiuntivo Presente"),
        AuxDrillPair("Сослагательное", "essere", "Congiuntivo Presente", "B26", "Congiuntivo Presente: irregolari")
    )

    /** Pairs grouped by category, preserving declaration order. */
    val GROUPED: Map<String, List<AuxDrillPair>> = ALL.groupBy { it.category }

    /** Aux verbs that the pool must contain for these pairs to work. */
    val AUX_VERBS: Set<String> = ALL.map { it.verb }.toSet()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.AuxDrillCatalogTest"`
Expected: PASS — all 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/AuxDrillCatalog.kt app/src/test/java/com/alexpo/grammermate/data/AuxDrillCatalogTest.kt
git commit -m "feat(aux-drill): add catalog of 12 verb×tense→lesson pairs"
```

---

## Task 3: AuxDrillStore interface + fake

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt`
- Create: `app/src/test/java/com/alexpo/grammermate/testharness/FakeAuxDrillStore.kt`

First the interface and an in-memory fake (no Android deps) so the ViewModel can be developed/tested before the YAML impl.

- [ ] **Step 1: Write the interface**

Create `app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt`:

```kotlin
package com.alexpo.grammermate.data

interface AuxDrillStore {

    fun loadProgress(): Map<String, AuxDrillComboProgress>

    fun saveProgress(progress: Map<String, AuxDrillComboProgress>)

    fun upsertComboProgress(key: String, progress: AuxDrillComboProgress)

    /** Flush pending writes to disk. */
    fun flush()
}
```

- [ ] **Step 2: Write the fake**

Create `app/src/test/java/com/alexpo/grammermate/testharness/FakeAuxDrillStore.kt`:

```kotlin
package com.alexpo.grammermate.testharness

import com.alexpo.grammermate.data.AuxDrillComboProgress
import com.alexpo.grammermate.data.AuxDrillStore

/** In-memory fake for tests. No file I/O, no Android deps. */
class FakeAuxDrillStore : AuxDrillStore {

    private val progressData = mutableMapOf<String, AuxDrillComboProgress>()

    override fun loadProgress(): Map<String, AuxDrillComboProgress> = progressData

    override fun saveProgress(progress: Map<String, AuxDrillComboProgress>) {
        progressData.clear()
        progressData.putAll(progress)
    }

    override fun upsertComboProgress(key: String, progress: AuxDrillComboProgress) {
        progressData[key] = progress
    }

    override fun flush() {
        // No-op
    }

    fun clear() {
        progressData.clear()
    }
}
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:compileDebugUnitTestKotlin` (Windows: `gradlew :app:compileDebugUnitTestKotlin`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt app/src/test/java/com/alexpo/grammermate/testharness/FakeAuxDrillStore.kt
git commit -m "feat(aux-drill): add AuxDrillStore interface + fake"
```

---

## Task 4: AuxDrillStore YAML implementation

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt` (append impl)
- Modify: `app/src/main/java/com/alexpo/grammermate/data/StoreFactory.kt` (add accessor)
- Modify: `app/src/main/java/com/alexpo/grammermate/AppContainer.kt` (add accessor)
- Create: `app/src/test/java/com/alexpo/grammermate/data/AuxDrillStoreTest.kt`

YAML impl mirrors `VerbDrillStoreImpl` but uses separate files `aux_drill_progress.yaml` so progress never mixes with Verb Drill.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/alexpo/grammermate/data/AuxDrillStoreTest.kt`:

```kotlin
package com.alexpo.grammermate.data

import com.alexpo.grammermate.testharness.FakeAuxDrillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxDrillStoreTest {

    @Test
    fun upsertComboProgress_storesByKey() {
        val store = FakeAuxDrillStore()
        val key = "aux|avere|Presente"
        val progress = AuxDrillComboProgress(
            verb = "avere",
            tense = "Presente",
            totalCards = 35,
            everShownCardIds = setOf("c1", "c2"),
            todayShownCardIds = setOf("c1"),
            lastDate = "2026-06-24"
        )

        store.upsertComboProgress(key, progress)

        assertEquals(progress, store.loadProgress()[key])
    }

    @Test
    fun upsertComboProgress_overwritesExisting() {
        val store = FakeAuxDrillStore()
        val key = "aux|essere|Imperfetto"
        val first = AuxDrillComboProgress("essere", "Imperfetto", 35, setOf("a"))
        val second = AuxDrillComboProgress("essere", "Imperfetto", 35, setOf("a", "b", "c"))

        store.upsertComboProgress(key, first)
        store.upsertComboProgress(key, second)

        assertEquals(3, store.loadProgress()[key]?.everShownCardIds?.size)
    }

    @Test
    fun saveProgress_replacesAll() {
        val store = FakeAuxDrillStore()
        store.upsertComboProgress("old", AuxDrillComboProgress("avere", "Presente", 35))
        val newMap = mapOf(
            "aux|stare|Presente" to AuxDrillComboProgress("stare", "Presente", 35)
        )

        store.saveProgress(newMap)

        assertEquals(1, store.loadProgress().size)
        assertTrue(store.loadProgress().containsKey("aux|stare|Presente"))
    }

    @Test
    fun loadProgress_emptyWhenNothingSaved() {
        val store = FakeAuxDrillStore()
        assertTrue(store.loadProgress().isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.AuxDrillStoreTest"`
Expected: PASS (tests use the Fake, which already exists from Task 3). If it fails, the Fake has a bug — fix before proceeding.

Note: these tests lock the Fake contract. The YAML impl is verified by compilation + manual run; a full Robolectric yaml round-trip test is out of MVP scope (matches the VerbDrillStore test coverage level).

- [ ] **Step 3: Write the YAML implementation**

Append to `app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt` (below the interface):

```kotlin
import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AuxDrillStoreImpl(
    context: Context,
    private val packId: String? = null
) : AuxDrillStore {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")

    private val languageId: String = packId?.let {
        val parts = it.split("-")
        if (parts.size >= 2) parts[1] else "en"
    } ?: "en"

    // Pack-scoped path mirrors VerbDrillStoreImpl; filename is distinct so the
    // two stores never share a file.
    private val packDir: File? = packId?.let { File(baseDir, "drills/$it") }
    private val file: File = packDir?.let { File(it, "aux_drill_progress.yaml") }
        ?: File(baseDir, "aux_drill_progress_${languageId}.yaml")

    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    private var progressCache: Map<String, AuxDrillComboProgress>? = null

    override fun loadProgress(): Map<String, AuxDrillComboProgress> {
        progressCache?.let { return it }
        val loaded = loadProgressFromDisk()
        progressCache = loaded
        return loaded
    }

    private fun loadProgressFromDisk(): Map<String, AuxDrillComboProgress> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null }
            ?: return emptyMap()
        val data = raw as? Map<*, *> ?: return emptyMap()
        val payload = (data["data"] as? Map<*, *>) ?: data
        val result = mutableMapOf<String, AuxDrillComboProgress>()
        for ((key, value) in payload) {
            val comboKey = key as? String ?: continue
            val entry = value as? Map<*, *> ?: continue
            val verb = entry["verb"] as? String ?: continue
            val tense = entry["tense"] as? String ?: continue
            @Suppress("UNCHECKED_CAST")
            result[comboKey] = AuxDrillComboProgress(
                verb = verb,
                tense = tense,
                totalCards = (entry["totalCards"] as? Int) ?: 0,
                everShownCardIds = (entry["everShownCardIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                todayShownCardIds = (entry["todayShownCardIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                lastDate = entry["lastDate"] as? String ?: ""
            )
        }
        return result
    }

    override fun saveProgress(progress: Map<String, AuxDrillComboProgress>) {
        progressCache = progress
        persistProgressToDisk()
    }

    override fun upsertComboProgress(key: String, progress: AuxDrillComboProgress) = mutex.withLock {
        val all = loadProgress().toMutableMap()
        all[key] = progress
        progressCache = all
        persistProgressToDisk()
    }

    override fun flush() {
        // Writes are immediate.
    }

    private fun persistProgressToDisk() {
        val progress = progressCache ?: return
        val comboPayload = linkedMapOf<String, Any>()
        for ((key, value) in progress) {
            comboPayload[key] = linkedMapOf(
                "verb" to value.verb,
                "tense" to value.tense,
                "totalCards" to value.totalCards,
                "everShownCardIds" to value.everShownCardIds.toList(),
                "todayShownCardIds" to value.todayShownCardIds.toList(),
                "lastDate" to value.lastDate
            )
        }
        val data = linkedMapOf(
            "schemaVersion" to schemaVersion,
            "data" to comboPayload
        )
        try {
            AtomicFileWriter.writeText(file, yaml.dump(data))
        } catch (e: IOException) {
            Log.e("AuxDrillStore", "Failed to save aux drill progress: ${file.name}", e)
            throw e
        }
    }
}
```

- [ ] **Step 4: Add StoreFactory accessor**

In `app/src/main/java/com/alexpo/grammermate/data/StoreFactory.kt`:

Add a cache field next to `verbDrillCache` (around line 16):
```kotlin
private val auxDrillCache = mutableMapOf<String?, AuxDrillStoreImpl>()
```

Add an accessor method next to `getVerbDrillStore` (after line 44):
```kotlin
@Synchronized
fun getAuxDrillStore(packId: String?): AuxDrillStore {
    return auxDrillCache.getOrPut(packId) {
        AuxDrillStoreImpl(appContext, packId = packId)
    }
}
```

- [ ] **Step 5: Add AppContainer accessor**

In `app/src/main/java/com/alexpo/grammermate/AppContainer.kt`, next to the `verbDrillStore` accessor (line ~58):

```kotlin
fun auxDrillStore(packId: String?): AuxDrillStore = storeFactory.getAuxDrillStore(packId)
```

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.AuxDrillStoreTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt app/src/main/java/com/alexpo/grammermate/data/StoreFactory.kt app/src/main/java/com/alexpo/grammermate/AppContainer.kt app/src/test/java/com/alexpo/grammermate/data/AuxDrillStoreTest.kt
git commit -m "feat(aux-drill): add YAML-backed AuxDrillStoreImpl + factory wiring"
```

---

## Task 5: AuxDrillViewModel — loading + filtering

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt`
- Create: `app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt`

The ViewModel loads the shared pool, maps to `AuxDrillCard`, and filters by selected pair. This task covers loading + filtering + progress display; session logic comes in Task 6.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt`:

```kotlin
package com.alexpo.grammermate.ui

import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.AuxDrillCard
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.testharness.FakeAuxDrillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxDrillViewModelTest {

    private fun makeCards(): List<VerbDrillCard> {
        // 3 avere/Presente + 3 essere/Presente + 2 other
        return listOf(
            VerbDrillCard("a1", "ru1", "Io ho x", "avere", "Presente", "irregular_unique", "Io", 1),
            VerbDrillCard("a2", "ru2", "Tu hai x", "avere", "Presente", "irregular_unique", "Tu", 2),
            VerbDrillCard("a3", "ru3", "Lui ha x", "avere", "Presente", "irregular_unique", "Lui", 3),
            VerbDrillCard("e1", "ru4", "Io sono x", "essere", "Presente", "irregular_unique", "Io", 1),
            VerbDrillCard("e2", "ru5", "Tu sei x", "essere", "Presente", "irregular_unique", "Tu", 2),
            VerbDrillCard("e3", "ru6", "Lui è x", "essere", "Presente", "irregular_unique", "Lui", 3),
            VerbDrillCard("o1", "ru7", "Io compro", "comprare", "Presente", "regular_are", "Io", 1),
            VerbDrillCard("o2", "ru8", "Io compravo", "comprare", "Imperfetto", "regular_are", "Io", 1)
        )
    }

    @Test
    fun injectCards_buildsAuxCardsFromPool() {
        val vm = AuxDrillViewModel(FakeAuxDrillStore())
        vm.injectPoolForTest(makeCards())

        assertEquals(8, vm.allPoolCardsForTest().size)
    }

    @Test
    fun selectPair_filtersToAuxVerbAndTense() {
        val vm = AuxDrillViewModel(FakeAuxDrillStore())
        vm.injectPoolForTest(makeCards())

        val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
        vm.selectPair(pair)

        val filtered = vm.currentFilteredCardsForTest()
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.verb == "avere" && it.tense == "Presente" })
    }

    @Test
    fun selectPair_reportsTotalCardsCount() {
        val vm = AuxDrillViewModel(FakeAuxDrillStore())
        vm.injectPoolForTest(makeCards())

        val pair = AuxDrillCatalog.ALL.first { it.verb == "essere" && it.tense == "Presente" }
        vm.selectPair(pair)

        assertEquals(3, vm.uiState.value.totalCards)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.AuxDrillViewModelTest"`
Expected: FAIL — unresolved reference `AuxDrillViewModel`.

- [ ] **Step 3: Write the ViewModel (loading + filtering only)**

Create `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt`:

```kotlin
package com.alexpo.grammermate.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexpo.grammermate.AppContainer
import com.alexpo.grammermate.GrammarMateApplication
import com.alexpo.grammermate.data.AuxDrillCard
import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.AuxDrillComboProgress
import com.alexpo.grammermate.data.AuxDrillPair
import com.alexpo.grammermate.data.AuxDrillStore
import com.alexpo.grammermate.data.AuxDrillUiState
import com.alexpo.grammermate.data.LessonStore
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.data.VerbDrillCsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuxDrillViewModel(application: Application) : AndroidViewModel(application) {

    private val logTag = "AuxDrillVM"
    private val container: AppContainer = when (application) {
        is GrammarMateApplication -> application.container
        else -> AppContainer(application)
    }

    private val lessonStore: LessonStore = container.lessonStore
    private var auxDrillStore: AuxDrillStore = container.auxDrillStore(null)
    private var usingTestStore = false

    /** Test-only constructor with an injected store. */
    constructor(application: Application, testStore: AuxDrillStore) : this(application) {
        auxDrillStore = testStore
        usingTestStore = true
    }

    private val _uiState = MutableStateFlow(AuxDrillUiState(availablePairs = AuxDrillCatalog.GROUPED.toList().flatMap { it.second }))
    val uiState: StateFlow<AuxDrillUiState> = _uiState

    private var allCards: List<AuxDrillCard> = emptyList()
    private var progressMap: Map<String, AuxDrillComboProgress> = emptyMap()
    private var currentPackId: String? = null
    private var sessionSize: Int = 10

    init {
        sessionSize = container.configStore.load().sessionSize
    }

    /**
     * Load the shared pool for the given pack. Maps parsed VerbDrillCards into
     * AuxDrillCards (same underlying file, separate model).
     */
    fun reloadForPack(packId: String, languageId: String) {
        sessionSize = container.configStore.load().sessionSize
        currentPackId = packId
        if (!usingTestStore) {
            auxDrillStore = container.auxDrillStore(packId)
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadCards(packId, languageId) }
    }

    private suspend fun loadCards(packId: String, languageId: String) {
        if (usingTestStore) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val ioResult = withContext(Dispatchers.IO) {
            val files = lessonStore.getVerbDrillFiles(packId, languageId)
            val cards = mutableListOf<AuxDrillCard>()
            for (file in files) {
                val parseResult = file.bufferedReader().use { reader ->
                    VerbDrillCsvParser.parse(reader)
                }
                val parsed = parseResult.data ?: continue
                cards.addAll(parsed.map { it.toAux() })
            }
            Triple(cards, auxDrillStore.loadProgress(), languageId)
        }
        allCards = ioResult.first
        progressMap = ioResult.second
        _uiState.update {
            it.copy(
                isLoading = false,
                loadedLanguageId = ioResult.third
            )
        }
        Log.d(logTag, "Loaded ${allCards.size} pool cards for aux drill")
    }

    private fun VerbDrillCard.toAux(): AuxDrillCard = AuxDrillCard(
        id = id,
        promptRu = promptRu,
        answer = answer,
        verb = verb,
        tense = tense,
        group = group,
        person = person,
        rank = rank
    )

    /** Select a (verb×tense) pair from the catalog and compute filtered pool. */
    fun selectPair(pair: AuxDrillPair) {
        val filtered = filteredCards(pair)
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        _uiState.update {
            it.copy(
                selectedPair = pair,
                totalCards = filtered.size,
                everShownCount = progress?.everShownCardIds?.size ?: 0,
                todayShownCount = progress?.todayShownCardIds?.size ?: 0,
                allDoneToday = false
            )
        }
    }

    private fun filteredCards(pair: AuxDrillPair): List<AuxDrillCard> =
        allCards.filter { it.verb == pair.verb && it.tense == pair.tense }

    internal fun comboKeyFor(pair: AuxDrillPair): String = "aux|${pair.verb}|${pair.tense}"

    // ── Test hooks ───────────────────────────────────────────────────────
    internal fun injectPoolForTest(cards: List<VerbDrillCard>) {
        allCards = cards.map { it.toAux() }
        progressMap = auxDrillStore.loadProgress()
        _uiState.update { it.copy(isLoading = false, loadedLanguageId = "it") }
    }

    internal fun allPoolCardsForTest(): List<AuxDrillCard> = allCards

    internal fun currentFilteredCardsForTest(): List<AuxDrillCard> =
        _uiState.value.selectedPair?.let { filteredCards(it) } ?: emptyList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.AuxDrillViewModelTest"`
Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt
git commit -m "feat(aux-drill): add ViewModel with pool loading + pair filtering"
```

---

## Task 6: AuxDrillViewModel — session logic

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt`
- Modify: `app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt` (append)

Add startSession / submitCorrectAnswer / markCardCompleted / exitSession, mirroring VerbDrillViewModel session logic but scoped to the selected pair.

- [ ] **Step 1: Append failing tests**

Append to `AuxDrillViewModelTest.kt` (inside the class):

```kotlin
@Test
fun startSession_buildsSessionFromFilteredCards() {
    val vm = AuxDrillViewModel(FakeAuxDrillStore())
    vm.injectPoolForTest(makeCards())

    val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
    vm.selectPair(pair)
    vm.setSessionSizeForTest(2)
    vm.startSession()

    val session = vm.uiState.value.session
    assertTrue(session != null)
    assertEquals(2, session?.cards?.size)
    assertTrue(session?.cards?.all { it.verb == "avere" } == true)
}

@Test
fun submitCorrectAnswer_advancesAndPersists() {
    val vm = AuxDrillViewModel(FakeAuxDrillStore())
    vm.injectPoolForTest(makeCards())
    val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
    vm.selectPair(pair)
    vm.setSessionSizeForTest(2)
    vm.startSession()

    val firstCardId = vm.uiState.value.session!!.cards.first().id
    vm.submitCorrectAnswer()

    assertEquals(1, vm.uiState.value.session?.correctCount)
    val key = "aux|avere|Presente"
    val progress = (vm.auxStoreForTest() as com.alexpo.grammermate.testharness.FakeAuxDrillStore).loadProgress()[key]
    assertTrue(progress?.everShownCardIds?.contains(firstCardId) == true)
}

@Test
fun startSession_marksAllDoneWhenNoCards() {
    val vm = AuxDrillViewModel(FakeAuxDrillStore())
    vm.injectPoolForTest(makeCards())
    // Futuro Semplice: no cards in test pool
    val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Futuro Semplice" }
    vm.selectPair(pair)
    vm.startSession()

    assertTrue(vm.uiState.value.allDoneToday)
    assertEquals(null, vm.uiState.value.session)
}

@Test
fun exitSession_clearsSession() {
    val vm = AuxDrillViewModel(FakeAuxDrillStore())
    vm.injectPoolForTest(makeCards())
    val pair = AuxDrillCatalog.ALL.first { it.verb == "avere" && it.tense == "Presente" }
    vm.selectPair(pair)
    vm.setSessionSizeForTest(2)
    vm.startSession()

    vm.exitSession()

    assertEquals(null, vm.uiState.value.session)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.AuxDrillViewModelTest"`
Expected: FAIL — `startSession`, `submitCorrectAnswer`, `exitSession`, `setSessionSizeForTest`, `auxStoreForTest` unresolved.

- [ ] **Step 3: Add session methods to the ViewModel**

Append to `AuxDrillViewModel.kt` (before the test-hooks section):

```kotlin
    private var cardShownTimestamp: Long = 0L

    fun setSessionSize(size: Int) {
        sessionSize = size.coerceIn(1, 1000)
    }

    fun startSession() {
        val pair = _uiState.value.selectedPair ?: return
        val filtered = filteredCards(pair)
        if (filtered.isEmpty()) {
            _uiState.update { it.copy(session = null, allDoneToday = true) }
            return
        }
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        val shownToday = progress?.todayShownCardIds ?: emptySet()
        val remaining = filtered.filter { it.id !in shownToday }

        val pool = if (remaining.isEmpty()) filtered else remaining
        val selected = pool.shuffled().take(sessionSize)

        _uiState.update {
            it.copy(
                session = com.alexpo.grammermate.data.AuxDrillSessionState(cards = selected),
                allDoneToday = false
            )
        }
        cardShownTimestamp = System.currentTimeMillis()
    }

    fun submitCorrectAnswer() {
        val session = _uiState.value.session ?: return
        if (session.isComplete || session.currentIndex >= session.cards.size) return
        val card = session.cards[session.currentIndex]
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    correctCount = session.correctCount + 1,
                    isComplete = isComplete
                )
            )
        }
        persistCardProgress(card)
        if (!isComplete) cardShownTimestamp = System.currentTimeMillis()
        updateProgressDisplay()
    }

    fun markCardCompleted() {
        val session = _uiState.value.session ?: return
        if (session.isComplete || session.currentIndex >= session.cards.size) return
        val card = session.cards[session.currentIndex]
        val nextIndex = session.currentIndex + 1
        val isComplete = nextIndex >= session.cards.size

        _uiState.update { state ->
            state.copy(
                session = session.copy(
                    currentIndex = nextIndex,
                    incorrectCount = session.incorrectCount + 1,
                    isComplete = isComplete
                )
            )
        }
        persistCardProgress(card)
        if (!isComplete) cardShownTimestamp = System.currentTimeMillis()
        updateProgressDisplay()
    }

    fun exitSession() {
        auxDrillStore.flush()
        _uiState.update { it.copy(session = null) }
    }

    private fun persistCardProgress(card: AuxDrillCard) {
        val pair = _uiState.value.selectedPair ?: return
        val comboKey = comboKeyFor(pair)
        val existing = progressMap[comboKey]
        val ever = (existing?.everShownCardIds ?: emptySet()) + card.id
        val today = (existing?.todayShownCardIds ?: emptySet()) + card.id
        val total = filteredCards(pair).size
        val updated = AuxDrillComboProgress(
            verb = pair.verb,
            tense = pair.tense,
            totalCards = total,
            everShownCardIds = ever,
            todayShownCardIds = today,
            lastDate = java.time.LocalDate.now().toString()
        )
        progressMap = progressMap.toMutableMap().apply { this[comboKey] = updated }
        auxDrillStore.upsertComboProgress(comboKey, updated)
    }

    private fun updateProgressDisplay() {
        val pair = _uiState.value.selectedPair ?: return
        val comboKey = comboKeyFor(pair)
        val progress = progressMap[comboKey]
        _uiState.update {
            it.copy(
                everShownCount = progress?.everShownCardIds?.size ?: 0,
                todayShownCount = progress?.todayShownCardIds?.size ?: 0
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPair = null, session = null, allDoneToday = false) }
    }
```

And add two test hooks next to the existing ones:

```kotlin
    internal fun setSessionSizeForTest(size: Int) = setSessionSize(size)

    internal fun auxStoreForTest(): AuxDrillStore = auxDrillStore
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.alexpo.grammermate.ui.AuxDrillViewModelTest"`
Expected: PASS — 7 tests total.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt app/src/test/java/com/alexpo/grammermate/ui/AuxDrillViewModelTest.kt
git commit -m "feat(aux-drill): add session start/answer/exit logic to ViewModel"
```

---

## Task 7: String resources

**Files:**
- Modify: `app/src/main/res/values/strings-app.xml`
- Modify: `app/src/main/res/values-ru/strings-app.xml`

- [ ] **Step 1: Add English strings**

In `app/src/main/res/values/strings-app.xml`, add near the verb_* block (after line ~80):

```xml
    <string name="aux_drill_title">Lead-in Exercises</string>
    <string name="aux_drill_entry">Lead-in</string>
    <string name="aux_drill_leads_to">Leads to %1$s — %2$s</string>
    <string name="aux_drill_select_pair">Select an auxiliary verb to drill</string>
    <string name="aux_drill_progress">%1$d / %2$d seen</string>
    <string name="aux_drill_today">Today: %1$d</string>
    <string name="aux_drill_all_done">All cards done today</string>
    <string name="aux_drill_start">Begin</string>
    <string name="aux_drill_content_desc_back">Back</string>
```

- [ ] **Step 2: Add Russian strings**

In `app/src/main/res/values-ru/strings-app.xml`, same position:

```xml
    <string name="aux_drill_title">Подводящие упражнения</string>
    <string name="aux_drill_entry">Подводящие</string>
    <string name="aux_drill_leads_to">Подводит к %1$s — %2$s</string>
    <string name="aux_drill_select_pair">Выберите служебный глагол для тренировки</string>
    <string name="aux_drill_progress">%1$d / %2$d пройдено</string>
    <string name="aux_drill_today">Сегодня: %1$d</string>
    <string name="aux_drill_all_done">На сегодня всё пройдено</string>
    <string name="aux_drill_start">Начать</string>
    <string name="aux_drill_content_desc_back">Назад</string>
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:processDebugResources` (Windows: `gradlew :app:processDebugResources`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings-app.xml app/src/main/res/values-ru/strings-app.xml
git commit -m "feat(aux-drill): add string resources (en/ru)"
```

---

## Task 8: AuxDrillScreen UI

**Files:**
- Create: `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillScreen.kt`

Two composable surfaces in one file: the menu (catalog grouped by category) and a minimal training card. Reuses the same input controls pattern as VerbDrillScreen for consistency.

- [ ] **Step 1: Write the screen**

Create `app/src/main/java/com/alexpo/grammermate/ui/AuxDrillScreen.kt`:

```kotlin
package com.alexpo.grammermate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexpo.grammermate.R
import com.alexpo.grammermate.data.AuxDrillCatalog
import com.alexpo.grammermate.data.AuxDrillPair

@Composable
fun AuxDrillScreen(
    viewModel: AuxDrillViewModel,
    onBack: () -> Unit,
    onStartTraining: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.session != null) {
        AuxDrillTrainingSurface(
            state = state,
            onSubmitCorrect = viewModel::submitCorrectAnswer,
            onSkip = viewModel::markCardCompleted,
            onExit = { viewModel.exitSession(); onBack() }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.aux_drill_content_desc_back))
            }
            Spacer(modifier = Modifier.height(0.dp))
            Text(text = stringResource(R.string.aux_drill_title), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.aux_drill_select_pair),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AuxDrillCatalog.GROUPED.forEach { (category, pairs) ->
                item {
                    Text(
                        text = category,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(pairs) { pair ->
                    AuxPairCard(
                        pair = pair,
                        selected = state.selectedPair == pair,
                        totalCards = if (state.selectedPair == pair) state.totalCards else 0,
                        everShown = if (state.selectedPair == pair) state.everShownCount else 0,
                        todayShown = if (state.selectedPair == pair) state.todayShownCount else 0,
                        onClick = { viewModel.selectPair(pair) },
                        onStart = {
                            viewModel.selectPair(pair)
                            viewModel.startSession()
                            onStartTraining()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuxPairCard(
    pair: AuxDrillPair,
    selected: Boolean,
    totalCards: Int,
    everShown: Int,
    todayShown: Int,
    onClick: () -> Unit,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${pair.verb} — ${pair.tense}", fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.aux_drill_leads_to, pair.lessonId, pair.lessonTopic), fontSize = 12.sp)
            }
            if (selected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.aux_drill_progress, everShown, totalCards), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(stringResource(R.string.aux_drill_today, todayShown), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (totalCards > 0) everShown.toFloat() / totalCards.toFloat() else 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.aux_drill_start))
                }
            }
        }
    }
}

@Composable
private fun AuxDrillTrainingSurface(
    state: com.alexpo.grammermate.data.AuxDrillUiState,
    onSubmitCorrect: () -> Unit,
    onSkip: () -> Unit,
    onExit: () -> Unit
) {
    val session = state.session ?: return
    val card = session.cards.getOrNull(session.currentIndex)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Text("Aux Drill  ${session.currentIndex + 1}/${session.cards.size}")
        }
        Spacer(modifier = Modifier.height(24.dp))
        card?.let {
            Text(text = it.promptRu, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it.answer, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(onClick = onSubmitCorrect, modifier = Modifier.weight(1f)) { Text("Correct") }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/AuxDrillScreen.kt
git commit -m "feat(aux-drill): add menu + training screen composables"
```

---

## Task 9: Wire route + entry tile

**Files:**
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`
- Modify: `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Add the route constant**

In `app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt`, in the `Routes` object (after `VOCAB_DRILL` line ~118):

```kotlin
    const val AUX_DRILL = "aux_drill"
```

- [ ] **Step 2: Add the composable block**

In `GrammarMateApp.kt`, after the `composable(Routes.VOCAB_DRILL) { ... }` block (around line 890), add:

```kotlin
                    composable(Routes.AUX_DRILL) {
                        val auxVm = viewModel<AuxDrillViewModel>()
                        val packId = state.navigation.activePackId
                        LaunchedEffect(packId, state.navigation.selectedLanguageId) {
                            if (packId != null) {
                                auxVm.reloadForPack(packId.value, state.navigation.selectedLanguageId?.value ?: "it")
                            }
                        }
                        val auxExit = remember(auxVm) {
                            {
                                auxVm.exitSession()
                                onNavigate(Routes.HOME)
                            }
                        }
                        BackHandler {
                            auxExit()
                        }
                        AuxDrillScreen(
                            viewModel = auxVm,
                            onBack = auxExit,
                            onStartTraining = { /* training shown inline in same screen */ }
                        )
                    }
```

- [ ] **Step 3: Add the entry tile on Home**

In `app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt`, in the `if (hasVerbDrill || hasVocabDrill) { Row(...) }` block (around line 330), wrap the existing two tiles and add a third. Replace the Row content:

Find (around line 330–358):
```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasVerbDrill) {
                    VerbDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=verb")
                            AuditLogger.getInstanceOrNull()?.verbDrillStart(0)
                            onOpenVerbDrill()
                        }
                    )
                }
                if (hasVocabDrill) {
                    VocabDrillEntryTile(
                        modifier = if (hasVerbDrill) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=vocab")
                            AuditLogger.getInstanceOrNull()?.vocabDrillStart("")
                            onOpenVocabDrill()
                        },
                        masteredCount = state.vocabSprint.vocabMasteredCount
                    )
                }
            }
```

Replace with (adds `onOpenAuxDrill` + a third tile):
```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasVerbDrill) {
                    VerbDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=verb")
                            AuditLogger.getInstanceOrNull()?.verbDrillStart(0)
                            onOpenVerbDrill()
                        }
                    )
                }
                if (hasVerbDrill) {
                    AuxDrillEntryTile(modifier = Modifier.weight(1f), onClick = onOpenAuxDrill)
                }
                if (hasVocabDrill) {
                    VocabDrillEntryTile(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ScreenLogger.tap("drill_start", details = "type=vocab")
                            AuditLogger.getInstanceOrNull()?.vocabDrillStart("")
                            onOpenVocabDrill()
                        },
                        masteredCount = state.vocabSprint.vocabMasteredCount
                    )
                }
            }
```

- [ ] **Step 4: Add AuxDrillEntryTile composable + the HomeScreen params**

At the end of `HomeScreen.kt`, add (next to `VerbDrillEntryTile`):

```kotlin
@Composable
fun AuxDrillEntryTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .testTag("aux_drill_entry_tile")
            .cardOutline(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.aux_drill_entry),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
```

Add the `onOpenAuxDrill` parameter to the `HomeScreen` composable signature (find the existing `onOpenVerbDrill` param and add `onOpenAuxDrill: () -> Unit = {}` next to it). Also add the call site: find where `onOpenVerbDrill = remember { { onNavigate(Routes.VERB_DRILL) } }` is set (around line 527) and add below it:
```kotlin
                                onOpenAuxDrill = remember { { onNavigate(Routes.AUX_DRILL) } },
```

Ensure imports in HomeScreen.kt include `clickable`, `testTag`, `Card`, `CardDefaults` (they are already present since `VerbDrillEntryTile` uses them).

- [ ] **Step 5: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If unresolved imports (clickable/testTag/cardOutline), they already exist via VerbDrillEntryTile — verify the new tile is in the same file scope.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt
git commit -m "feat(aux-drill): wire AUX_DRILL route + entry tile on Home"
```

---

## Task 10: Build + full test suite verification

**Files:** none (verification only)

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest` (Windows: `gradlew :app:testDebugUnitTest`)
Expected: BUILD SUCCESSFUL, all existing VerbDrill tests still pass (no regression) + new aux tests pass.

- [ ] **Step 2: Run lint + assemble debug**

Run: `./gradlew :app:assembleDebug` (Windows: `gradlew :app:assembleDebug`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify isolation — grep for forbidden cross-references**

Run (PowerShell/cmd). Confirm no aux class references VerbDrillViewModel/Store/ComboProgress:
```bash
grep -rn "VerbDrillViewModel\|VerbDrillStore\|VerbDrillComboProgress\|VerbDrillUiState" app/src/main/java/com/alexpo/grammermate/ui/AuxDrillViewModel.kt app/src/main/java/com/alexpo/grammermate/ui/AuxDrillScreen.kt app/src/main/java/com/alexpo/grammermate/data/AuxDrillStore.kt app/src/main/java/com/alexpo/grammermate/data/AuxDrillCard.kt
```
Expected: empty (no matches) — proves isolation from Verb Drill layer. The only allowed shared reference is `VerbDrillCsvParser` and `LessonStore.getVerbDrillFiles`, which are in different files and not searched here.

- [ ] **Step 4: Final commit if any formatting fixes needed**

```bash
git add -A
git commit -m "chore(aux-drill): build verification" --allow-empty
```

---

## Definition of Done (matches spec §10)

- [x] Тайл «Подводящие» появляется на Home при `hasVerbDrill` (Task 9).
- [x] Экран выбора показывает категории с парами и номерами уроков (Task 8).
- [x] Выбор пары запускает тренировку из карт пула по глаголу+времени (Tasks 5, 6, 8).
- [x] Прогресс в `aux_drill_progress.yaml`, не влияет на Verb Drill (Tasks 3, 4).
- [x] Существующий Verb Drill работает без изменений (Task 10 — full suite green).
- [x] Назад из тренировки возвращает на экран выбора/домой (Task 8, 9).
