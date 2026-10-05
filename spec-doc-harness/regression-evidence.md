# Регрессии архитектурного ревью

План: [исправления от 2026-10-05](../docs/plans/2026-10-05-architecture-review-remediation.md). Исходный аудит: [архитектурное ревью](../docs/architecture/ARCHITECTURE_REVIEW_2026-10-05.md). Это журнал доказательств, а не отметка полной аппаратной приёмки.

## Исходная ревизия

`a0cf2d866b5bcd345ffafe4761065330134c3ee3`: `assembleDebug` PASS через Windows classpath; 679 unit tests / 117 suite PASS, 0 failures/errors/skips. `lintDebug` FAIL: 6 NewApi errors в AtomicFileWriter, 175 warnings, 5 informational. Штатный `bash ./gradlew --version` FAIL: `NoClassDefFoundError: org/gradle/wrapper/IDownload`. Архитектурный guard: 238 production-файлов, 3 self-tests PASS.

12 дополнительных тестов / 6 suite завершились 12 падениями на ожидаемых assertions, без ошибок настройки и пропусков. Они запускались до изменения production через отдельный Gradle init script. Исходные XML сохранены локально в `build/reports/review-2026-10-05/{baseline-unit-results,probe-unit-results}`; [probe-summary](../build/reports/review-2026-10-05/probe-summary.txt) содержит точные сообщения. Каталог build не является переносимым архивом CI.

| Сценарий | REQ / UC / AC | Исходное доказательство | Итоговая проверка |
|---|---|---|---|
| Manifest `../review-victim` не удаляет соседний каталог | 13 | FAIL: импорт принят, sentinel удалён | Ожидается |
| Update без обязательного CSV сохраняет прежний пакет | 13 | FAIL: прежний lesson file удалён | Ожидается |
| `mastery.yaml` с `unrelated: value` отклоняется до удаления прогресса | 14 | FAIL: restore успешен, hidden file удалён | Ожидается |
| Backup содержит Pomodoro history и background marks/position | 11, 14, 18 | FAIL: history отсутствует | Ожидается |
| Второе автоматическое окно mastery сохраняет новую запись | 18 | FAIL: вторая запись отсутствует | Ожидается |
| Prev пересоздаёт Word Bank для показанной карточки | 05, 06 | FAIL: prompt alpha, bank beta | Ожидается |
| Автоматический переход пересоздаёт Word Bank | 05, 06 | FAIL: prompt beta, bank alpha; низкоуровневый Daily Translate, продуктовый вход отключён | Ожидается |
| Завершение Italian TTS download после выбора English не ставит English ready | 16 | FAIL: ready=true для неготового языка | Ожидается |
| После ответа в уроке A новый урок B не наследует correctCount | 02, 04 | FAIL: 1 вместо 0 | Ожидается |
| Settings → другой пакет → возврат очищает старый draft | 02, 08 | FAIL: прежний текст сохранён | Ожидается |
| Возврат к выбору пакета сохраняет HARD | 02, 15 | FAIL: EASY | Ожидается |
| Verb UI Next → Play → Check записывает показанную карточку | 05, 06, 10 | FAIL: persisted card A при ответе на B | Ожидается |

## Дополнительные границы регрессии

Для готовности исправлений также проверяются: ошибка в середине публикации пакета и rollback; реальные старые/новые backup, неполный manifest и пустые snapshots; два независимых pack checkpoints после restart/restore; scoped/full auxiliary reset; CSV import и пустой draft в pack navigation; Verb Prev/Repeat/Continue/Reset через UI; Story toolbar Back после начала playback; чтение старых ELITE/VOCAB; штатный wrapper и assets в чистом checkout; AtomicFileWriter на API 24/25.

Для AtomicFileWriter Robolectric проверяет выбор допустимой API-ветки на 24/25/26 и создание файла на API 24. Перезапись и сохранность прежнего файла при ошибке проверяются отдельно на host JVM. Это не исполнение atomic replacement на настоящем устройстве с API 24/25.

При независимом просмотре исправлений найдены дополнительные границы. Они не включены в число 12 исходных отрицательных проверок:

