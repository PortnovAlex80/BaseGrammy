# Сценарии

| ID | Шаги | Результат / требование | REQ |
|---|---|---|---|
| UC-01 | Full data/no data/backup/SAFcancel/deniedstorage→status/DONE. | RestoreDONE доVM; permissions/SAF отказ не вечныйstartup. | REQ-01 |
| UC-02 | Ответить в A → выбрать B; из TRAINING открыть Settings → выбрать другой пакет → вернуться; save/restart/restore → продолжить. | Ввод очищается при смене контекста; настройки сохраняются; счётчики и checkpoint принадлежат выбранному уроку. | REQ-02 |
| UC-03 | Прочитать сохранённые ELITE/VOCAB; открыть действующие story/background destinations. | Старые значения переходят в HOME при чтении; мёртвых destinations нет. | REQ-03 |
| UC-04 | Locked/earlystart/boss/complete→dialog/progress/nextaction. | Lesson/sublesson/boss completion соблюдает access/progress. | REQ-04 |
| UC-05 | ACTIVE typed/Word Bank → Next → Prev; Verb Next → Play → Check → Exit → Continue. | PAUSED после ручного перехода, ввод очищен; банк и курсор соответствуют карточке; credit/shown только после выполнения. | REQ-05 |
| UC-06 | Wrong/correct/showanswer/skip/voicekeyboardwordbank→state. | Submit/hint/inputmode не смешивает showanswer и gradedcredit. | REQ-06 |
| UC-07 | Lesson Exit; dailycancelHOME; verbauxpopTo; done matchingroute. | Back/stop/done зависит returnTo и settingsmodal. | REQ-07 |
| UC-08 | Draft → Settings → dismiss той же сессии; повторить со сменой пакета; отдельно Home Settings и Ladder Back. | Та же карточка сохраняет draft; новая очищает; resume только живой TRAINING. | REQ-08 |
| UC-09 | Start/resume/repeat/blockcomplete/cancel→cursor/state. | Daily cursor/block/resume/repeat scoped по pack. | REQ-09 |
| UC-10 | Verb start → навигация → Check → Exit → Repeat/Continue/Reset; aux empty/nonempty start/return. | Verb сохраняет идентичность batch и ответов; навигация не завершает карточку; scoped progress и освоение сохраняются по контракту. | REQ-10 |
| UC-11 | Rate/exitmastery; backgroundmark/position/pause. | Vocab/background сохраняют ratings/marks/position и refresh. | REQ-11 |
| UC-12 | Missingstory/readplaypausestopback; quizclose vscomplete. | Reader/quiz различены; Back stops narration; phase/allCorrect вquiz. | REQ-12 |
| UC-13 | Установить v1 → попытаться установить v2 без CSV/с traversal/с ошибкой записи; затем корректный update. CSV import/create → список пакетов. | Отказ сохраняет v1; успешный импорт доступен через обычную навигацию; отмена SAF не меняет данные. | REQ-13 |
| UC-14 | Save → restart → restore; современные полные/повреждённые/неполные и старые snapshot; warmed caches/pending writer; два pack checkpoints. | Валидация предшествует записи; success только при завершении; старые форматы читаются; UI показывает восстановленный контекст без credit. | REQ-14 |
| UC-15 | Session3–20/nameblank-trim50/invalidlangsystem/themehintstestmode. | Settings соблюдают limits/name/UIlanguage whitelist. | REQ-15 |
| UC-16 | API31 grant/deny; model error/cancel; начать Italian download → выбрать English → завершить Italian; clip/TTS. | Ошибки явны; ready вычислен для текущего языка. | REQ-16 |
| UC-17 | Hide card_2 в пакете A → открыть другой пакет B; два урока и mixed review; flag/unflag/unhide/reset/restore; Verb/Aux/Vocab report sheet без фиктивного Hide. | Чужая карточка видима; скрытие и credit принадлежат действительному источнику. Старые неоднозначные hidden IDs не становятся глобальными фильтрами. | REQ-17 |
| UC-18 | Duration/history/profile/timer/pausebacksave. | Pomodoro/profile/streak — собственное state, не gradingcommand. | REQ-18 |
| UC-19 | Чистый Git checkout → stock wrapper → asset check → build/unit/lint/architecture gate; atomic overwrite на API 24/25. | Входы сборки отслеживаются; локальные обходы и неотслеживаемые ZIP не требуются. | REQ-19 |

Positive/cancel/refusal читать вместе с UI/FN и [приёмкой](acceptance.md). Фактические результаты отделены от описания сценариев в [регрессионных доказательствах](regression-evidence.md).
