// ──────────────────────────────────────────────────────────────────────
// GrammarMate — German Learning Program Generator
// All data embedded. Run: Workflow({scriptPath: this_file})
// ──────────────────────────────────────────────────────────────────────

export const meta = {
  name: 'german-program',
  description: 'Generate complete German learning program with real AI-written content',
  phases: [
    { title: 'Research', detail: 'Study Italian pack as quality reference' },
    { title: 'Curriculum', detail: 'Design unique German chapter structure' },
    { title: 'Stories', detail: 'AI writes allegorical stories with {de} tags' },
    { title: 'Lessons', detail: 'AI writes real lesson CSV content' },
    { title: 'Drills', detail: 'AI writes verb conjugation and vocab drills' },
    { title: 'Chips', detail: 'AI writes grammar chip content' },
    { title: 'Package', detail: 'Build ZIP, deploy to assets, patch app code' },
  ],
}

const LANG = 'de'
const LANG_NAME = 'German'
const LANG_RU = 'Немецкий'
const LANG_RU_LOWER = 'немецкий'
const PACK_ID = 'GERMAN_EXPRESS'
const PROJECT = 'd:/Development/BaseGrammy'
const ASSETS = PROJECT + '/app/src/main/assets/grammarmate/packs'
const OUT = 'output_de'
const TENSES = 'Präsens, Perfekt, Präteritum, Futur I, Plusquamperfekt, Konjunktiv II, Imperativ'
const NUM_CHAPTERS = 3
const LESSONS_PER_CH = 12

// ── Phase 1: Research ──
phase('Research')

const ref = await agent(
  `Read Italian pack content as quality reference for German program generation.
  In d:/Development/BaseGrammy run:
  1. python -c "import zipfile;z=zipfile.ZipFile('app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS.zip');print(z.read('stories/chapter_00.md').decode('utf-8')[:6000])"
  2. python -c "import zipfile;z=zipfile.ZipFile('app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS.zip');print(z.read('lesson_01_A01.csv').decode('utf-8')[:2000])"
  3. python -c "import zipfile;z=zipfile.ZipFile('app/src/main/assets/grammarmate/packs/ITALIAN_EXPRESS.zip');print(z.read('it_verb_groups_all.csv').decode('utf-8')[:3000])"
  Return ALL content verbatim — this is the quality benchmark.`,
  { label: 'italian-ref' }
)

log('Italian reference collected')

// ── Phase 2: Curriculum ──
phase('Curriculum')

const CURRICULUM_SCHEMA = {
  type: 'object',
  properties: { chapters: { type: 'array', items: { type: 'object', properties: {
    chapterId: {type:'string'}, order: {type:'integer'}, title: {type:'string'},
    subtitle: {type:'string'}, storyFile: {type:'string'}, grammarFocus: {type:'string'},
    storyOutline: {type:'string'}, lessons: {type:'array', items: {type:'string'}}
  }, required: ['chapterId','order','title','subtitle','storyFile','grammarFocus','storyOutline','lessons']}}},
  required: ['chapters']
}

const curriculum = await agent(
  `Design a German (de) learning curriculum for GrammarMate app (RU→DE).
  German features: 3 genders (m/f/n), 4 cases (Nom/Akk/Dat/Gen), verb conjugation, Konjunktiv II.
  Tenses: ${TENSES}
  Design ${NUM_CHAPTERS} chapters. Chapter 0 = intro "Architecture of Language" (no lessons).
  Chapter 1 should cover cases and genders (unique to German). Chapter 2 = present tense basics.
  Each content chapter has ${LESSONS_PER_CH} lessons: lesson_XX_AXX format.
  Lesson numbering: chapter 1 = lessons 01-12, chapter 2 = lessons 13-24.`,
  { label: 'curriculum', schema: CURRICULUM_SCHEMA }
)

log('Curriculum: ' + curriculum.chapters.length + ' chapters')

// ── Phase 3: Stories ──
phase('Stories')

