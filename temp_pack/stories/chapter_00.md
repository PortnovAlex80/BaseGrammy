# Тестовая глава 0 — Проверка мультиязычного TTS

---

## Абзац 1 — Русский язык

Это первый абзац на русском языке. Здесь нет иностранных вставок, только чистый русский текст. Мы проверим, что русская TTS модель работает корректно и произносит этот текст с правильной интонацией и ударением.

Если вы слышите этот текст на русском языке — значит базовая TTS функциональность работает правильно.

---

## Paragraph 2 — English Language

This is the second paragraph written entirely in English language. There are no foreign insertions here, just pure English text to test the English TTS model. We will verify that the pronunciation is clear and natural with proper intonation.

{it}This is an Italian word inside English text.{/it} Here we continue in English. The system should detect the Italian marker and switch to Italian pronunciation for just that word, then return to English.

This test verifies that language switching works correctly even within a single paragraph.

---

## Абзац 3 — Итальянский язык с вставками

Questo è il terzo paragrafo scritto interamente in italiano. {ru}Это русская вставка внутри итальянского текста.{/it} Qui continuiamo in italiano per verificare che la pronuncia italiana sia corretta.

Testiamo tre tipi di commutazione:
1. Italiano → {en}inglese{/it} → Italiano
2. Italiano → {ru}русский{/it} → Italiano
3. Italiano puro senza insertions

{it}Vedere{/it} означает "видеть" по-итальянски. {en}To see{/it} по-английски. И "видеть" по-русски.

Конец тестовой главы.