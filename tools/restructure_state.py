#!/usr/bin/env python3
"""
Step 4.1: Restructure TrainingUiState from flat (86 fields) to nested (10 groups).

Works in three passes:
1. Replace the data class definitions in TrainingViewModel.kt
2. Transform all .copy() calls on TrainingUiState across all .kt files
3. Transform all field read accesses (state.field -> state.group.field)

IDEMPOTENT: Running twice produces the same result (detects already-transformed code).
"""

import re
import os
import sys
from pathlib import Path
from collections import defaultdict, OrderedDict

# ── Configuration ──────────────────────────────────────────────────────────────

BASE_DIR = Path(r"D:\Разработка\BaseGrammy")
APP_DIR = BASE_DIR / "app" / "src" / "main" / "java" / "com" / "alexpo" / "grammermate"
UI_DIR = APP_DIR / "ui"
VIEWMODEL_FILE = UI_DIR / "TrainingViewModel.kt"

SCAN_DIRS = [
    UI_DIR,
    UI_DIR / "helpers",
    UI_DIR / "screens",
    UI_DIR / "components",
]

FIELD_GROUPS = {
    'languages': 'navigation', 'installedPacks': 'navigation', 'selectedLanguageId': 'navigation',
    'activePackId': 'navigation', 'activePackLessonIds': 'navigation', 'lessons': 'navigation',
    'selectedLessonId': 'navigation', 'mode': 'navigation', 'userName': 'navigation',
    'ladderRows': 'navigation', 'initialScreen': 'navigation', 'currentScreen': 'navigation',
    'appVersion': 'navigation',
    'sessionState': 'cardSession', 'currentIndex': 'cardSession', 'currentCard': 'cardSession',
    'inputText': 'cardSession', 'correctCount': 'cardSession', 'incorrectCount': 'cardSession',
    'incorrectAttemptsForCard': 'cardSession', 'activeTimeMs': 'cardSession',
    'voiceActiveMs': 'cardSession', 'voiceWordCount': 'cardSession', 'hintCount': 'cardSession',
    'voicePromptStartMs': 'cardSession', 'answerText': 'cardSession', 'lastResult': 'cardSession',
    'lastRating': 'cardSession', 'inputMode': 'cardSession', 'voiceTriggerToken': 'cardSession',
    'subLessonTotal': 'cardSession', 'subLessonCount': 'cardSession',
    'subLessonTypes': 'cardSession', 'activeSubLessonIndex': 'cardSession',
    'completedSubLessonCount': 'cardSession', 'subLessonFinishedToken': 'cardSession',
    'wordBankWords': 'cardSession', 'selectedWords': 'cardSession', 'currentStreak': 'cardSession',
    'longestStreak': 'cardSession', 'streakMessage': 'cardSession',
    'streakCelebrationToken': 'cardSession', 'hintLevel': 'cardSession',
    'badSentenceCount': 'cardSession', 'testMode': 'cardSession', 'vocabSprintLimit': 'cardSession',
    'bossActive': 'boss', 'bossType': 'boss', 'bossTotal': 'boss', 'bossProgress': 'boss',
    'bossReward': 'boss', 'bossRewardMessage': 'boss', 'bossFinishedToken': 'boss',
    'bossLastType': 'boss', 'bossErrorMessage': 'boss', 'bossLessonRewards': 'boss',
    'bossMegaRewards': 'boss',
    'storyCheckInDone': 'story', 'storyCheckOutDone': 'story', 'activeStory': 'story',
    'storyErrorMessage': 'story',
    'currentVocab': 'vocabSprint', 'vocabInputText': 'vocabSprint', 'vocabAttempts': 'vocabSprint',
    'vocabAnswerText': 'vocabSprint', 'vocabIndex': 'vocabSprint', 'vocabTotal': 'vocabSprint',
    'vocabWordBankWords': 'vocabSprint', 'vocabFinishedToken': 'vocabSprint',
    'vocabErrorMessage': 'vocabSprint', 'vocabInputMode': 'vocabSprint',
    'vocabVoiceTriggerToken': 'vocabSprint', 'vocabMasteredCount': 'vocabSprint',
    'eliteActive': 'elite', 'eliteStepIndex': 'elite', 'eliteBestSpeeds': 'elite',
    'eliteFinishedToken': 'elite', 'eliteUnlocked': 'elite', 'eliteSizeMultiplier': 'elite',
    'isDrillMode': 'drill', 'drillCardIndex': 'drill', 'drillTotalCards': 'drill',
    'drillShowStartDialog': 'drill', 'drillHasProgress': 'drill',
    'lessonFlowers': 'flowerDisplay', 'currentLessonFlower': 'flowerDisplay',
    'currentLessonShownCount': 'flowerDisplay',
    'ttsState': 'audio', 'ttsDownloadState': 'audio', 'ttsModelReady': 'audio',
    'ttsMeteredNetwork': 'audio', 'bgTtsDownloading': 'audio',
    'bgTtsDownloadStates': 'audio', 'ttsModelsReady': 'audio', 'ttsSpeed': 'audio',
    'ruTextScale': 'audio', 'useOfflineAsr': 'audio', 'asrState': 'audio',
    'asrModelReady': 'audio', 'asrDownloadState': 'audio', 'asrMeteredNetwork': 'audio',
    'asrErrorMessage': 'audio', 'audioPermissionDenied': 'audio',
    'dailySession': 'daily', 'dailyCursor': 'daily',
}

