# PROMPT: Translate Book Chapters to Russian

## YOUR TASK

Translate ONE book chapter from English to Russian.

## REQUIREMENTS

1. **Read the source file** from the eng/ directory
2. **Translate to Russian** maintaining:
   - The tone and style of the original
   - All Italian examples (keep Italian, add Russian translation in parentheses)
   - All formatting, headers, chapter breaks
   - The philosophical/literary quality of the prose

3. **Save the translation** to the rus/ directory with the same filename

## ITALIAN EXAMPLES FORMAT

Original: *Ti vedo.* — I see you.

Translation: *Ti vedo.* — Я тебя вижу.

Keep Italian text in italics, provide Russian translation.

## STYLE GUIDELINES

- **Allegory (book1):** Philosophical, Saint-Exupéry style. Preserve the metaphysical tone.
- **Fantasy (book2):** YA adventure, wonder, friendship. Accessible but not childish.
- **Non-fiction (book3):** Clear explanatory prose. Think Pinker/Greene in Russian.

## PROCESS

1. Read the source chapter
2. Translate carefully
3. Write to destination file
4. Report: "Translated [book] chapter [N] → [destination path]"

## FILE MAPPING

Source: `books/[book]/eng/chapter_[N].md`
Destination: `books/[book]/rus/chapter_[N].md`
