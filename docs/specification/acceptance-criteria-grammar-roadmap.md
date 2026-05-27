# Grammar Story Roadmap - Acceptance Criteria

## Overview
Добавление слоя **Chapters** над уроками для нарративного обучения грамматике через истории.

---

## 1. Manifest & Data Layer

### AC-1.1: Manifest v2 с Chapters
**Given:** Манифест pack'а имеет `schemaVersion: 2` с полем `chapters`
**When:** Приложение загружает pack
**Then:** 
- Chapters парсятся корректно
- Каждая chapter содержит: `chapterId`, `order`, `title`, `subtitle`, `storyFile`, `lessons[]`
- Lessons внутри chapter сохраняют порядок из `order` field

### AC-1.2: Manifest v1 Backward Compatibility
**Given:** Манифест pack'а имеет `schemaVersion: 1` (без `chapters`)
**When:** Приложение загружает pack
**Then:**
- `getChapters()` возвращает `emptyList()`
- Приложение показывает классический HOME экран
- Существующие功能 продолжают работать без изменений

### AC-1.3: Chapter Progress Tracking
**Given:** Pack имеет chapters
**When:** Пользователь проходит уроки
**Then:**
- `ChapterProgress` сохраняется в pack-scoped store
- `lessonsStarted` = количество уроков с `mastery > 0`
- `lessonsCompleted` = количество уроков с `intervalStepIndex >= 3`
- Прогресс обновляется атомарно через `AtomicFileWriter`

---

## 2. UI Layer - Grammar Story Roadmap Screen

### AC-2.1: Conditional UI Routing
**Given:** Пользователь открывает приложение
**When:** Активный pack имеет chapters
**Then:** Показывается `GrammarStoryRoadmapScreen`

**Given:** Пользователь открывает приложение
**When:** Активный pack НЕ имеет chapters
**Then:** Показывается классический `ClassicHomeScreen`

### AC-2.2: Chapter List Display
**Given:** Пользователь на Grammar Story Roadmap
**When:** Pack имеет 7 chapters
**Then:**
- Все 7 chapters отображаются в порядке `order`
- Каждая chapter показывает: title, subtitle, progress bar, статус
- Chapter 0 имеет статус "DONE" (зеленая галочка)
- Chapter 1 имеет статус "ACTIVE"
- Chapters 2-6 имеют статус "LOCKED"

### AC-2.3: Chapter Progress Visualization
**Given:** Chapter имеет 4 урока
**When:** Пользователь завершил 2 урока (mastery >= 3)
**Then:**
- Progress bar показывает 50%
- Статус chapter обновляется соответствующе
- Процент вычисляется как `lessonsCompleted / totalLessons * 100`

### AC-2.4: Chapter Status States
**Given:** Chapter с уроками
**When:** Уроки в разных состояниях
**Then:**
- **LOCKED:** Chapter недоступна (серая иконка с замком)
- **ACTIVE:** Chapter в прогрессе (зеленая подсветка)
- **DONE:** Все уроки завершены (зеленая галочка)

---

## 3. Story Reader Screen

### AC-3.1: Read Story Navigation
**Given:** Пользователь на Grammar Story Roadmap
**When:** Кликает "Read Story" на Chapter 0
**Then:**
- Открывается `StoryReaderScreen`
- Загружается контент из `chapter0_story.md`
- Header показывает chapter title "Before Language"

### AC-3.2: Markdown Rendering
**Given:** Story reader открыт с markdown контентом
**When:** Контент содержит заголовки, **bold**, *italic*, списки
**Then:**
- Markdown рендерится корректно
- Текст адаптирован под мобильный экран
- Длинный контент можно скроллить

### AC-3.3: Back Navigation
**Given:** Пользователь на Story Reader Screen
**When:** Кликает "Back to Roadmap"
**Then:**
- Возвращается на Grammar Story Roadmap
- Прогресс сохраняется

---

## 4. Practice Modes Integration

### AC-4.1: Continue Button
**Given:** Chapter 1 активна, lesson 2 в прогрессе
**When:** Пользователь кликает "Continue" на Chapter 1
**Then:**
- Запускается lesson 2 (последний активный)
- Открывается TrainingScreen в LESSON mode
- Карточки загружаются из lesson 2

### AC-4.2: Verb Practice Button
**Given:** Пользователь на Grammar Story Roadmap
**When:** Кликает "Verb Practice"
**Then:**
- Открывается `VerbDrillScreen`
- Verb drill функционал работает как раньше
- Прогресс сохраняется независимо

### AC-4.3: Flashcards Button
**Given:** Пользователь на Grammar Story Roadmap
**When:** Кликает "Flashcards"
**Then:**
- Открывается `VocabDrillScreen`
- Vocab drill функционал работает как раньше
- Прогресс сохраняется независимо

### AC-4.4: Daily Practice Button
**Given:** Пользователь на Grammar Story Roadmap
**When:** Кликает "Daily Practice"
**Then:**
- Открывается Daily Practice Screen
- Daily practice функционал работает как раньше
- Сессия включает 3 блока: translate, vocab, verbs

---

## 5. Progress & Persistence

### AC-5.1: Chapter Progress Updates on Lesson Complete
**Given:** Chapter имеет 3 урока
**When:** Пользователь завершает lesson 1 (mastery >= 3)
**Then:**
- `ChapterProgress.lessonsCompleted` инкрементируется
- Progress bar обновляется
- Изменения сохраняются в `ChapterProgressStore`