const stories = await pipeline(
  curriculum.chapters,
  ch => agent(`Write a COMPLETE allegorical story for a German language learning app chapter.

REFERENCE QUALITY (Italian — match this depth and style):
${ref.substring(0, 5000)}

CHAPTER: ${ch.title} (${ch.subtitle})
GRAMMAR FOCUS: ${ch.grammarFocus}
STORY OUTLINE: ${ch.storyOutline}

RULES:
1. Write in RUSSIAN with German inserts
2. Use tags: {de}German text{/de} for ALL German words/phrases
3. Use *italic* for German words, **bold** for emphasis
4. 2000-5000 words, rich and engaging allegorical narrative
5. Start with chapter title as heading (no # prefix)
6. Include REAL German sentences, verb forms, noun declensions
7. Explain grammar through metaphor (like the Italian reference)
8. ${ch.order === 0 ? 'Use "locked room" metaphor: show complex German sentence, break into verbs, explain structure' : 'Build on prior chapters, introduce new grammar via metaphor'}
9. NO PLACEHOLDERS — write actual content

Write the COMPLETE story now.`, { label: 'story-' + ch.chapterId, phase: 'Stories' })
)

log(stories.filter(Boolean).length + ' stories generated')

// ── Phase 4: Lessons ──
phase('Lessons')

const LESSON_SCHEMA = { type: 'object', additionalProperties: { type: 'string' } }

const lessonBatches = curriculum.chapters.filter(ch => ch.lessons.length > 0).map(ch => ({
  ch,
  prompt: `Write REAL lesson CSV content for ${ch.lessons.length} German lessons.

FORMAT: Semicolon-delimited CSV per lesson file.
- Line 1: Title (max 160 chars, only letters/digits/spaces)
- Lines 2+: RU prompt with hint;DE answer
- Multiple answers: answer1+answer2
- 15-25 sentence pairs per lesson
- Progressive difficulty

CHAPTER: ${ch.title} — grammar focus: ${ch.grammarFocus}
LESSONS: ${ch.lessons.join(', ')}

Return JSON: lessonId -> full CSV string content (with real German translations, NOT placeholders).`,
  schema: LESSON_SCHEMA
}))

const lessonResults = await pipeline(
  lessonBatches,
  lb => agent(lb.prompt, { label: 'lessons-ch' + lb.ch.order, phase: 'Lessons', schema: lb.schema })
)

const allLessons = {}
lessonResults.filter(Boolean).forEach(r => { if (r) Object.assign(allLessons, r) })
log(Object.keys(allLessons).length + ' lesson files')

// ── Phase 5: Drills ──
phase('Drills')

const DRILL_SCHEMA = {
  type: 'object',
  properties: {
    verb_drill: {type:'string'}, nouns: {type:'string'}, verbs: {type:'string'},
    adjectives: {type:'string'}, adverbs: {type:'string'}, numbers: {type:'string'}, pronouns: {type:'string'}
  },
  required: ['verb_drill','nouns','verbs','adjectives','adverbs','numbers','pronouns']
}

const drills = await agent(
  `Write REAL drill CSV content for German.

TENSES: ${TENSES}
GENDERS: 3 (m/f/n). CASES: 4 (Nom/Akk/Dat/Gen).

1. VERB DRILL (de_verb_groups_all.csv): semicolon-delimited, 60+ rows
   ru;de;verb;tense;group;rank
   Include: sein, haben, werden, gehen, sprechen, lesen, schreiben, essen, trinken, machen, kommen, sehen, wissen, möchten, können, dürfen, müssen, sollen, wollen

2. VOCAB DRILLS (comma-delimited, 30+ entries each):
   de_drill_nouns.csv: rank,noun,collocations,ru  (include gender: der/die/das)
   de_drill_verbs.csv: rank,verb,collocations,ru
   de_drill_adjectives.csv: rank,adjective,msg,fsg,nsg,mpl,fpl,npl,collocations,ru
   de_drill_adverbs.csv: rank,adverb,comparative,superlative,ru,collocations
   de_drill_numbers.csv: category,de,ru,form_m,form_f,notes
   de_drill_pronouns.csv: type,category,person,form_sg_m,form_sg_f,form_pl_m,form_pl_f,notes,ru

Return JSON with all 7 CSV contents. REAL data, NO placeholders.`,
  { label: 'drills', phase: 'Drills', schema: DRILL_SCHEMA }
)

log('Drills generated')

// ── Phase 6: Grammar chips ──
phase('Chips')

const CHIP_SCHEMA = { type: 'object', additionalProperties: { type: 'object', properties: {
  json: { type: 'object' }, md: { type: 'string' }
}, required: ['json','md'] } }

const totalLessons = curriculum.chapters.reduce((s,c) => s + c.lessons.length, 0)
const chips = await agent(
  `Write grammar chips for ${totalLessons} German lessons.
CHAPTERS: ${curriculum.chapters.map(c => c.chapterId + ': ' + c.title + ' (' + c.grammarFocus + ', ' + c.lessons.length + ' lessons)').join(' | ')}

Each chip: {"json": {key, title, essence, examples: {de: [...], ru: [...]}, relatedLesson}, "md": "markdown content with {de} tags"}
Return map: lessonId -> {json, md}.`,
  { label: 'chips', phase: 'Chips', schema: CHIP_SCHEMA }
)