GROUP_CLASS = {
    'navigation': 'NavigationState',
    'cardSession': 'CardSessionState',
    'boss': 'BossState',
    'story': 'StoryState',
    'vocabSprint': 'VocabSprintState',
    'elite': 'EliteState',
    'drill': 'DrillState',
    'flowerDisplay': 'FlowerDisplayState',
    'audio': 'AudioState',
    'daily': 'DailyPracticeState',
}

ALL_FIELDS = set(FIELD_GROUPS.keys())
ALL_GROUPS = set(GROUP_CLASS.keys())

# Known subjects that are NOT TrainingUiState (do not transform their copy calls)
NON_STATE_COPY_SUBJECTS = {
    'config', 'configStore.load()', 'mastery', 'cursor', 'currentCursor',
    'color', 'Color', 'surface', 'accent', 'profile',
}

# ── Nested class definitions ───────────────────────────────────────────────────

NESTED_CLASSES = '''data class NavigationState(
    val languages: List<com.alexpo.grammermate.data.Language> = emptyList(),
    val installedPacks: List<com.alexpo.grammermate.data.LessonPack> = emptyList(),
    val selectedLanguageId: String = "en",
    val activePackId: String? = null,
    val activePackLessonIds: List<String>? = null,
    val lessons: List<Lesson> = emptyList(),
    val selectedLessonId: String? = null,
    val mode: TrainingMode = TrainingMode.LESSON,
    val userName: String = "GrammarMateUser",
    val ladderRows: List<LessonLadderRow> = emptyList(),
    val initialScreen: String = "HOME",
    val currentScreen: String = "HOME",
    val appVersion: String = "1.5"
)

data class CardSessionState(
    val sessionState: SessionState = SessionState.ACTIVE,
    val currentIndex: Int = 0,
    val currentCard: SentenceCard? = null,
    val inputText: String = "",
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val incorrectAttemptsForCard: Int = 0,
    val activeTimeMs: Long = 0L,
    val voiceActiveMs: Long = 0L,
    val voiceWordCount: Int = 0,
    val hintCount: Int = 0,
    val voicePromptStartMs: Long? = null,
    val answerText: String? = null,
    val lastResult: Boolean? = null,
    val lastRating: Double? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val voiceTriggerToken: Int = 0,
    val subLessonTotal: Int = 0,
    val subLessonCount: Int = 0,
    val subLessonTypes: List<SubLessonType> = emptyList(),
    val activeSubLessonIndex: Int = 0,
    val completedSubLessonCount: Int = 0,
    val subLessonFinishedToken: Int = 0,
    val wordBankWords: List<String> = emptyList(),
    val selectedWords: List<String> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMessage: String? = null,
    val streakCelebrationToken: Int = 0,
    val hintLevel: com.alexpo.grammermate.data.HintLevel = com.alexpo.grammermate.data.HintLevel.EASY,
    val badSentenceCount: Int = 0,
    val testMode: Boolean = false,
    val vocabSprintLimit: Int = 20
)

data class BossState(
    val bossActive: Boolean = false,
    val bossType: BossType? = null,
    val bossTotal: Int = 0,
    val bossProgress: Int = 0,
    val bossReward: BossReward? = null,
    val bossRewardMessage: String? = null,
    val bossFinishedToken: Int = 0,
    val bossLastType: BossType? = null,
    val bossErrorMessage: String? = null,
    val bossLessonRewards: Map<String, BossReward> = emptyMap(),
    val bossMegaRewards: Map<String, BossReward> = emptyMap()
)

data class StoryState(
    val storyCheckInDone: Boolean = false,
    val storyCheckOutDone: Boolean = false,
    val activeStory: StoryQuiz? = null,
    val storyErrorMessage: String? = null
)

data class VocabSprintState(
    val currentVocab: VocabEntry? = null,
    val vocabInputText: String = "",
    val vocabAttempts: Int = 0,
    val vocabAnswerText: String? = null,
    val vocabIndex: Int = 0,
    val vocabTotal: Int = 0,
    val vocabWordBankWords: List<String> = emptyList(),
    val vocabFinishedToken: Int = 0,
    val vocabErrorMessage: String? = null,
    val vocabInputMode: InputMode = InputMode.VOICE,
    val vocabVoiceTriggerToken: Int = 0,
    val vocabMasteredCount: Int = 0
)

data class EliteState(
    val eliteActive: Boolean = false,
    val eliteStepIndex: Int = 0,
    val eliteBestSpeeds: List<Double> = emptyList(),
    val eliteFinishedToken: Int = 0,
    val eliteUnlocked: Boolean = false,
    val eliteSizeMultiplier: Double = 1.25
)

data class DrillState(
    val isDrillMode: Boolean = false,
    val drillCardIndex: Int = 0,
    val drillTotalCards: Int = 0,
    val drillShowStartDialog: Boolean = false,
    val drillHasProgress: Boolean = false
)

data class FlowerDisplayState(
    val lessonFlowers: Map<String, FlowerVisual> = emptyMap(),
    val currentLessonFlower: FlowerVisual? = null,
    val currentLessonShownCount: Int = 0
)

data class AudioState(
    val ttsState: TtsState = TtsState.IDLE,
    val ttsDownloadState: DownloadState = DownloadState.Idle,
    val ttsModelReady: Boolean = false,
    val ttsMeteredNetwork: Boolean = false,
    val bgTtsDownloading: Boolean = false,
    val bgTtsDownloadStates: Map<String, DownloadState> = emptyMap(),
    val ttsModelsReady: Map<String, Boolean> = emptyMap(),
    val ttsSpeed: Float = 1.0f,
    val ruTextScale: Float = 1.0f,
    val useOfflineAsr: Boolean = false,
    val asrState: AsrState = AsrState.IDLE,
    val asrModelReady: Boolean = false,
    val asrDownloadState: DownloadState = DownloadState.Idle,
    val asrMeteredNetwork: Boolean = false,
    val asrErrorMessage: String? = null,
    val audioPermissionDenied: Boolean = false
)

data class DailyPracticeState(
    val dailySession: DailySessionState = DailySessionState(),
    val dailyCursor: DailyCursorState = DailyCursorState()
)'''

