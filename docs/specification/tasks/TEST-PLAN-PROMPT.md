# Контекст для нового сеанса: создание плана тестирования GrammarMate

## Что установлено и работает

### 1. Compose UI тесты через Robolectric (JVM, БЕЗ эмулятора)
- Зависимости добавлены в `app/build.gradle.kts`:
  - `testImplementation("androidx.compose.ui:ui-test-junit4")`
  - `testImplementation("androidx.compose.ui:ui-test-manifest")`
  - `testImplementation("org.robolectric:robolectric:4.11.1")` (был раньше)
  - `testImplementation("androidx.test:core:1.5.0")` (был раньше)
- JVM args для теста: `jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")`
- Test manifest: `app/src/test/AndroidManifest.xml` (с ComponentActivity)

**Важно:** Проект должен запускаться из пути БЕЗ кириллических символов. Сейчас это `D:\Dev\BaseGrammy`. Класслоадер Java и Robolectric не могут читать файлы из путей с кириллицей (проверено).

**Запуск:**
```bash
java -cp "gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --tests "com.alexpo.grammermate.ui.HomeScreenJourneyTest"
```

**Первый работающий тест:** `app/src/test/java/com/alexpo/grammermate/ui/HomeScreenJourneyTest.kt`
- 2 теста проходят: рендер HomeScreen ("Grammar Roadmap" виден) + клик по "Continue Learning" вызывает onPrimaryAction callback
- Использует `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [28])`, `createComposeRule()`
- Тестирует HomeScreen в изоляции (передаёт TrainingUiState напрямую, без ViewModel)

**Ограничения Robolectric Compose тестов:**
- Нет реального рендеринга (нет пикселей) — но semantic tree работает
- Нет анимаций (мгновенные)
- `stringResource()` работает (Robolectric подставляет ресурсы)
- Навигация через NavHost НЕ тестируется изолированно (нужен полный GrammarMateApp)
- Колбэки onClick проверяются через флаги (var clicked = false)

### 2. Maestro E2E тесты (yaml-флоу)
- CLI установлен: Maestro 2.5.1 (`$HOME/.maestro/bin`)
- 10 флоу в `.maestro/flows/` — все валидны (проверено парсером)
- **ТРЕБУЕТ эмулятор или устройство** — не работает без него
- Флоу покрывают: home, settings, lesson roadmap, daily practice, verb/vocab drill, training answer, welcome dialog, input modes, ladder

### 3. mobile-mcp
- MCP сервер `@mobilenext/mobile-mcp` настроен
- **ТРЕБУЕТ эмулятор или устройство**

### 4. Существующие unit-тесты
- ~20 файлов в `app/src/test/`
- Большинство в `data/` — парсеры, сторы, алгоритмы
- Несколько в `feature/` — boss, daily, progress
- **Известные сломанные тесты:**
  - `DailyPracticeCoordinatorTest.kt` — ссылки на удалённые методы (advanceToNextBlock, advanceDailyTask, replaceCurrentBlock)
  - `ProgressTrackerTest.kt` — параметр taskIndex не найден
  - Эти тесты компилируются с ошибками и блокируют запуск ВСЕХ тестов через `testDebugUnitTest`

## Экраны приложения (ui/screens/)
- `HomeScreen.kt` — главный экран (уже тестируется)
- `LessonRoadmapScreen.kt` — выбор урока
- `TrainingScreen.kt` — тренировка (самый сложный экран)
- `SettingsScreen.kt` — настройки
- `LadderScreen.kt` — лестница интервалов
- `StoryQuizScreen.kt` — квиз по истории

## Что нужно сделать

Изучи всю кодовую базу тестов и экранов, и создай **комплексный план тестирования**:

1. **Проанализируй все существующие тесты** — какие проходят, какие сломаны, что покрывают
2. **Изучи все экраны** (ui/screens/) — какие composables доступны для изолированного тестирования (принимают state + callbacks)
3. **Изучи TrainingViewModel** — какие части можно тестировать через helpers/feature/ изолированно
4. **Составь план:**
   - Какие Compose UI тесты можно написать БЕЗ эмулятора (приоритет)
   - Какие unit-тесты нужно починить
   - Какие новые unit-тесты нужны для критического бизнес-логики
   - Что отложить до появления эмулятора (Maestro, mobile-mcp)
5. **Оцени** каждый пункт по приоритету (P0/P1/P2) и сложности (S/M/L)

Покажи план в виде таблицы с колонками: Приоритет | Что | Файл(ы) | Сложность | Статус

Формат: "Создай задачу" или просто detailed markdown — на твоё усмотрение, главное чтобы план был actionable.