log('Chips generated')

// ── Phase 7: Package ──
phase('Package')

// Build manifest
const manifest = {
  schemaVersion: 2,
  packId: PACK_ID,
  packVersion: "v1",
  language: LANG,
  displayName: LANG_NAME + " Express",
  description: LANG_NAME + " course: " + curriculum.chapters.length + " chapters",
  chapters: curriculum.chapters.map(ch => ({
    chapterId: ch.chapterId, order: ch.order, title: ch.title, subtitle: ch.subtitle,
    storyFile: ch.storyFile, lessons: ch.lessons
  })),
  verbDrill: { files: [LANG + "_verb_groups_all.csv"] },
  vocabDrill: { files: [LANG + "_drill_nouns.csv", LANG + "_drill_verbs.csv", LANG + "_drill_adjectives.csv", LANG + "_drill_adverbs.csv", LANG + "_drill_numbers.csv", LANG + "_drill_pronouns.csv"] }
}

// Use a single packaging agent that writes all files
const pkgResult = await agent(
  `Package the German learning program. Write ALL files using the Write tool.

BASE DIR: ${PROJECT}/${OUT}/${PACK_ID}
Create dirs: stories/, grammar_chips/, grammar_chips_json/

FILE 1: manifest.json
${JSON.stringify(manifest, null, 2)}

FILES 2-${stories.filter(Boolean).length + 1}: Stories
${stories.map((s, i) => stories[i] ? 'FILE: stories/' + curriculum.chapters[i].storyFile + '\nCONTENT:\n' + s.substring(0, 8000) + '\n(continue writing the full story)' : '').filter(Boolean).join('\n\n')}

FILES: Lesson CSVs (${Object.keys(allLessons).length} files)
${Object.entries(allLessons).slice(0, 5).map(([id, csv]) => 'FILE: ' + id + '.csv\n' + csv.substring(0, 2000)).join('\n\n')}
${Object.keys(allLessons).length > 5 ? '... and ' + (Object.keys(allLessons).length - 5) + ' more lesson files' : ''}

FILES: Drills (7 files)
FILE: ${LANG}_verb_groups_all.csv
${drills.verb_drill ? drills.verb_drill.substring(0, 3000) : ''}

FILE: ${LANG}_drill_nouns.csv
${drills.nouns ? drills.nouns.substring(0, 2000) : ''}

FILE: ${LANG}_drill_verbs.csv
${drills.verbs ? drills.verbs.substring(0, 2000) : ''}

FILE: ${LANG}_drill_adjectives.csv
${drills.adjectives ? drills.adjectives.substring(0, 2000) : ''}

FILE: ${LANG}_drill_adverbs.csv
${drills.adverbs ? drills.adverbs.substring(0, 2000) : ''}

FILE: ${LANG}_drill_numbers.csv
${drills.numbers ? drills.numbers.substring(0, 2000) : ''}

FILE: ${LANG}_drill_pronouns.csv
${drills.pronouns ? drills.pronouns.substring(0, 2000) : ''}

AFTER writing all files, create the ZIP:
cd ${PROJECT} && python -c "
import zipfile, os, json
pack_dir = '${OUT}/${PACK_ID}'
with zipfile.ZipFile('${OUT}/${PACK_ID}.zip', 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(pack_dir):
        for f in sorted(files):
            path = os.path.join(root, f)
            arcname = os.path.relpath(path, pack_dir)
            zf.write(path, arcname)
    print(f'Created ${PACK_ID}.zip: {len(zf.namelist())} files, {sum(i.file_size for i in zf.infolist())/1024:.1f} KB')
"

Then copy to assets:
cp ${PROJECT}/${OUT}/${PACK_ID}.zip ${ASSETS}/${PACK_ID}.zip
echo "Deployed to ${ASSETS}/${PACK_ID}.zip"`,
  { label: 'package', phase: 'Package' }
)

log(PACK_ID + ' packaged and deployed')

return {
  packId: PACK_ID,
  chapters: curriculum.chapters.length,
  lessons: Object.keys(allLessons).length,
  storiesWritten: stories.filter(Boolean).length,
  drillFiles: drills ? 7 : 0,
  chipCount: chips ? Object.keys(chips).length : 0,
}