NEW_TRAINING_UI_STATE = '''data class TrainingUiState(
    val navigation: NavigationState = NavigationState(),
    val cardSession: CardSessionState = CardSessionState(),
    val boss: BossState = BossState(),
    val story: StoryState = StoryState(),
    val vocabSprint: VocabSprintState = VocabSprintState(),
    val elite: EliteState = EliteState(),
    val drill: DrillState = DrillState(),
    val flowerDisplay: FlowerDisplayState = FlowerDisplayState(),
    val audio: AudioState = AudioState(),
    val daily: DailyPracticeState = DailyPracticeState()
) {
    /**
     * Reset all session-related state to defaults.
     * Used by selectLanguage, selectLesson, selectMode, importLessonPack,
     * addLanguage, and refreshLessons to clear stale training state.
     */
    fun resetSessionState(): TrainingUiState = copy(
        cardSession = CardSessionState(sessionState = SessionState.PAUSED),
        boss = BossState(),
        story = StoryState(),
        vocabSprint = VocabSprintState(),
        drill = DrillState()
    )

    /**
     * Full session reset including counters and timers.
     * Used when changing language or importing packs where all progress resets.
     */
    fun resetAllSessionState(): TrainingUiState = resetSessionState().copy(
        cardSession = CardSessionState(correctCount = 0, incorrectCount = 0, activeTimeMs = 0L, voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0, currentCard = null),
        elite = EliteState(eliteActive = false),
        boss = BossState(bossLessonRewards = emptyMap(), bossMegaRewards = emptyMap()),
        flowerDisplay = FlowerDisplayState(lessonFlowers = emptyMap(), currentLessonFlower = null)
    )
}'''

