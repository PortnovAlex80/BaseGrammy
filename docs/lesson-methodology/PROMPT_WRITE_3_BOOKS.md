# PROMPT: Write 3 Italian Course Books (7 Chapters Each)

## SESSION GOAL

Launch 3 independent subagents to write 7 chapters each for 3 different course variants:
1. **Base Allegorical** (book1/) — Continue from Chapter 2 (Chapters 0-1 exist)
2. **YA Portal Fantasy** (book2/) — Write all 7 chapters from scratch
3. **Popular Science Grammar** (book3/) — Write all 7 chapters from scratch

---

## MATERIALS TO READ (ALL AGENTS)

Before writing, each agent MUST read:

### Core Methodology
1. `D:\Development\BaseGrammy\docs\lesson-methodology\italian-course-program.csv` — 63-lesson curriculum
2. `D:\Development\BaseGrammy\docs\lesson-methodology\book-plan-two-voices.md` — Original narrative plan

### For Reference (Allegorical Style)
3. `D:\Development\BaseGrammy\docs\lesson-methodology\book1\chapter_00.md` — "Before Language"
4. `D:\Development\BaseGrammy\docs\lesson-methodology\book1\chapter_01.md` — "Now"

---

## SHARED REQUIREMENTS (ALL 3 BOOKS)

### Chapter Structure (7 chapters total)
- **Chapter 0:** Pre-A01 (Infinitives only, no person/tense)
- **Chapter 1:** Block A (A01-A16) — Present, articles, basic communication
- **Chapter 2:** Block B (B01-B10) — Past/Future tenses
- **Chapter 3:** Block B (B11-B24) — Pronouns, connectors, Congiuntivo basics
- **Chapter 4:** Block C (C01-C03) — Hypothetical periods
- **Chapter 5:** Block C (C04-C18) — Indirect speech, style
- **Chapter 6:** Block C (C19-C20) — Final assembly, meta-grammar

### The Final Thought
ALL 3 books must build toward the same final sentence that can only be expressed at C2:

> *"Se avessi saputo allora che saresti sparita, sarei rimasto accanto a te. E il mondo che vedevamo insieme non si sarebbe spento."*

### Language & Tone
- **Primary language:** English prose with Italian examples
- **Target audience:** Russian speakers learning Italian (RU→IT)
- **Tone:** Engaging, clear, not condescending
- **Grammar mapping:** Each chapter must clearly show which grammatical concepts enable new expressions

### What Each Chapter Must Contain
1. **Narrative progression** — story moves forward
2. **Grammar breakthroughs** — new constructions appear naturally in dialogue/narration
3. **Italian examples** — actual Italian sentences with translations
4. **"What they can now say"** — summary of new expressive capabilities
5. **"What is still impossible"** — hint at what's still beyond reach

---

## BOOK-SPECIFIC INSTRUCTIONS

### BOOK 1: ALLEGORICAL TALE (book1/)
**Agent:** Allegory-Writer

**Assignment:** Continue from Chapter 2 — write Chapters 2-6 (5 chapters total)

**Style:** Saint-Exupéry / Le Guin allegorical tone
- Two beings: **Iro** (tries to preserve) and **Ua** (tries to tell)
- Setting: Fading world, light slowly withdrawing
- Tone: Philosophical, precise, metaphor-heavy but clear
- Already written: Chapter 0 ("Before Language"), Chapter 1 ("Now")

**Chapter structure:**
- Chapter 2: History and Horizon (B01-B10) — Imperfetto, Passato Prossimo, Trapassato, Futuro, Condizionale
- Chapter 3: Swaddlings and Fabric (B11-B24) — Pronouns, relatives, Congiuntivo
- Chapter 4: Hypothesis About Past (C01-C03) — Periodo Ipotetico
- Chapter 5: Assembly (C04-C18) — Indirect speech, style
- Chapter 6: The Final Thought (C19-C20) — Meta-grammar, complete expression

