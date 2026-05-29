# Как создавать локальных субагентов для проекта

## 📋 Обзор

Локальные субагенты - это специализированные экземпляры Claude с экспертизой в конкретных областях. Вместо одного общего агента вы можете создать специалистов для разных задач.

## 🎯 Преимущества локальных агентов

- **Изоляция контекста**: Каждый агент работает в отдельном контексте
- **Параллелизация**: Несколько агентов могут работать одновременно
- **Специализация**: Каждый агент имеет свою экспертизу и инструменты
- **Экономия токенов**: Агенты видят только релевантную информацию

## 📁 Структура файлов

Локальные агенты хранятся в `.claude/agents/`:

```
.claude/
├── agents/
│   ├── agent-name.md           # Markdown с frontmatter
│   └── another-agent.md
└── settings.json               # Настройки проекта
```

## 📝 Формат агента (Frontmatter)

```yaml
---
name: my-agent
description: Используйте этого агента когда... (когда вызывать)
tools: Read, Write, Edit, Bash   # Опционально: разрешённые инструменты
model: sonnet                     # Опционально: модель (sonnet/opus/haiku)
color: blue                       # Опционально: цвет в UI
memory: project                   # Опционально: user/project/local
---
```

### Поля frontmatter:

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `name` | string | ✅ Да | Уникальное имя агента |
| `description` | string | ✅ Да | Когда использовать этого агента (натуральный язык) |
| `tools` | string[] | ❌ Нет | Разрешённые инструменты (по умолчанию все) |
| `model` | string | ❌ Нет | Модель: `sonnet`/`opus`/`haiku`/`inherit` |
| `color` | string | ❌ Нет | Цвет UI: `blue`/`green`/`orange`/`purple`/`red`/`yellow` |
| `memory` | string | ❌ Нет | Память: `user`/`project`/`local` |

## 🤖 Создание агента через `/agents`

1. Запустите Claude Code
2. Введите команду `/agents`
3. Выберите "Create New Agent"
4. Выберите уровень (project/user - рекомендуется project)
5. Опишите обязанности агента
6. Claude создаст файл в `.claude/agents/`

## ✍️ Ручное создание агента

Создайте файл `.claude/agents/my-agent.md`:

```markdown
---
name: code-reviewer
description: Используйте этого агента для ревью кода, поиска багов и проверки качества
tools: Read, Grep, Glob
model: sonnet
color: purple
---

Вы старший разработчик, специализирующийся на code review...

## Твоя роль
- Проверяйте код на security vulnerabilities
- Ищите performance issues
- Проверяйте соответствие convention

## Твои правила
- Никогда не предлагайте изменения без объяснения
- Всегда ссылайтесь на best practices
- Будьте конструктивны
```

## 🛠️ Примеры комбинаций инструментов

| Use case | Инструменты | Описание |
|----------|-------------|----------|
| Read-only анализ | `Read`, `Grep`, `Glob` | Чтение без модификации |
| Тестирование | `Bash`, `Read`, `Grep` | Запуск команд и анализ |
| Модификация кода | `Read`, `Edit`, `Write`, `Grep`, `Glob` | Полный доступ к файлам |
| Полный доступ | (все инструменты) | Унаследовать все инструменты |

## 🎨 Примеры агентов

### 1. Code Reviewer
```markdown
---
name: code-reviewer
description: Используйте для ревью кода после завершения major шагов
model: sonnet
color: purple
---

Ты senior code reviewer с экспертизой в security, performance и architecture...
```

### 2. Debugger
```markdown
---
name: debugger
description: Используйте когда есть баг, test failure или unexpected behavior
tools: Read, Grep, Bash
model: opus
color: red
---

Ти системный debugger с методическим подходом...
```

### 3. Architect
```markdown
---
name: architect
description: Используйте для проектирования architecture новых фич
model: opus
color: green
---

Ти senior software architect...
```

## 📌 Вызов агентов

### Автоматический вызов
Claude автоматически вызывает агента на основе `description`:
```
"Проверь код на security проблемы" → вызовет code-reviewer
```

### Явный вызов
Упомяните агента по имени:
```
"Используй code-reviewer агента для проверки authentication module"
```

### Через Agent tool
```
Agent({
  description: "Review authentication code",
  subagent_type: "code-reviewer"
})
```

## 🔄 Загрузка агентов

Агенты загружаются при **запуске** Claude Code. Если вы создали новый файл агента во время сессии, **перезапустите сессию** для его загрузки.

## ⚠️ Troubleshooting

### Агент не вызывается
1. Проверьте, что `description` чётко описывает когда вызывать
2. Используйте явный вызов по имени
3. Убедитесь, что файл агента существует в `.claude/agents/`

### Агент не загрузился
- Перезапустите Claude Code сессию
- Проверьте синтаксис frontmatter (валидный YAML)

### Windows: длинные prompts проваливаются
Ограничение командной строки Windows (8191 chars). Делайте prompts короче или используйте файлы-агенты.

## 📚 Дополнительно

- [Official Claude Code Subagents Docs](https://code.claude.com/docs/en/agent-sdk/subagents)
- [Subagents Quickstart Guide](https://shipyard.build/blog/claude-code-subagents-guide)
- [Awesome Claude Code Subagents](https://github.com/VoltAgent/awesome-claude-code-subagents)