# ── Logging ────────────────────────────────────────────────────────────────────

log_entries = []
files_modified = 0
replacements_per_file = defaultdict(int)

def log(msg):
    log_entries.append(msg)
    print(msg)

def log_file(file_path, count):
    global files_modified
    if count > 0:
        files_modified += 1
        replacements_per_file[str(file_path)] = count

# ── Helpers ────────────────────────────────────────────────────────────────────

def find_matching_paren(text, start, open_ch='(', close_ch=')'):
    """Find the position of the closing delimiter matching the one at `start`."""
    depth = 0
    i = start
    in_string = False
    in_char = False
    while i < len(text):
        ch = text[i]
        if in_string:
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if ch == '\\':
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue
        if ch == '"':
            in_string = True
        elif ch == "'":
            in_char = True
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def extract_balanced_parens(text, start):
    """Extract text inside balanced parentheses starting at position `start` (which is '(').
    Returns (inner_text, end_pos) where end_pos is position of closing ')'.
    """
    end = find_matching_paren(text, start)
    if end == -1:
        return None, -1
    return text[start+1:end], end


def split_params(text):
    """Split comma-separated parameters respecting nested parens/braces/strings.

    Returns list of param text strings (trimmed).
    """
    params = []
    depth_paren = 0
    depth_brace = 0
    depth_bracket = 0
    in_string = False
    in_char = False
    current_start = 0
    i = 0

    while i < len(text):
        ch = text[i]
        if in_string:
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if ch == '\\':
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue

        if ch == '"':
            in_string = True
        elif ch == "'":
            in_char = True
        elif ch == '(':
            depth_paren += 1
        elif ch == ')':
            depth_paren -= 1
        elif ch == '{':
            depth_brace += 1
        elif ch == '}':
            depth_brace -= 1
        elif ch == '[':
            depth_bracket += 1
        elif ch == ']':
            depth_bracket -= 1
        elif ch == ',' and depth_paren == 0 and depth_brace == 0 and depth_bracket == 0:
            param = text[current_start:i].strip()
            if param:
                params.append(param)
            current_start = i + 1

        i += 1

    # Last param
    param = text[current_start:].strip()
    if param:
        params.append(param)

    return params


def get_param_name(param_text):
    """Extract the parameter name (before '=') from a named parameter."""
    # Find top-level '='
    depth = 0
    in_string = False
    in_char = False
    i = 0
    while i < len(param_text):
        ch = param_text[i]
        if in_string:
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if ch == '\\':
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue
        if ch == '"':
            in_string = True
        elif ch == "'":
            in_char = True
        elif ch in '({[':
            depth += 1
        elif ch in ')}]':
            depth -= 1
        elif ch == '=' and depth == 0:
            # Check for == or != or <= or >=
            if i + 1 < len(param_text) and param_text[i+1] == '=':
                i += 2
                continue
            if i > 0 and param_text[i-1] in '!<>':
                i += 1
                continue
            name = param_text[:i].strip()
            return name
        i += 1
    return None  # Positional parameter


# ── Step 1: Replace data class definitions ─────────────────────────────────────

