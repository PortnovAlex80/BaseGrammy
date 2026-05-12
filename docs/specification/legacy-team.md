# Team Coordination

## Tasks

### Agent A: Vocab System (Tasks 1, 5, 8)
- Add VocabProgressStore (persist vocab progress like DrillProgressStore)
- Add continue dialog to vocab sprint (fresh or continue)
- Import external Italian vocab CSVs into assets (drill_adjectives.csv etc)
- Build Anki-like spaced repetition for vocab (review overdue words first)
- Remove second Vocab from roadmap (buildRoadmapEntries)
- **Files touched**: new VocabProgressStore.kt, LessonStore.kt, TrainingViewModel.kt (vocab methods), GrammarMateApp.kt (vocab UI, buildRoadmapEntries)

### Agent B: Boss System (Tasks 4, 7)
- Mega reward stored per-lesson (Map<String, BossReward> like bossLessonRewards), not single global
- Boss unlock: only after completing >= 15 sub-lessons of current lesson
- **Files touched**: TrainingViewModel.kt (finishBoss, startBoss), GrammarMateApp.kt (BossTile, roadmap boss entry), Models.kt (TrainingProgress)

### Agent C: Roadmap Restructure (Task 6)
- Remove Story tiles completely (StoryCheckIn, StoryCheckOut, StoryPhase)
- Drill as additional tile, NOT replacing Story
- **Files touched**: GrammarMateApp.kt (buildRoadmapEntries, LessonRoadmapScreen, remove Story UI)

## External CSV files location
```
D:\Разработка\БазаАнглийскогоИтал\data\it\drill\
  drill_adjectives.csv (128 lines)
  drill_adverbs.csv (81 lines)
  drill_nouns.csv (2061 lines)
  drill_numbers.csv (150 lines)
  drill_pronouns.csv (143 lines)
  drill_verbs.csv (872 lines)
```
Format: `rank,word,collocations` (collocations = semicolon-separated phrases)
