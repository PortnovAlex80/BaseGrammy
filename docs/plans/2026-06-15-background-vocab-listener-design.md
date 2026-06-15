# Background Vocab Listener — Дизайн (MVP 50 слов)

**Дата:** 2026-06-15
**Статус:** утверждён (все ключевые развилки закрыты)
**Ветка реализации:** `feature/background-vocab-listener` (создать от `feature/vocab-progress-pack-scoped`)

---

## Цель и гипотеза

Пассивное фоновое заучивание итальянских слов: телефон в кармане, экран выключен, приложение свёрнуто — играет циклический «эхо-скрипт» на слово.

**Скрипт слова:** `слово(IT) → пауза → перевод(RU) → коллокация(IT) → пауза → перевод(RU) → 3-5 предложений(IT+RU каждый) → пауза`.

**Гипотеза (MVP):** чередующийся эхо-скрипт it↔ru помогает запоминать слова при пассивном прослушивании в фоне. Проверяем на 50 словах.

---

## Scope

**В MVP (50 слов):**
- TTS на лету — Sherpa-ONNX, существующие модели **Paola** (IT) + **Irina** (RU), уже скачаны.
- **True background**: `ForegroundService` + `MediaSession` + lock-screen notification.
- Скрипт на слово (см. выше), IT+RU на каждое предложение.
- Управление: play/pause, next-word/prev-word, seek по слову.
- Паузы через `{pause:N}` в разметке.
- Обе модели (IT+RU) **резидентно** → мгновенное переключение, без перезагрузок.

**Отложено (за рамками MVP):**
- Масштаб до 12500 слов (bulk-генерация контента).
- Ветка mp3-flag (архитектура заложена, имплементация `Mp3Segment` — позже).
- SRS-сортировка колоды.
- Счёт в mastery/цветок — **НЕ считается** (пассивный довесок, как WORD_BANK).

---

## Архитектура — 5 компонентов

```
┌──────────────────────────────────────────────────────────┐
│  VocabPlaybackService (ForegroundService)                │
│  ─ долгоживущий scope, переживает сворачивание/экран выкл│
│  ─ владеет DeckPlayer + MediaSession + notification      │
│                                                          │
│  DeckPlayer ──▶ playSegments(segments) ──▶ TtsEngine     │
│   wordIndex       (рефактор из                  ┌────────┴────────┐
│   next/prev       playMultilingualStory)        │ Map<lang,       │
│   seek                                              OfflineTts>   │
│                  ▲                                  IT+RU резид.   │
│                  │ {pause:N} → delay(ms)          ~300 МБ         │
│  WordScript ──▶ MultilingualStoryParser                         │
│   (размеченная                      ▲                            │
│    строка на слово)                 │                            │
└────────────────│─────────────────────┴───────────────────────────┘
                 │
   bg_vocab_50.csv (50 слов) ← pack asset
```

**Принцип:** одно слово = одна размеченная строка (существующий синтаксис `{lang}…{/lang}` + новый `{pause:N}`). Точечные правки в существующем коде; весь новый фон — в новом сервисе и DeckPlayer.

---

## S2. Данные

### Модель

```kotlin
data class PhrasePair(val it: String, val ru: String)

data class ScriptPauses(
    val afterWord: Long = 300,
    val afterTranslation: Long = 500,
    val afterCollocation: Long = 300,
    val afterSentence: Long = 400,
    val betweenWords: Long = 1000
)

data class WordScript(
    val rank: Int,
    val wordIt: String,
    val wordRu: String,
    val collocations: List<PhrasePair>,   // 1-2 шт.
    val sentences: List<PhrasePair>,      // 3-5 шт.
    val pauses: ScriptPauses = ScriptPauses()
) {
    fun toMarkup(): String  // → размеченная строка (см. ниже)
}
```

### Скрипт слова (`toMarkup()`)

```
{it}casa{/it}{pause:300}
{ru}дом, жилище{/ru}{pause:500}
{it}bella casa{/it}{pause:300}
{ru}красивый дом{/ru}{pause:500}
{it}Ogni sera torno a casa.{/it}{pause:400}
{ru}Каждый вечер я возвращаюсь домой.{/ru}{pause:1000}
```

### CSV `bg_vocab_50.csv`

Колонки: `rank, word, ru, collo_it, collo_ru, s1_it, s1_ru, s2_it, s2_ru, ..., s5_it, s5_ru`.