def transform_viewmodel_class():
    """Replace the flat TrainingUiState with nested version in TrainingViewModel.kt."""
    log("=== Transforming TrainingViewModel.kt data class ===")

    content = VIEWMODEL_FILE.read_text(encoding='utf-8')

    # Check if already transformed
    if 'val navigation: NavigationState' in content:
        log("  Already transformed (skipping class replacement)")
        return True

    # Find the data class
    match = re.search(r'^data class TrainingUiState\(', content, re.MULTILINE)
    if not match:
        log("  ERROR: Could not find 'data class TrainingUiState('")
        return False

    start = match.start()

    # Find the end of the entire class (including inner functions)
    # The class starts with "data class TrainingUiState(" and the opening
    # paren is at match.end() - 1
    paren_pos = match.end() - 1

    # Find matching close paren
    paren_end = find_matching_paren(content, paren_pos)
    if paren_end == -1:
        log("  ERROR: Could not find matching paren")
        return False

    # Check if there's a body { ... }
    rest = content[paren_end+1:paren_end+10].lstrip()
    if rest.startswith('{'):
        brace_pos = content.index('{', paren_end + 1)
        brace_end = find_matching_paren(content, brace_pos, '{', '}')
        if brace_end == -1:
            log("  ERROR: Could not find matching brace")
            return False
        end = brace_end + 1
    else:
        end = paren_end + 1

    # Replace
    replacement = NESTED_CLASSES + '\n\n' + NEW_TRAINING_UI_STATE + '\n'
    content = content[:start] + replacement + content[end:]

    VIEWMODEL_FILE.write_text(content, encoding='utf-8')
    log("  Replaced TrainingUiState with nested version")
    log_file(VIEWMODEL_FILE, 1)
    return True


# ── Step 2: Transform copy() calls ─────────────────────────────────────────────

def find_copy_calls(content):
    """Find all .copy( positions in content.

    Returns list of (dot_pos, copy_start, open_paren_pos).
    """
    results = []
    pos = 0
    while True:
        idx = content.find('.copy(', pos)
        if idx == -1:
            break
        open_paren = idx + 5  # position of '('
        results.append((idx, idx + 5, open_paren))
        pos = idx + 6
    return results


def get_copy_subject(content, dot_pos):
    """Determine what variable precedes a .copy( call.

    Returns (subject_name, chain_before_copy) or (None, None).
    subject_name is the base variable (it, state, current, base, etc.)
    chain_before_copy is the full expression before .copy(
    """
    # Scan backwards from the dot to find the subject
    before = content[:dot_pos]

    # Check for chained calls like "it.resetSessionState().copy("
    # or "configStore.load().copy("
    # We want the initial subject variable and the full chain
    chain_match = re.search(
        r'(\w+)((?:\.\w+(?:\(\))?)*)$', before
    )

    if chain_match:
        return chain_match.group(1), chain_match.group(0)
    return None, None


# Subjects that are definitely NOT TrainingUiState
NON_STATE_SUBJECTS = frozenset({
    'config', 'mastery', 'cursor', 'currentCursor', 'color',
    'Color', 'surface', 'accent', 'profile', 'this',
    'downloadState', 'bgState', 'ready', 'readyMap',
    'updatedBgStates', 'updatedReady', 'stateMap', 'updated',
    'result', 'resetCursor', 'reward', 'configStore',
    'progress', 'lessonStore', 'pack', 'lesson', 'language',
    'streak', 'streakData', 'profile', 'profileStore', 'dailySession',
    'session', 'card', 'entry', 'vocabSession',
})

# Full chains that are NOT state copy calls
NON_STATE_CHAINS = frozenset({
    'configStore.load()', 'DailySessionState()', 'DailyCursorState()',
})

# Subjects that are definitely TrainingUiState
STATE_SUBJECTS = frozenset({
    'it', 'state', 'current', 'base',
})


def is_state_copy_call(content, dot_pos, subject, chain):
    """Check if this .copy() call is on a TrainingUiState object.

    Uses multiple heuristics to avoid false positives on non-state objects.
    """
    if subject is None:
        return False

    # Check non-state subjects
    if subject in NON_STATE_SUBJECTS:
        return False

    # Check non-state chains (e.g., configStore.load())
    if chain and chain in NON_STATE_CHAINS:
        return False

    # Check for constructor calls like DailySessionState().copy()
    # But NOT method calls like it.resetSessionState().copy()
    # Constructors have no dot before the class name
    if chain:
        # Match "ClassName()" at the END of the chain, where ClassName is NOT preceded by a dot
        # Chain is like "it.resetSessionState()" or "DailySessionState()"
        if re.search(r'(?<!\.)\w+State\(\)$|\w+Config\(\)$', chain):
            # Additional check: if the chain contains a dot, the last part is a method call
            # which could be resetSessionState() — allow it
            if '.' not in chain:
                return False
            # If chain has dots, check if the last call looks like a factory/constructor
            last_call = chain.rsplit('.', 1)[-1] if '.' in chain else chain
            # resetSessionState, resetAllSessionState are method calls that return TrainingUiState
            # Don't skip these
            if last_call.startswith('reset'):
                pass  # Allow
            elif re.match(r'\w+State\(\)$', last_call):
                return False

    # Known state subjects
    if subject in STATE_SUBJECTS:
        return True

    # For unknown subjects, check if the copy params contain any mapped fields
    # that haven't already been grouped
    open_paren = dot_pos + 5
    inner, end = extract_balanced_parens(content, open_paren)
    if inner is None:
        return False

    params = split_params(inner)
    has_unmapped_field = False
    all_already_grouped = True
    for p in params:
        name = get_param_name(p)
        if name and name in ALL_FIELDS:
            has_unmapped_field = True
            all_already_grouped = False
            break
        elif name and name in ALL_GROUPS:
            # Already grouped - skip, don't count as needing transformation
            continue
        else:
            all_already_grouped = False

    # Only treat as state copy if there are unmapped fields that need grouping
    if not has_unmapped_field:
        return False

    # Even if it has mapped fields, check for non-state patterns
    # e.g., "mastery.copy(correctCount = ...)" — mastery is in NON_STATE_SUBJECTS
    # Already checked above. If we got here, it's likely a state copy.
    log(f"  WARNING: Unknown subject '{subject}' with mapped fields at char {dot_pos}")
    return True


