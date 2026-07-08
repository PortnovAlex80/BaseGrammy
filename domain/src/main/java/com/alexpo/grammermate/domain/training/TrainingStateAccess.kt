package com.alexpo.grammermate.domain.training

import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.model.SessionState
import com.alexpo.grammermate.domain.model.TrainingMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-cutting доменный порт для чтения/записи разделяемого training state.
 *
 * **AC-18 / GAP C2 (HIGH, blocking Wave 1).** Это successor legacy-интерфейса
 * `TrainingStateAccess` из `feature/daily/DailySessionHelper.kt`, поднятый с
 * feature-слоя в `:domain` как стабильный cross-cutting контракт. Потребляется
 * шестью downstream-эпиками Wave 1–4 (Training E04, Daily E07, BackgroundVocab
 * E08, Gamification E10, Boss E11, Pomodoro E12) **через DI**, а НЕ через прямые
 * ссылки на `TrainingViewModel`. Реализацию предоставляет корневой composition
 * (`AppContainer`/ViewModel-слой в E01/E13); фичи видят только этот интерфейс.
 *
 * ### Почему доменный, а не ViewModel-access
 *
 * В legacy порт был завязан на god-state `TrainingUiState` (40+ полей: navigation
 * + cardSession + boss + story + vocabSprint + daily + pomodoro …) и реализовывался
 * анонимным объектом внутри `TrainingViewModel`. Это делало все фичи неявно
 * зависимыми от ViewModel и от единого мутабельного UI-агрегата — главная
 * tech-debt, мешающая разнести фичи по модулям.
 *
 * Доменный successor [TrainingStateAccess] оперирует **только** тем срезом
 * состояния, который действительно разделяется между фичами cross-cutting:
 *
 *  - **Контекст навигации** ([TrainingState.navigation]) — какой пак/язык/урок/
 *    режим сейчас активен. Нужен Daily/Boss/Pomodoro/Progress, чтобы знать, с
 *    чем работаетает пользователь, не дёргая ViewModel напрямую.
 *  - **Сессионные счётчики** ([TrainingState.session]) — correct/incorrect/hint,
 *    индекс карточки, активное время. Их читает Progress (для persist), Boss
 *    (для наград/порогов), Pomodoro (baseline счётчиков) и Daily.
 *
 * Feature-local срезы состояния (boss/daily/pomodoro/vocab/story) в новой
 * архитектуре **владеются фичами локально** (E04/E07/E10/E11/E12) и НЕ входят в
 * этот cross-cutting порт — это и есть исправление legacy god-state.
 *
 * ### Стабильность контракта
 *
 * Сигнатуры ниже — канонический контракт, фиксируемый в SRS-001 §5.11 до старта
 * Wave 1 (NFR-4). Любое изменение (добавление/удаление метода, смена типа,
 * `suspend`/`StateFlow`) после старта Wave 1 = **drift** и требует отдельной
 * задачи с `trace_add(link_type='derived_from', target=SRS-001)`. Параллельные
 * dev-задачи Wave 1–4 **потребляют** этот порт, не меняют его.
 *
 * ### Чистота домена
 *
 * Pure JVM Kotlin: нет Android, нет `ViewModel`, нет Room/Sql/Hilt. Единственная
 * внешняя зависимость — `kotlinx.coroutines.flow.StateFlow` (как и у остальных
 * портов, возвращающих реактивные потоки). Чтение состояния синхронно через
 * `.value` (hot-источник, всегда есть текущее значение); persist — `suspend`
 * (правило SRS §5: все mutating/persist-методы портов — `suspend`).
 *
 * @see TrainingState
 */
interface TrainingStateAccess {

    /**
     * Hot-источник текущего разделяемого training state.
     *
     * Мигрирует legacy `val uiState: StateFlow<TrainingUiState>`, но тип сужен
     * до доменного cross-cutting среза [TrainingState] (вместо UI god-state).
     * Подписчики (feature-модули) читают `.value` для синхронного снимка либо
     * `.collect` для реактивных обновлений. Реализация гарантирует непустое
     * начальное значение ([TrainingState.Empty]).
     */
    val trainingState: StateFlow<TrainingState>

    /**
     * Атомарно обновить разделяемый training state через чистую [transform].
     *
     * Мигрирует legacy `fun updateState(transform: (TrainingUiState) -> TrainingUiState)`.
     * Реализация применяет [transform] к текущему значению под своим锁ом (та же
     * семантика, что и `MutableStateFlow.update` / `compareAndSet`-цикл в legacy
     * `TrainingViewModel`), гарантируя, что конкурирующие обновления от разных
     * фич не теряются. [transform] — чистая функция над [TrainingState]; side
     * -effects (persist, навигация) выполняются реализацией, а не callers.
     *
     * @param transform функция «текущее состояние → новое состояние».
     */
    fun updateState(transform: (TrainingState) -> TrainingState)