### AC-5.2: Chapter Progress Independent Across Chapters
**Given:** Pack имеет 2 chapters
**When:** Пользователь завершает урок в Chapter 1
**Then:**
- Chapter 1 прогресс обновляется
- Chapter 0 прогресс НЕ изменяется
- Прогресс каждой chapter независим

### AC-5.3: Last Accessed Tracking
**Given:** Пользователь проходит уроки в Chapter 1
**When:** Урок завершается
**Then:**
- `ChapterProgress.lastAccessedMs` обновляется
- Используется для сортировки/фильтрации активных chapters

---

## 6. Cross-Pack Behavior

### AC-6.1: Pack Switching
**Given:** Пользователь на Pack A (с chapters)
**When:** Переключается на Pack B (без chapters)
**Then:**
- UI переключается на Classic Home Screen
- Прогресс Pack A сохраняется отдельно
- Pack B показывает свой список уроков

### AC-6.2: Pack-Scoped Progress
**Given:** Два pack'а используют одинаковые `lessonId`
**When:** Пользователь проходит урок в Pack A
**Then:**
- Chapter progress Pack A обновляется
- Chapter progress Pack B НЕ изменяется
- Прогресс изолирован по packId

---

## 7. Error Handling

### AC-7.1: Missing Story File
**Given:** Chapter ссылается на `storyFile`
**When:** Файл не существует в pack
**Then:**
- "Read Story" кнопка скрывается или disabled
- Остальной функционал работает
- Логируется warning

### AC-7.2: Corrupted Manifest
**Given:** Manifest v2 имеет невалидный JSON
**When:** Pack загружается
**Then:**
- Фоллбэк на manifest v1 парсинг
- Если не удается - pack не импортируется
- Пользователю показывается error message

### AC-7.3: Empty Chapter
**Given:** Chapter не имеет уроков (`lessons: []`)
**When:** Отображается на Roadmap
**Then:**
- Chapter показывается с 0 уроков
- Progress bar показывает 0%
- "Continue" кнопка disabled

---

## 8. Performance

### AC-8.1: Chapter Loading Time
**Given:** Pack имеет 10 chapters с 50 уроками
**When:** Roadmap screen рендерится
**Then:**
- Загрузка занимает < 500ms
- UI не блокируется
- Прогресс bar анимируется плавно

### AC-8.2: Story Reader Rendering
**Given:** Story file имеет 100KB markdown
**When:** Story reader открывается
**Then:**
- Рендеринг занимает < 300ms
- Скролл плавный (60 FPS)
- Memory usage не превышает лимиты

---

## 9. Regression Tests (Critical Path)

### RT-1: Old Pack Still Works
**Given:** Существующий pack без chapters
**When:** Пользователь обновляет приложение
**Then:**
- Pack загружается корректно
- Все lessons доступны
- Mastery progress сохраняется
- Никакой функционал не сломался

### RT-2: Verb Drill Unaffected
**Given:** Pack с chapters
**When:** Пользователь использует Verb Drill
**Then:**
- Verb drill работает как раньше
- Прогресс verb drill независим от chapters
- Session card функционал сохраняется

### RT-3: Daily Practice Unaffected
**Given:** Pack с chapters
**When:** Пользователь запускает Daily Practice
**Then:**
- 3 блока работают как раньше
- Cursor state корректен
- Streak засчитывается

### RT-4: Flower State Unchanged
**Given:** Pack с chapters
**When:** Пользователь проходит уроки
**Then:**
- Цветы растут как раньше
- `uniqueCardShows` считается корректно
- Health decay работает по Ebbinghaus

---

## 10. Manual Testing Checklist

### UI/UX
- [ ] Roadmap screen показывает все chapters
- [ ] Progress bars отображают правильные проценты
- [ ] Chapter статусы визуально различимы (LOCKED/ACTIVE/DONE)
- [ ] "Read Story" открывает текст истории
- [ ] Markdown рендерится корректно
- [ ] Кнопки "Continue", "Verb Practice", "Flashcards", "Daily Practice" работают

### Navigation
- [ ] Старый pack → Classic Home
- [ ] Новый pack → Roadmap
- [ ] Story Reader → Back → Roadmap
- [ ] Pack switching сохраняет прогресс

### Progress
- [ ] Завершение урока обновляет chapter progress
- [ ] Progress bar обновляется в real-time
- [ ] Прогресс независим между chapters
- [ ] Прогресс сохраняется после app restart

### Backward Compatibility
- [ ] Существующие packs загружаются
- [ ] Mastery progress не теряется
- [ ] Verb drill работает
- [ ] Daily practice работает
- [ ] Цветы растут корректно

---

## Success Metrics

### Must Have (P0)
- ✅ Manifest v2 парсится корректно
- ✅ Roadmap screen показывается для packs с chapters
- ✅ Classic Home для packs без chapters
- ✅ Story Reader рендерит markdown
- ✅ "Continue" запускает последний активный урок
- ✅ Chapter progress сохраняется

### Should Have (P1)
- ✅ Progress bars обновляются в real-time
- ✅ Chapter статусы визуально различимы
- ✅ Pack switching работает
- ✅ Error handling для missing story files

### Nice to Have (P2)
- ✅ Плавные анимации progress bars
- ✅ Story search functionality
- ✅ Chapter bookmarks
- ✅ Offline story caching

---

## Definition of Done

Feature считается завершенным когда:
1. Все **Must Have (P0)** критерии выполнены
2. Все Regression Tests (RT-1 to RT-4) проходят
3. Manual Testing Checklist на 80%+ выполнен
4. Code review прошел
5. Документация обновлена
6. Backward compatibility проверена на существующих packs