Коллизия скрытия отдельно воспроизведена до добавления runtime-происхождения: `HiddenScopeReviewReproTest` завершился одним ожидаемым падением на реальных EN_WORD_ORDER_A1 и EN_WORD_ORDER_A1_DRILLS, без ошибок и пропусков. XML сохранён в `build/reports/review-2026-10-05/hidden-probe-unit-results`. Исправление сохраняет старые непривязанные hidden/report записи и прежнюю mastery history; оно предотвращает новые неверные записи, но не восстанавливает отсутствующий исторический контекст.

| Контракт | REQ / UC / AC | Проверка |
|---|---|---|
| CSV row ID не является глобальной идентичностью скрытия или источником mixed credit | 04, 06, 17 | Реальные EN_WORD_ORDER_A1 и EN_WORD_ORDER_A1_DRILLS имеют разные предложения с card_2; Hide A не скрывает B; смешанное повторение пишет освоение исходному уроку |
| Неподдерживаемое Hide не обещает изменения | 10, 11, 17 | Vocab callback прежде только закрывал sheet, Verb/aux только пропускал карточку без фильтрации будущих сессий; отсутствующий handler не создаёт кнопку Hide. Реальные Start → Report click tests проверяют отсутствие действия; sentence Hide сохраняется |
| Объявленный вложенный grammarChip разрешается и обновляется после импорта | 04, 13 | Загрузка реального EN_ADV path; повторный импорт заменяет уже закешированный chip |
| Force reload встроенного пакета использует ту же безопасную установку | 13 | Ошибка импорта сохраняет старую установку; нет предварительного удаления |
| Объявленные verb/vocab/background CSV валидируются; удалённый из manifest drill CSV не попадает в новый deck | 13 | Валидный update и update с повреждённым обязательным содержимым |
| Старые standalone CSV становятся видимыми пользовательскими пакетами | 02, 13 | Узкая миграция индексированных UUID-уроков; исходные lesson/card IDs сохранены; повтор не дублирует пакеты |
| Повторный CSV import и delete соблюдают lifecycle | 13 | То же название, одинаковый pack/lesson ID, перезагрузка, удаление draft |
| Частично прогретый pack cache не подменяет полный inventory | 14, 17 | Read A → loadAll возвращает A и B |
| Reset инвалидирует уже выданные объекты хранилищ | 10, 11, 17 | Warm verb/aux/word/vocab → reset/evict → старые ссылки читают новые файлы; другой pack сохранён |
| Auxiliary training не пишет в обычный VerbDrill | 05, 10 | Совпадающий ID карточки не изменяет regular verb progress/session |
| Обновление пакета с тем же ID обновляет drill UI | 02, 10, 11, 13 | Реальные store + VM reload видят новые verb/vocab decks; устаревший результат загрузки не подменяет новую selection; изменённый каталог очищает старую живую сессию, Repeat использует новые объекты по сохранённым IDs |
| Drill/review не перезаписывают checkpoint обычного урока | 02, 04, 10, 14 | Lesson checkpoint → drill/exit → restart/restore сохраняет позицию урока |
| Изменение reader content/pack обновляет Play callback | 12, 16 | Recompose → Play передаёт актуальный текст/файл/pack |
| Reset сохраняет только постоянные предпочтения | 02, 08, 15 | Hint/test mode сохранены; session input mode получает обычный default; same-card pause/resume сохраняет ввод и mode |

Конкретные постоянные test classes, результаты и ревизия будут записаны после выполнения. Наличие исправленного исходника не означает PASS. Обширные AC-01…18 остаются открытыми в части cancel/refusal, устройства и реального аудио, пока нет соответствующего доказательства.

## Что обнаружили промежуточные проверки

Первый полный прогон исправлений завершился 726 тестами и четырьмя падениями. Два теста реальных backup-сериализаторов обнаружили ошибку нового валидатора: строковое поле `mode` в жалобе ошибочно проверялось как enum режима обучения. Проверка enum теперь относится только к `progress.yaml`; настоящие сериализованные жалобы проходят roundtrip. Две другие ошибки относились к подготовке тестового checkpoint через обход живого кеша и к выбору элемента Settings. Проверки содержимого восстановленных данных и итогового состояния сохранены.

