# SRS BaseGrammy / GrammarMate

## Назначение

Android: выбор языка/пакета, дорожные карты уроков/глав, карточные тренировки, daily practice, истории/quiz, verb/vocab/aux drills, фоновое слушание, профиль/streak/mastery, настройки и backup прогресса. Вход [MainActivity](../app/src/main/java/com/alexpo/grammermate/MainActivity.kt) → [AppRoot](../app/src/main/java/com/alexpo/grammermate/ui/AppRoot.kt).

## Поток

RestoreNotifier DONE предшествует созданию TrainingViewModel: до него spinner preparing/restoring/waiting-user. HOME показывает classic либо chapter layout по active pack. Settings — modal sheet. Старые сохранённые ELITE/VOCAB читаются как HOME; отдельных destinations нет. STORY_READER/BACKGROUND_VOCAB имеют собственные Routes, enum tracking сводит их к STORY/HOME. Daily остаётся отключённым продуктовым входом; наличие его внутренних тестов не означает доступность режима пользователю.

Session runner общий для lesson и drill-like training. Cursor, answer/result, ACTIVE/PAUSED, timer/audio и navigation events имеют разных владельцев. Manual arrows не оценивают ответ, оставляют PAUSED. Back/done зависит от returnTo.

## Данные

Pack/language/lesson/chapter scope определяет cards/mastery/progress/streak/cursor. Hidden/bad cards, vocabulary, word/verb/aux state и профиль сохраняются локально. SAF imports lesson/pack/sound используют validators и warnings; no content/model/audio — отдельный отказ, не успешная пустая сессия.

Backup — managed progress snapshot, включая Pomodoro history и background marks/position; installed packs и config исключены. Все источники читаются и проверяются по поддерживаемым схемам до записи. Отсутствующий managed file сбрасывает старое значение только для подтверждённого полного современного snapshot; старые backup имеют совместимый путь восстановления. При exception выполняется rollback, который сам может отказать по I/O; это не транзакция всего приложения. После restore обновляются кеши и выбранный pack/lesson checkpoint, без начисления credit. Startup restore и permissions зависят от Android lifecycle.

## Audio boundaries

TTS/clip narration, offlineASR/modeldownload, Bluetooth mic/route и permissions разделены. Bluetooth CONNECT на API31+; deny выключает mic. Story предпочитает pre-rendered clip при наличии, иначе multilingualTTS. Качество/фон/device compatibility не проверены. Android app не публикует обнаруженный MCP/HTTP server; network downloads при этом существуют.

План исправлений, исполненные исходные проверки и итоговые регрессии связаны через REQ → UC → AC → UI/FN → source. [Журнал доказательств](regression-evidence.md) отличает воспроизведённые ошибки от статических рисков и проверки JVM от реального устройства. [Карта источников](source-map.md) фиксирует состав проверенной ревизии.