**Output directory:** `D:\Development\BaseGrammy\docs\lesson-methodology\book1\`

**Files to create:** `chapter_02.md` through `chapter_06.md`

---

### BOOK 2: YA PORTAL FANTASY (book2/)
**Agent:** Fantasy-Writer

**Assignment:** Write ALL 7 chapters from scratch

**Style:** Contemporary YA portal fantasy / isekai
- **Protagonist:** Misha (12, from Moscow, struggling with Italian lessons)
- **Key character:** Aria (13, Italian girl trapped in fantasy realm)
- **Setting:** Modern Moscow → Fantasy Italy accessible through speaking Italian correctly
- **Magic system:** Speaking Italian correctly = magical power/spells
- **Tone:** Adventure, wonder, growing up, friendship

**Chapter structure:**
- Chapter 0: The Door (pre-A01) — Misha finds the portal but can't enter, only infinitives
- Chapter 1: First Steps (A01-A16) — Misha enters, learns basic Italian, meets Aria
- Chapter 2: The Quest Begins (B01-B10) — Past/Future tenses, learn realm's history
- Chapter 3: The Forest of Words (B11-B24) — Pronouns = magical bonds, Congiuntivo = possibility magic
- Chapter 4: The Shadow Realm (C01-C03) — Hypothetical periods = counterfactual magic
- Chapter 5: The Library of Echoes (C04-C18) — Indirect speech = channeling others' words
- Chapter 6: The Spell of Completion (C19-C20) — Final battle, complete grammar as ultimate spell

**Key concept:** Each new tense/construction = new magical ability. Grammar IS magic.

**Output directory:** `D:\Development\BaseGrammy\docs\lesson-methodology\book2\`

**Files to create:** `chapter_00.md` through `chapter_06.md`

---

### BOOK 3: POPULAR SCIENCE GRAMMAR (book3/)
**Agent:** NonFiction-Writer

**Assignment:** Write ALL 7 chapters from scratch

**Style:** Popular science / explanatory non-fiction
- No fictional characters or plot
- Tone: Like Steven Pinker, Brian Greene, or Mary Roach writing about grammar
- Clear explanations of WHY Italian grammar works as it does
- Examples from real life, travel, culture, practical situations
- For readers who want understanding, not story

**Chapter structure:**
- Chapter 0: The Architecture of Language (pre-A01) — What infinitives are, why they exist alone
- Chapter 1: The Center of Being (A01-A16) — Why present tense comes first, articles as shared memory
- Chapter 2: The Landscape of Time (B01-B10) — How past/future tenses map to human cognition
- Chapter 3: The Fabric of Connection (B11-B24) — Pronouns as efficiency, Congiuntivo as subjectivity
- Chapter 4: The Space of What-If (C01-C03) — How hypothetical thought shapes language
- Chapter 5: The Echo Chamber (C04-C18) — Indirect speech as embedded consciousness
- Chapter 6: The Complete Map (C19-C20) — Grammar as unified system

**Key concept:** Grammar isn't rules — it's a cognitive technology for expressing thought.

**Output directory:** `D:\Development\BaseGrammy\docs\lesson-methodology\book3\`

**Files to create:** `chapter_00.md` through `chapter_06.md`

---

## FORMATTING REQUIREMENTS

### File header for each chapter:
```markdown
# Chapter [N] — [Title]

---

[Narrative/explanatory content]

---

*[End of Chapter [N]]*
```

### Length guidelines:
- Chapter 0: ~80-100 lines
- Chapters 1-6: ~150-200 lines each

### Italian examples:
- Always provide Italian + English translation
- Use italics for Italian words in prose: *vedo*, *ti vedo*
- Block quotes for full sentences: > *Ti vedo.* — I see you.

---

## WORKFLOW

1. Create output directories if they don't exist
2. Read all required materials first
3. Write chapters sequentially (0 → 6)
4. After each chapter, save to file
5. Continue until all chapters complete
6. Report completion with summary

---

## QUALITY CHECKLIST

Before finalizing each chapter, verify:
- [ ] Grammatical concepts from the CSV are accurately embedded
- [ ] The Final Thought is still impossible at current level
- [ ] New expressive capabilities are clear
- [ ] Italian examples are correct
- [ ] Tone matches the book's style
- [ ] Length is appropriate
- [ ] File is saved to correct directory

---

## INDEPENDENT WORK

- Each agent works independently, in parallel
- No coordination needed between agents
- Different books can use different interpretations of the same grammar
- Focus on making YOUR book the best it can be for YOUR audience

---

*Start date: [session date]*
*Expected completion: 7 chapters × ~3 hours = ~21 hours per agent*