def transform_copy_params(params_text, subject, content_around=None):
    """Transform the parameters of a .copy() call.

    Groups parameters by their group and creates nested copy calls.

    Returns (new_params_text, was_transformed).
    """
    params = split_params(params_text)
    if not params:
        return params_text, False

    # Classify each param
    groups = OrderedDict()
    unmapped = []
    has_mapped = False

    for p in params:
        name = get_param_name(p)
        if name and name in FIELD_GROUPS:
            group = FIELD_GROUPS[name]
            if group not in groups:
                groups[group] = []
            groups[group].append(p)
            has_mapped = True
        elif name and name in ALL_GROUPS:
            # Already a group param (e.g., dailySession = ...)
            unmapped.append(p)
        else:
            unmapped.append(p)

    if not has_mapped:
        return params_text, False

    # Build new parameter list
    new_params = []

    for group, param_list in groups.items():
        inner = ', '.join(param_list)
        new_params.append(f'{group} = {subject}.{group}.copy({inner})')

    for p in unmapped:
        new_params.append(p)

    return ', '.join(new_params), True


def transform_all_copy_calls(content, file_path):
    """Find and transform all TrainingUiState .copy() calls."""
    replacements = 0
    offset = 0  # Track position shifts

    # Find all .copy( positions
    copy_calls = find_copy_calls(content)

    # Process from end to start to maintain positions
    for dot_pos, copy_kw_end, open_paren in reversed(copy_calls):
        subject, chain = get_copy_subject(content, dot_pos)

        if not is_state_copy_call(content, dot_pos, subject, chain):
            continue

        # Extract the full parameter list
        inner_text, close_paren = extract_balanced_parens(content, open_paren)
        if inner_text is None:
            continue

        # Transform params
        new_inner, was_transformed = transform_copy_params(inner_text, subject)
        if not was_transformed:
            continue

        # Replace the copy call
        old_call = content[dot_pos:close_paren + 1]
        new_call = f'.copy({new_inner})'

        content = content[:dot_pos] + new_call + content[close_paren + 1:]
        replacements += 1

        # Log
        # Get line number
        line_num = content[:dot_pos].count('\n') + 1
        log(f"  L~{line_num}: {subject}.copy({len(split_params(inner_text))} params) -> nested copy")

    return content, replacements


# ── Step 3: Transform field reads ──────────────────────────────────────────────