    /**
     * Персистировать текущий training state (счётчики, курсор, контекст) в
     * data-слой.
     *
     * Мигрирует legacy `fun saveProgress()` (в домене стал `suspend` по правилу
     * SRS §5: mutating/persist-операции порта — `suspend`, чтобы реализация
     * могла писать в Room/DataStore без блокировки потока UI). Фичи вызывают
     * это в контрольных точках (завершение подурока, ответ, пауза помодоро),
     * как и в legacy.
     */
    suspend fun saveProgress()
}

/**
 * Доменный cross-cutting срез training state — то, что действительно разделяется
 * между feature-модулями E04/E07/E08/E10/E11/E12.
 *
 * Сужает legacy god-state `TrainingUiState` до двух ответствённостей:
 *
 *  - [navigation] — контекст текущей тренировки (язык/пак/урок/режим/экран);
 *  - [session] — сессионные счётчики и курсор карточки.
 *
 * Feature-local подсостояния (boss-battle, daily-practice, pomodoro-timer,
 * vocab-sprint, story-reader) сюда **не входят** — ими владеют соответствующие
 * фичи локально в Wave 1+. Это устраняет legacy-связанность всех фич через
 * единый мутабельный агрегат.
 *
 * Значения иммутабельны (`data class` с `val`); изменения идут только через
 * [TrainingStateAccess.updateState], возвращающего новый [TrainingState].
 *
 * @property navigation контекст навигации текущей тренировки.
 * @property session     сессионные счётчики и курсор карточки.
 */
data class TrainingState(
    val navigation: TrainingNavigation = TrainingNavigation(),
    val session: TrainingSessionCounters = TrainingSessionCounters(),
) {
    companion object {
        /** Начальное пустое состояние: ничего не выбрано, счётчики нулевые. */
        val Empty: TrainingState = TrainingState()
    }
}

/**
 * Контекст навигации текущей тренировки — cross-cutting срез, читаемый всеми
 * feature-модулями, чтобы знать, с каким паком/языком/уроком работает пользователь.
 *
 * Мигрирует поля legacy `NavigationState`, действительно используемые downstream
 * -эпиками (Daily/Boss/Pomodoro/Progress читают `activePackId`, `selectedLessonId`,
 * `selectedLanguageId`, `mode`, `currentScreen`). UI-поля навигации (списки
 * языков/паков, ladder, тема, appVersion, welcomeDialogAttempts) остаются во
 * view/ui-слое и сюда не входят — это не cross-cutting доменный контракт.
 *
 * @property activePackId      активный пак тренировки (null, если пак не выбран).
 * @property selectedLanguageId выбранный язык обучения (null на старте).
 * @property selectedLessonId  выбранный урок (null для drill/daily/помодоро).
 * @property mode              режим тренировки текущего пака.
 * @property currentScreen     логический экран/маршрут (для persist-контекста).
 */
data class TrainingNavigation(
    val activePackId: PackId? = null,
    val selectedLanguageId: LanguageId? = null,
    val selectedLessonId: LessonId? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
    val currentScreen: String = "HOME",
)

/**
 * Сессионные счётчики и курсор карточки — cross-cutting срез, читаемый Progress
 * (для persist), Boss (для порогов/наград), Pomodoro (baseline) и Daily.
 *
 * Мигрирует поля legacy `CardSessionState`, действительно используемые
 * downstream-эпиками. UI-специфичные поля (inputText, answerText, lastResult,
 * wordBank, streak-сообщения, hintLevel, voicePromptStartMs и т.д.) остаются во
 * view-feature-слое и сюда не входят — это не cross-cutting доменный контракт.
 *
 * @property currentIndex        индекс текущей карточки в пуле сессии.
 * @property sessionState        состояние шага сессии (ACTIVE / HINT_SHOWN).
 * @property correctCount        счётчик правильных ответов.
 * @property incorrectCount      счётчик неправильных ответов.
 * @property incorrectAttemptsForCard число неверных попыток по текущей карточке.
 * @property hintCount           сколько раз показывали подсказку.
 * @property activeTimeMs        накопленное активное время тренировки (эпоха-мс).
 * @property voiceActiveMs       накопленное время голосового ввода (эпоха-мс).
 * @property voiceWordCount      распознанных слов голосового ввода.
 * @property completedSubLessonCount сколько подуроков завершено в текущем уроке.
 */
data class TrainingSessionCounters(
    val currentIndex: Int = 0,
    val sessionState: SessionState = SessionState.ACTIVE,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val hintCount: Int = 0,
    val activeTimeMs: Long = 0L,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val completedSubLessonCount: Int = 0,
)