Robolectric-проверки новых report actions выявили ограничение доставки pointer-событий в отдельное окно sheet. В них конкретные видимые и включённые UI controls активируются через `SemanticsActions.OnClick`, закрытие Settings — через реальное UI-действие `Dismiss`. Обработчики ViewModel напрямую не вызываются вместо проверяемого пользовательского действия. Пункт выбора пакета ограничен настоящим popup: такое же имя присутствует в информационном списке. Сразу после действия проверяется изменение хранилища или состояния. Эти проверки связывания UI и данных отделены от [Android touch-фикстуры](../app/src/androidTest/java/com/alexpo/grammermate/ui/ModalTouchRegressionTest.kt), где остаются `performClick` и системный `Espresso.pressBack`.

Android touch-фикстура использует настоящие `TrainingScreenContent`, `AppSettingsOverlay` и ViewModel в `ComponentActivity`. Это проверка компонентов и окон Android; она не считается маршрутом через `MainActivity`. Для маршрутов остаётся отдельный production-entry smoke test. Фикстура создаёт уникальные pack/lesson IDs и восстанавливает прежние progress/config после завершения принадлежащих ей ViewModel и писателей.

Следующий полный прогон содержал 737 тестов: 735 PASS, два таймаута в `HiddenCardScopeUiRegressionTest.setup` при ожидании `!isLoading`; тела этих тестов не начинались. APK/test APK и lint прошли, lint содержал 0 errors, 175 warnings, 5 informational. Часть запуска совпала с загрузкой AVD, после снижения доступной памяти AVD был остановлен. Фикстура теперь готовит настоящие встроенные пакеты до ViewModel и освобождает принадлежащий ей `ViewModelStore` перед восстановлением данных. Таймаут 60 секунд и проверки действий не увеличены и не ослаблены. Итоговые unit и device gates выполняются последовательно; исходные XML этой попытки сохранены отдельно в `full-with-avd-unit-results`.

Адресный повтор после этой подготовки дал один PASS и один timeout уже при выборе тестового пакета. Это отдельная ошибка введённого lifecycle harness: стандартный `ViewModelProvider` использовал singleton `AndroidViewModelFactory`, удерживающий Application первого Robolectric-метода. В фикстуре теперь создаётся явная фабрика для текущего Application, проверяется `assertSame` и при неудаче выводится фактический контекст. Исходные loading timeouts этим механизмом не объясняются: тогда ViewModel создавался напрямую. Результат неудачного адресного прогона сохранён в `focused-hidden-lifecycle-unit-results`; production-код ради него не менялся.

## Android-проверка обнаружила path regression

Перед устройством свежая JVM suite дала 737 тестов, 132 suite и 0 failures/errors/skips; debug APK, test APK и lint также прошли на том коде. При чистой API 36 установке приложение не показывало доступные языки. В первом устройстве APK-install был заблокирован нехваткой места; после подготовки отдельного AVD с достаточным хранилищем инструментальные тесты запустились. Четыре native cases остановились до проверок действий: два — до выбора языка/Italian pack, два — при импорте fixture в `@Before`. Прямая ошибка: `Invalid pack-relative path: ../../../../../data/com.alexpo.grammermate/files/...` в `PackInstallTransaction.kt`. Красные Android XML сохранены в `device-canonical-path-results`; холодная загрузка до старта instrumented runner отдельно в `device-cold-start-anr-results`. Эти результаты относятся к коду до финальной корректировки и не маскируются как PASS.

Исправление нормализует transaction root один раз для recovery, journal, target paths, directories и registry. Добавлены два регрессионных теста в `PackInstallTransactionTest`: импорт/rollback через `filesDir` с lexical alias и проверка containment при отличающемся canonical root. После этой последней production/test правки пользователь прямо попросил остановить дальнейшие запуски. **Их исходник включён в commit, но post-fix сборка, JVM tests и Android cases локально не запускались.** Ранее приведённые 737/lint не являются доказательством исправленного Android пути. Следующий push активирует настроенный CI на feature-ветках; окончательная проверка остаётся там.