def transform_field_reads(content, file_path):
    """Transform state.fieldName -> state.group.fieldName.

    Only transforms when the subject is a known TrainingUiState variable:
    - Direct state vars: it, state, current, base (when used with updateState/copy)
    - uiState.value references: _uiState.value, uiState.value
    - Function parameters named 'state' of type TrainingUiState

    Does NOT transform fields on config, progress, profile, streakData, etc.
    """
    replacements = 0
    lines = content.split('\n')
    new_lines = []

    # Build a combined regex that matches ANY mapped field after a dot
    # Sort by length (longest first) to avoid partial matches
    sorted_fields = sorted(ALL_FIELDS, key=len, reverse=True)
    field_pattern = '|'.join(re.escape(f) for f in sorted_fields)

    # Pattern: subject.fieldName where subject is a known state variable
    # We match the SUBJECT + .fieldName to ensure we only transform the right references
    # Subject patterns:
    #   - "it." (inside updateState lambda)
    #   - "state." (parameter)
    #   - "current." (inside updateState lambda)
    #   - "base." (intermediate val)
    #   - "_uiState.value." (direct state access)
    #   - "uiState.value." (direct state access)
    #   - "stateAccess.uiState.value." (helper access)
    #   - followed by navigation/cardSession/etc group (already transformed, skip)
    subject_pattern = r'(?:it|state|current|base|_uiState\.value|uiState\.value|stateAccess\.uiState\.value)'

    combined_re = re.compile(
        r'(?<![a-zA-Z0-9_])(' + subject_pattern + r')' +
        r'\.(' + field_pattern + r')(?![a-zA-Z0-9_])'
    )

    for line_num_0, line in enumerate(lines):
        line_num = line_num_0 + 1

        # Skip import lines
        if line.strip().startswith('import '):
            new_lines.append(line)
            continue

        stripped = line.strip()

        def replacer(m):
            nonlocal replacements
            subject = m.group(1)
            field = m.group(2)
            group = FIELD_GROUPS[field]
            pos = m.start()
            prefix = line[:pos]

            # Check if already transformed (preceded by a group name)
            # The subject.field is now subject.group.field
            # Check if the text between subject and field includes a group
            # Actually, the regex only matches subject.field, so if it's already
            # subject.group.field, the match won't happen. But let's double-check
            # for edge cases.
            after_subject = line[m.end(1):m.start(2)]
            if '.navigation.' in after_subject or '.cardSession.' in after_subject or \
               '.boss.' in after_subject or '.story.' in after_subject or \
               '.vocabSprint.' in after_subject or '.elite.' in after_subject or \
               '.drill.' in after_subject or '.flowerDisplay.' in after_subject or \
               '.audio.' in after_subject or '.daily.' in after_subject:
                return m.group(0)  # Already has a group

            # Skip if inside a comment
            comment_idx = line.find('//')
            if comment_idx >= 0 and pos > comment_idx:
                return m.group(0)

            # Check if inside a string literal (but NOT inside Kotlin string interpolation ${})
            # In Kotlin, "...${expr}..." the ${expr} is a code expression.
            # We need to check if the match position is inside a ${...} block
            # rather than in a plain string context.
            if prefix.count('"') % 2 == 1:
                # We're between quotes. Check if we're inside a ${} interpolation.
                # Find the last unmatched ${ before our position
                in_interpolation = False
                i = len(prefix) - 1
                brace_depth = 0
                while i >= 0:
                    ch = prefix[i]
                    if ch == '}':
                        brace_depth += 1
                    elif ch == '{':
                        if brace_depth > 0:
                            brace_depth -= 1
                        elif i > 0 and prefix[i-1] == '$':
                            # Found ${ that isn't closed
                            in_interpolation = True
                            break
                    i -= 1
                if not in_interpolation:
                    return m.group(0)  # Inside a plain string, skip

            # Apply transformation: subject.field -> subject.group.field
            replacements += 1
            log(f"  L{line_num}: {subject}.{field} -> {subject}.{group}.{field}")
            return subject + '.' + group + '.' + field

        new_line = combined_re.sub(replacer, line)
        new_lines.append(new_line)

    return '\n'.join(new_lines), replacements


# ── Step 4: Handle self-references in copy values ──────────────────────────────