- `word`, `ru`, `collo_it` — берём из существующих `it_drill_*.csv` (топ-50 частотности уже покрыты: casa, tempo, vita, anno, parte, lavoro, …).
- `collo_ru`, `s{1-5}_it`, `s{1-5}_ru` — **генерируются офлайн** (LLM-пайплайн), кураторская проверка выборки.

### Декларация в манифесте пака (новый раздел, по аналогии с `vocabDrill`)

```json
"backgroundVocab": {
  "file": "bg_vocab_50.csv",
  "defaultLanguage": "it",
  "translationLanguage": "ru"
}
```

`PackImporter` копирует CSV в `grammarmate/drills/{packId}/bg_vocab/`; `DrillFileManager` резолвит по packId.

---

## S3. Рефактор TtsEngine — резидентная мапа моделей

**Сейчас:** `offlineTts: OfflineTts?` — один, ([TtsEngine.kt:76](../../app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt#L76)). `initialize(lang)` на [:124-130](../../app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt#L124-L130) принудительно выгружает другой язык через `doRelease()`.

**Меняем на:**
```kotlin
private val offlineTts = LinkedHashMap<String, OfflineTts>()  // LRU
private val maxResidentModels = 3
```
- `initialize(lang)` грузит в мапу **не выгружая** другие; при превышении `maxResidentModels` — евикт самый старый (с `free()`).
- `speak()` выбирает движок из мапы по языку.
- Убрать принудительный `doRelease()` другого языка на [:124-130](../../app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt#L124-L130).
- `activeLanguageId` → по сути `offlineTts.containsKey(lang)`.

**Эффект:** IT+RU резидентно (~300 МБ), переключение мгновенное. Бонус — ускоряет мультиязычные сторителлы. `System.gc()` на [:167](../../app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt#L167) — только при реальной загрузке нового.

---

## S4. Парсер `{pause:N}` + DeckPlayer

### Парсер

В [MultilingualStoryParser.kt:38](../../app/src/main/java/com/alexpo/grammermate/data/MultilingualStoryParser.kt#L38) добавить regex `\{pause:(\d+)\}`. Парсер эмитит `PauseSegment(ms)`. `TextSegment` → sealed `Segment { Text | Pause }` (или поле `pauseAfterMs` — на имплементации).

### playSegments()

Ядро цикла `playMultilingualStory` ([:256-312](../../app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt#L256-L312)) вынести в:
```kotlin
suspend fun playSegments(
    segments: List<Segment>,
    speed: Float,
    onSegment: (Int) -> Unit,
    isPaused: () -> Boolean
)
```
В цикле: `if (seg is Pause) delay(seg.ms) else speak(...)`. Сторителл-плейер начинает вызывать тот же `playSegments` (паузы просто игнорируются/применяются — обратно совместимо).

### DeckPlayer

```kotlin
class DeckPlayer(
    private val tts: TtsEngine,
    private val scope: CoroutineScope,
    private val onWordChange: (Int) -> Unit
) {
    private var words: List<WordScript>
    private var wordIndex = 0
    private var job: Job?
    fun start(words); fun pause(); fun resume()
    fun nextWord(); fun prevWord(); fun seekToWord(i: Int)
}
```
На слово → `playSegments(parse(word.toMarkup()), …)`; межсловная пауза `pauses.betweenWords`; `wordIndex` → MediaSession и UI.

---

## S5. ForegroundService + MediaSession + манифест

### VocabPlaybackService

- `class VocabPlaybackService : Service()` — `startForeground` с MediaStyle-уведомлением.
- Владеет `DeckPlayer`; берёт синглтон `TtsEngine` через `AppContainer` (нулевых правок движка для владения — только S3 для резидентности).
- `MediaSession` с колбэками: Play/Pause → `DeckPlayer.pause/resume`; Next/Prev → `nextWord/prevWord`; Stop → `stopForeground + cancel`.
- Долгоживущий scope: `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, **не** viewModelScope.

### AndroidManifest.xml ([сейчас:](../../app/src/main/AndroidManifest.xml) нет сервисов)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".service.VocabPlaybackService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="android.intent.action.MEDIA_BUTTON" />
    </intent-filter>
</service>
```

`POST_NOTIFICATIONS` — рантайм-запрос на Android 13+ (без него уведомление не покажется, но сервис работает).

---

## Data flow

```
bg_vocab_50.csv → LessonStore → List<WordScript>
  → DeckPlayer (в сервисе)
  → на слово: WordScript.toMarkup()
  → MultilingualStoryParser (с {pause:N}) → List<Segment>
  → playSegments() → TtsEngine[lang] (мапа) → AudioTrack
MediaSession → wordIndex (next/prev/seek) → DeckPlayer
UI-экран → bind к сервису → показывает текущее слово/позицию
```

---

## Волны сборки

| Wave | Содержимое | Чекпоинт |
|------|-----------|----------|
| **1. Движок** | S3 (резидентная мапа) + S4 (парсер pause + `playSegments`) | Сборка; сторителли не сломались; юнит-тест играет строку с паузами и переключает it↔ru без перезагрузки |
| **2. Данные** | `WordScript` + CSV-парсер + 50 слов контента | Тест парсит CSV → `toMarkup()` корректна |
| **3. Фон** | S5 сервис + MediaSession + манифест + уведомление | Играет при выключенном экране; управление с блокировки |
| **4. UI** | Экран входа (из дома пака) + bind к сервису | Полный юзер-путь |

---

## Журнал решений

| Развилка | Решение | Почему |
|----------|---------|--------|
| Контент | 50 слов из частотного, MVP | Проверить гипотезу на малой партии |
| Движок | на существующем, TTS на лету | Модели уже скачаны; переиспользуем TtsEngine |
| Паузы | `{pause:N}` в разметке | Гибко (разные длительности), соответствует философии разметки |
| Фон | ForegroundService + MediaSession | True-background (экран выкл) требует сервис; TtsEngine фон-совместим |
| Чередование it↔ru | резидентная мапа моделей | Тестирует исходный чередующийся скрипт; бонус — сторителли |
| Mastery | НЕ считается | Только VOICE/KEYBOARD растят цветок; пассивный режим — довесок |
| Контент 50 слов | генерируется офлайн (LLM) | 50×5≈250 предложений — разовая генерация, кураторская проверка |
| Перевод предложений | IT + RU на каждое | Полное понимание в фоне |

---

## Риски

- **~300 МБ RAM** на 2 резидентные модели → OOM на старых устройствах. Снижение: LRU-евикция (пауза вернётся только при выдавливании).
- **POST_NOTIFICATIONS** на Android 13+ — рантайм-запрос.
- **Doze / kill** — ForegroundService mediaPlayback даёт максимум свободы; WAKE_LOCK удерживает CPU.
- **Качество TTS** на скорости — возможно per-сегментная скорость (`speak()` уже принимает `speed`): слова медленнее, предложения быстрее.

---

## Будущее (за рамками MVP)

- **12500 слов:** bulk-генерация контента (collo_ru + предложения) офлайн-пайплайном → enriched CSV → тот же движок.
- **mp3-flag:** `AudioSegment` интерфейс с `TtsSegment` / `Mp3Segment`; для pre-rendered паков — Media3 ExoPlayer, для остальных — TTS.
- **SRS-сортировка** колоды по `WordMasteryStore`.
- **Контент-генерация в приложении** (lazy, с кешом) — если решим расти без офлайн-пайплайна.

---

## Ссылки на код

- TTS движок: [TtsEngine.kt](../../app/src/main/java/com/alexpo/grammermate/data/TtsEngine.kt) (один `offlineTts` :76, `initialize` :119, speak :340, AudioTrack :440-477)
- Синглтон: [TtsProvider.kt](../../app/src/main/java/com/alexpo/grammermate/data/TtsProvider.kt), [AppContainer.kt:26](../../app/src/main/java/com/alexpo/grammermate/AppContainer.kt#L26)
- Разметка: [MultilingualStoryParser.kt:38](../../app/src/main/java/com/alexpo/grammermate/data/MultilingualStoryParser.kt#L38), парс :131-212
- Плейер: [AudioCoordinator.kt](../../app/src/main/java/com/alexpo/grammermate/shared/audio/AudioCoordinator.kt) (`playMultilingualStory` :242-325, per-VM конструктор :39-42)
- Слова: [VocabWord.kt](../../app/src/main/java/com/alexpo/grammermate/data/VocabWord.kt), [it_drill_nouns.csv](../../app/src/main/assets/grammarmate/packs/it_drill_nouns.csv) (rank,noun,collocations,ru), частотник `docs/lesson-methodology/italian/it_12500_frequency.txt`
- Паки: [LessonPackManifest.kt](../../app/src/main/java/com/alexpo/grammermate/data/LessonPackManifest.kt), [PackImporter.kt](../../app/src/main/java/com/alexpo/grammermate/data/PackImporter.kt)
- Манифест: [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml) (нет сервисов, не хватает FOREGROUND_SERVICE/WAKE_LOCK/POST_NOTIFICATIONS)
