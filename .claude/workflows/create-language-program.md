# Learning Program Generator — AI Subagent

## Purpose
Claude workflow that generates a **complete, ready-to-use learning program** for a new target language. 
Real content — stories, translations, conjugation tables — written by AI, not Python templates.

## How to use
```
/workflow create-language-program --args '{"language": "German", "code": "de", "pack_id": "GERMAN_EXPRESS"}'
```

## What it produces
1. **Curriculum design** — unique chapter structure for the language
2. **Stories** — allegorical narratives in Russian with {lang}...{/lang} tagged target language inserts
3. **Lesson CSVs** — real RU→target translation pairs
4. **Grammar chips** — real grammar rules and examples
5. **Verb drill CSV** — real conjugation tables
6. **Vocab drill CSVs** — real word lists with translations
7. **manifest.json** — correct v2 schema
8. **ZIP pack** — deployed to assets
9. **App code patches** — LanguageManager, TTS, StoryParser, etc.