def transform_copy_value_refs(content, file_path):
    """Transform field references inside copy value expressions.

    After copy() transformation, value expressions may still reference
    the flat field (e.g., it.voiceTriggerToken instead of it.cardSession.voiceTriggerToken).

    This function finds such references that are INSIDE a group.copy() context
    and adds the group prefix.
    """
    replacements = 0

    # Use the same subject pattern as transform_field_reads
    subject_pat = r'(?:it|state|current|base)'
    sorted_fields = sorted(ALL_FIELDS, key=len, reverse=True)
    field_pattern = '|'.join(re.escape(f) for f in sorted_fields)

    # Combined pattern: subject.fieldName NOT already preceded by a group
    combined_re = re.compile(
        r'(?<![a-zA-Z0-9_.])(' + subject_pat + r')\.(' + field_pattern + r')(?![a-zA-Z0-9_])'
    )

    def replacer(m):
        nonlocal replacements
        subject = m.group(1)
        field = m.group(2)
        group = FIELD_GROUPS[field]
        pos = m.start()

        # Check if preceded by a group name (already transformed)
        prefix = content[:pos]
        for g in ALL_GROUPS:
            if prefix.rstrip().endswith('.' + g):
                return m.group(0)  # Already has group

        # Check if inside a string or comment
        line_start = content.rfind('\n', 0, pos) + 1
        line_before = content[line_start:pos]
        if line_before.count('"') % 2 == 1:
            return m.group(0)
        comment_idx = line_before.find('//')
        if comment_idx >= 0:
            return m.group(0)

        # Check context: is this inside a .group.copy(...) value expression?
        # Look for subject.group.copy( nearby before this position
        window = content[max(0, pos-300):pos]
        group_copy_pattern = re.escape(subject) + r'\.' + re.escape(group) + r'\.copy\('
        if re.search(group_copy_pattern, window):
            replacements += 1
            line_num = content[:pos].count('\n') + 1
            log(f"  L{line_num}: value ref {subject}.{field} -> {subject}.{group}.{field}")
            return subject + '.' + group + '.' + field

        return m.group(0)

    content = combined_re.sub(replacer, content)
    return content, replacements


# ── Main ───────────────────────────────────────────────────────────────────────

def collect_kt_files():
    """Collect all .kt files from scan directories."""
    files = []
    for d in SCAN_DIRS:
        if d.exists():
            for f in sorted(d.glob('*.kt')):
                files.append(f)
    return files


def file_needs_processing(content):
    """Check if a file references any TrainingUiState fields."""
    if 'TrainingUiState' in content:
        return True
    for field in ALL_FIELDS:
        if re.search(r'\.' + re.escape(field) + r'(?![a-zA-Z0-9_])', content):
            return True
    return False


def process_file(file_path):
    """Apply all transformations to a single file."""
    content = file_path.read_text(encoding='utf-8')
    original = content
    total = 0

    if not file_needs_processing(content):
        log(f"  SKIP: no state field references")
        return 0

    is_viewmodel = (file_path.name == 'TrainingViewModel.kt')

    # For TrainingViewModel.kt, skip the class definition itself (already handled)
    # but process everything else

    # Pass 1: Transform copy() calls
    content, copy_reps = transform_all_copy_calls(content, file_path)
    total += copy_reps
    if copy_reps:
        log(f"  Copy calls transformed: {copy_reps}")

    # Pass 2: Transform field reads
    content, read_reps = transform_field_reads(content, file_path)
    total += read_reps
    if read_reps:
        log(f"  Field reads transformed: {read_reps}")

    # Pass 3: Fix self-references in copy values
    content, ref_reps = transform_copy_value_refs(content, file_path)
    total += ref_reps
    if ref_reps:
        log(f"  Copy value refs fixed: {ref_reps}")

    if content != original:
        file_path.write_text(content, encoding='utf-8')
        log_file(file_path, total)

    return total


def main():
    global files_modified

    log("=" * 70)
    log("Step 4.1: Restructure TrainingUiState")
    log("=" * 70)

    # Step 1: Replace data class definitions
    if not transform_viewmodel_class():
        log("FAILED: Could not transform data class")
        sys.exit(1)

    # Step 2: Process all files
    kt_files = collect_kt_files()
    log(f"\n=== Processing {len(kt_files)} .kt files ===")

    for f in kt_files:
        log(f"\n--- {f.name} ---")
        reps = process_file(f)
        if reps > 0:
            log(f"  Total: {reps} transformations")

    # Summary
    log("\n" + "=" * 70)
    log("SUMMARY")
    log("=" * 70)
    log(f"Files modified: {files_modified}")
    log(f"Replacements per file:")
    for fp, count in sorted(replacements_per_file.items()):
        name = Path(fp).name
        log(f"  {name}: {count} transformations")

    log("\n=== Manual review needed for ===")
    log("1. configStore.load().copy(testMode = ...) — NOT TrainingUiState (should be untouched)")
    log("2. Chained: base.copy(...).copy(...) — second copy may need review")
    log("3. Complex when() branch copy calls")
    log("4. Any .copy() where params span groups but are mixed with existing group fields")

    return 0


if __name__ == '__main__':
    sys.exit(main())
