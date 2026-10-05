# Навигация и сессии

Владелец: [GrammarMateApp](../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt), [Routes](../app/src/main/java/com/alexpo/grammermate/ui/navigation/Routes.kt), [BackHandlers](../app/src/main/java/com/alexpo/grammermate/ui/navigation/NavBackHandlers.kt).

| Вход | Результат |
|---|---|
| ClassicHOME selectlesson | selectLesson(id,activepack)→LESSON |
| ChapterHOME continue | selectChapter→CHAPTER_LESSONS→selectLesson→LESSON |
| ChapterHOME Back | clearActivePack, routeHOME остаётся |
| Сохранённое AppScreen ELITE/VOCAB | Parse → HOME; отдельных runtime destinations нет |
| TRAINING done DAILY | daily.onBlockComplete→popToDAILY |
| TRAINING done LESSON | chapters→CHAPTER_LESSONS, classic→LESSON |
| TRAINING done CHAPTER_LESSONS | popToCHAPTER_LESSONS |
| TRAINING done VERB/AUX | popTo returnTo; fallbackresetHOME |
| TRAINING Back VERB/AUX | persistSessionState, exitVerbDrillSession, popTo returnTo |
| TRAINING Back DAILY | cancelDailySession→resetHOME |
| TRAINING Back lesson/other | Exitdialog, не silentpop |
| Settings open/dismiss | pauseSession; dismiss живой TRAINING → resumeFromSettings. Та же карточка сохраняет draft; смена пакета/урока очищает предыдущий ввод |
| Ladder Back | popopener; resume толькоTRAINING+currentcard |
| StoryReader Back | stopNarration+clearReader+pop |
| Verb/Vocab exit | exitSession, refreshprogress/streak, resetHOME |

[AppDrillDestinations](../app/src/main/java/com/alexpo/grammermate/ui/AppDrillDestinations.kt) reload по pack/language и связывает deck→runner→returnTo. [AppStoryDestinations](../app/src/main/java/com/alexpo/grammermate/ui/AppStoryDestinations.kt) связывает chapterreader/list/progress. onNavigate избегает currentroute duplicate, resetStack/popTo — отдельные функции. NavigationEvent.Navigate/CompletionDialog/BossSessionFinished — одноразовый collector, не comparison tokens.

Verb Check передаёт идентичность показанной карточки владельцу persisted drill progress; Next/Prev синхронизируют курсор без shown/credit. Word Bank пересоздаётся при смене карточки вперёд и назад. Startup/restore читает selected lesson из progress metadata и counters/cursor из pack checkpoint; явный выбор другого sublesson имеет приоритет над resume.

[SessionNavigationTransitions](../app/src/main/java/com/alexpo/grammermate/feature/training/SessionNavigationTransitions.kt): advance очищает answer/result и ACTIVE; previous сохраняет текущий ACTIVE/PAUSED; browse/manual arrows→PAUSED; selectSubLesson index0/PAUSED, currentCard ждёт BuildSessionCards; skipEndPAUSED. Timer/audit/input/events остаются у SessionRunner. Сценарии не должны объединять все переходы в одну «Следующая».
