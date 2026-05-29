# Agent 1: Block A (A01-A08) Comparison

## Methodology

**Old ZIP structure**: 7 broad lessons organized by verb tense/mood, NOT by grammar topic. Each lesson covers many grammar points simultaneously (articles, nouns, adjectives, verbs, pronouns, etc.) with sentences bundled by tense.

| Old Lesson | Title | Sentences |
|---|---|---|
| it_lesson_01 | Presente | 183 |
| it_lesson_02 | Verbi Riflessivi | 65 |
| it_lesson_03 | Imperfetto | 151 |
| it_lesson_04 | Passato Prossimo | 220 |
| it_lesson_05 | Futuro + Remoto | 151 |
| it_lesson_06 | Condizionale + Mix | 220 |
| it_lesson_07 | Tutti i Tempi | 217 |
| **Total** | | **1,207** (1207 content lines) |

**New structure**: 8 focused lessons organized by grammar topic, each targeting one specific concept with systematic progression.

| New Lesson | Topic | Sentences |
|---|---|---|
| lesson_01_A01 | Presente Indicativo | 71 |
| lesson_02_A02 | Articoli (det. + indet.) | 68 |
| lesson_03_A03 | Sostantivi: genere e numero | 56 |
| lesson_04_A04 | Aggettivi | 58 |
| lesson_05_A05 | Forma di cortesia (Lei) | 44 |
| lesson_06_A06 | Essere vs Avere (stati) | 55 |
| lesson_07_A07 | Domande (W-questions) | 53 |
| lesson_08_A08 | C'è / Ci sono | 46 |
| **Total** | | **451** |

---

## Lesson A01: Presente Indicativo

- **New file**: lesson_01_A01.csv (71 sentences)
- **Old ZIP mapping**: it_lesson_01 ("Presente") contains 183 sentences, many of which use Presente Indicativo.

### Sentence count: Old ~183 vs New 71

### Coverage comparison

| Grammar point | Old lesson 01 | New A01 |
|---|---|---|
| Regular -are verbs (parlare, mangiare, etc.) | Scattered across cooking, sports, travel vocab | Systematic: parlo italiano, studiamo a Roma |
| Regular -ere verbs (leggere, scrivere, etc.) | Present but mixed with all conjugations | Grouped: non capisco, non parli inglese |
| Regular -ire verbs (capire, finire, dormire) | Present (capisco, dormire) | Present: capisci, studiate |
| Irregular: essere (sono, sei, e, etc.) | Yes, mixed with other verbs | Dedicated block: Sono studente / Sei insegnante (lines 12-25) |
| Irregular: avere (ho, hai, ha, etc.) | Yes, mixed | Dedicated block: Ho tempo / Hai tempo? (lines 26-39) |
| Irregular: andare (vado, vai, va, etc.) | Yes (vado al parco) | Dedicated block: Vado a casa / Vai a scuola (lines 40-53) |
| Irregular: fare (faccio, fai, fa, etc.) | Yes (fanno il lavoro) | Dedicated block: Faccio ginnastica / Fai colazione (lines 54-67) |
| Negation with non | Sparse (non capisco niente) | Systematic: every verb block has non-variants |
| Questions (inversion) | Not systematically trained | Not in A01 (deferred to A07) |
| Survival formulas (mi chiamo, piacere) | Yes (line 2 of old L02: Mi chiamo Marco) | Present: Mi chiamo Marco, Come ti chiami?, Mi sento bene, Piacere (lines 68-72) |

### Notable differences

- **OLD**: Sentences are vocabulary-heavy, mixing grammar with diverse topics (cooking, sports, travel, daily routines). Examples: "Io cucino il pranzo", "Noi navighiamo in barca", "Io starnutisco". Verbs are correct but the focus is on vocabulary breadth, not conjugation mastery.
- **NEW**: Sentences are grammar-focused. Every sentence is designed to drill conjugation patterns. The same verb appears in all persons (io/tu/lui/lei/noi/voi/loro) before moving to the next verb. Minimal pairs: "Vado a casa" / "Non vado a casa".
- **OLD** includes many verbs not in NEW: amare, odiare, preferire, nuotare, sciare, dipingere, fotografare, viaggiare, etc. (approximately 50+ different verbs).
- **NEW** focuses on ~5 key verb groups (parlare/capire, essere, avere, andare, fare) with full paradigm coverage.

### Gaps

- **Old has, New lacks**: Wide verb vocabulary (amare, volere, potere, dovere, preferire, giocare, cucinare, etc.). However, many of these are better placed in later lessons (modals in A14, reflexive in B13).
- **New has, Old lacks**: Systematic negative paradigm for each verb group. Old lesson 01 has only 2 negative sentences out of 183. New A01 has 24 negative sentences (one per person per verb). Survival formulas grouped explicitly.

### Assessment

The new A01 is dramatically more focused. The old lesson threw 50+ verbs at the student. The new lesson takes 5 verb groups and drills them through all persons, affirmative, and negative. This is a major pedagogical improvement for building automaticism with conjugation.

---

## Lesson A02: Articoli (det. + indet.)

- **New file**: lesson_02_A02.csv (68 sentences)
- **Old ZIP mapping**: NO dedicated lesson. Articles are scattered across ALL old lessons as incidental vocabulary. Old lesson 01 contains many sentences with articles (il/lo/la/l'/un/uno/una/un') but never teaches them systematically.

### Sentence count: Old ~0 (dedicated) vs New 68

### Coverage comparison

| Grammar point | Old lessons | New A02 |
|---|---|---|
| il / la / lo / l' / i / gli / le (definite) | Used throughout but never contrasted | Systematic minimal pairs: Questo e il libro / Questa e la casa |
| un / uno / una / un' (indefinite) | Used throughout but never contrasted | Systematic: Questo e un libro / Questa e una casa |
| il vs un contrast | Never explicit | Core pattern: "Eto kniga (il libro); Questo e il libro" vs "Eto odin uchenik (uno studente); Questo e uno studente" |
| lo before s+consonant (lo specchio, lo zio) | Appears incidentally (lo specchio not in old) | Explicit: lo specchio, lo zio, lo stadio |
| l' before vowels (l'amico, l'acqua, l'anno) | Appears incidentally | Explicit: l'amico, l'acqua, l'anno |
| Plural articles (i/gli/le) | Scattered | Dedicated block: Questi sono gli spaghetti, Queste sono le chiavi |
| Negation with articles | Not covered | Present: Questo non e il libro, Questa non e la casa |
| Questions with articles | Not covered | Present: E il libro? E uno studente? |

### Notable differences

- **OLD**: Has NO lesson or section dedicated to articles. Students must absorb article rules by osmosis from vocabulary-rich sentences.
- **NEW**: Every sentence is a minimal pair contrasting definite vs indefinite: "Eto kniga (libro); Questo e il libro" immediately followed by "Eto odin uchenik (studente); Questo e uno studente". The Russian prompt makes the definite/indefinite distinction explicit.
- **NEW** covers plural articles (gli spaghetti, gli occhiali, le chiavi, i genitori, le scarpe, i pantaloni, le forbici) -- these only appear scattered in old lessons 4-7.
- **NEW** includes special forms: l' (l'amico, l'acqua, l'anno), lo (lo specchio, lo zio), uno (uno studente, uno specchio, uno zio), un' (un'insegnante, un'amica).

### Gaps

- **Old has**: Nothing dedicated. Articles were assumed knowledge.
- **New has**: Complete systematic article coverage with minimal pairs. This is entirely new content with no direct old equivalent.
- **Missing from both**: Articled prepositions (al, del, nel, sul) -- correctly deferred to A09 per the plan.

### Assessment

A02 is an entirely new lesson with no real old equivalent. The old pack never taught articles explicitly. The new lesson provides the first systematic article training in the course. 68 minimal-pair sentences covering all article forms is strong coverage.

---

## Lesson A03: Sostantivi: genere e numero

- **New file**: lesson_03_A03.csv (56 sentences)
- **Old ZIP mapping**: NO dedicated lesson. Nouns appear throughout all old lessons with gender/number but never taught systematically. Some nouns in old lesson 01 have gendered forms (ragazzo/ragazza, studente/studentessa) but without contrasting singular/plural.

### Sentence count: Old ~0 (dedicated) vs New 56

### Coverage comparison

| Grammar point | Old lessons | New A03 |
|---|---|---|
| -o/-a gender (ragazzo/ragazza, gatto/gatta) | Present incidentally | Systematic pairs: ragazzo/ragazza, gatto/gatta, amico/amica |
| -o/-i plural (libro/libri, tavolo/tavoli) | Some plurals in old L04-L07 | Explicit: libro/libri, tavolo/tavoli, giorno/giorni |
| -a/-e plural (penna/penne, sedia/sedie, notte/notti) | Rare in old | Explicit: penna/penne, sedia/sedie, notte/notti |
| -e/-i plural (giorno/giorni already -o group) | Rare | Not present (but -e nouns like autore/autori covered) |
| Invariable: città (sg=pl) | Appears in old L04+ | Explicit: città/città (lines 42-43) |
| Invariable: re, direttore | Not covered | Not covered (acceptable scope) |
| -ista: turista (m/f same form) | Not in old | Explicit: turista/una turista (lines 37-38) |
| -ema/-isma: problema | Appears in old (problema tecnico) | Explicit: problema/problemi (lines 40-41) |
| Irregular plurals: uomo/uomini | Appears in old | Explicit: uomo/uomini (lines 30-31) |
| Irregular plurals: donna/donne | Not in old systematically | Explicit: donna/donne (lines 32-33) |
| mano (feminine with -o) | Not covered | Explicit: mano/mani (lines 44-45) |
| Negation/questions with nouns | Not covered | Present: Questo non e un ragazzo? Non e un gatto? |

### Notable differences

- **OLD**: Never teaches gender/number as a topic. Students encounter ragazzo, ragazza, donne, uomini scattered across 1200+ sentences.
- **NEW**: Every noun appears as a quadruple: masculine singular, feminine singular (where applicable), masculine plural, feminine plural. Then negation and questions with the same nouns.
- **NEW** specifically targets -ista nouns (turista), -ema nouns (problema), invariable nouns (città), and irregular plurals (uomo/uomini) -- these are the key exceptions that trip up Russian speakers.

### Gaps

- **New lacks**: Nouns in -e (e.g., madre/madri, padre/padri, dottore/dottori). The lesson focuses on -o/-a/-e nouns but uses mostly -o/-a paradigms. Only autore/autori and lezione/lezioni cover -e nouns.
- **New lacks**: Nouns in -ione (lezione is present but stazione, nazione are not). Acceptable since these follow the regular -e pattern.
- **Old has**: Nothing systematic. Nouns were embedded in verb conjugation exercises.

### Assessment

A03 is another entirely new lesson with no direct old equivalent. The old pack assumed students would learn noun gender/number by exposure. The new lesson provides 56 sentences of systematic minimal pairs covering the core patterns and key exceptions. Good scope for an introductory lesson.

---

## Lesson A04: Aggettivi

- **New file**: lesson_04_A04.csv (58 sentences)
- **Old ZIP mapping**: NO dedicated lesson. Adjectives appear throughout old lessons, especially lesson 01 (bello, buono, bravo, nuovo, piccolo, giovane, grande) but never taught as a grammar topic.

### Sentence count: Old ~0 (dedicated) vs New 58

### Coverage comparison

| Grammar point | Old lessons | New A04 |
|---|---|---|
| Basic agreement: -o/-a/-i/-e (bello/bella/belli/belle) | Partial (bello ragazzo, bella vita scattered) | Systematic: bella ragazza / belle ragazze / bei ragazzi |
| bello before s+consonant: bello studente | Not in old | Explicit: bello studente (line 4) |
| bel before vowels: bell'amico, bell'uomo | Not in old | Explicit: bell'amico, bell'uomo (lines 5-6) |
| bei (plural masculine before consonant) | Not in old | Explicit: bei ragazzi (line 7) |
| buono/buona/buoni/buone | Present (buon padre, buona idea in old L01) | Systematic: buon padre / buona madre / buoni genitori |
| Position: before noun (bello, buono, nuovo) | Not taught | Implicit in sentence pairs |
| Position: after noun (felice, forte, importante) | Appears (donna forte, parte importante) | Explicit contrast: famiglia felice / parte importante / uomo forte |
| Invariable adjectives: giovane/giovani | Present in old | Explicit: giovane uomo / giovani donne |
| Meaning shift by position: povero uomo vs uomo povero | Not in old | Explicit: povero uomo (unhappy) vs uomo povero (poor) -- lines 38-39 |
| Meaning shift: grande uomo vs uomo grande | Present in old (grande uomo, uomo grande both in L01) | Explicit: grande uomo (great) vs uomo grande (big) -- lines 40-41 |
| Meaning shift: vecchio amico vs amico vecchio | Not in old | Explicit: vecchio amico (old friend) vs amico vecchio (old by age) -- lines 42-43 |
| Meaning shift: bella donna vs donna bella | Partial in old | Explicit: bella donna (beauty) vs donna bella (emphasis) -- lines 44-45 |
| Meaning shift: bravo ragazzo vs ragazzo bravo | Not in old | Explicit: bravo ragazzo (capable) vs ragazzo bravo (good job) -- lines 46-47 |
| Predicate adjectives: Il ragazzo e alto | Not systematically | Explicit block: Il ragazzo e alto / I ragazzi sono alti (lines 30-37) |
| Negation with adjectives | Sparse | Systematic: Questa non e una bella ragazza (lines 48-52) |
| Questions with adjectives | Not covered | Present: Questa e una bella ragazza? (lines 53-59) |

### Notable differences

- **OLD**: Adjectives are used in sentences but never contrasted or explained. Old lesson 01 has both "un grande uomo" and "un uomo grande" but the student has no way to understand why the meaning changes.
- **NEW**: The position-based meaning shifts are the highlight of the lesson. Lines 38-47 systematically contrast before-noun vs after-noun meanings for povero, grande, vecchio, bello, bravo. This is the kind of nuance that was completely absent from the old pack.
- **NEW** covers the tricky forms of bello (bello studente, bell'amico, bell'uomo, bei ragazzi, belle ragazze) -- these special forms are scattered across 100+ sentences in the old pack without any pattern recognition.

### Gaps

- **New lacks**: Comparative/superlative adjectives (più bello, il più bello) -- correctly deferred to A13.
- **New lacks**: Colors as adjectives (rosso, blu, verde) -- vocabulary, not grammar.
- **Old has**: More adjective vocabulary (fortunato, curioso, capriccioso, tenero, azzurro, celeste, etc.) -- but without grammar focus.

### Assessment

A04 is the strongest new lesson. The position-based meaning contrasts (povero uomo vs uomo povero, grande uomo vs uomo grande) are a major pedagogical innovation absent from the old pack. 58 sentences covering agreement patterns, special forms of bello, and meaning shifts is excellent.

---

## Lesson A05: Forma di cortesia (Lei)

- **New file**: lesson_05_A05.csv (44 sentences)
- **Old ZIP mapping**: NO dedicated lesson. The Lei form appears in old lesson 02 (reflexives) via "Come ti chiami?" but never contrasted with tu/Lei.

### Sentence count: Old ~0 (dedicated) vs New 44

### Coverage comparison

| Grammar point | Old lessons | New A05 |
|---|---|---|
| tu vs Lei contrast | Not in old | Core pattern: every sentence has tu/Lei pair (lines 2-19) |
| Lei with present indicative (Lei parla, Lei capisce) | Not in old | Systematic: Tu parli / Lei parla, Tu capisci / Lei capisce |
| Lei with essere (Lei e stanco?) | Not in old | Present: Tu sei stanco? / Lei e stanco? |
| Lei with avere (Lei ha fame?) | Not in old | Present: Tu hai fame? / Lei ha fame? |
| Lei with verbs of motion (Lei viene da...) | Not in old | Present: Tu vieni da Milano? / Lei da dove viene? |
| Survival phrases (Come sta? Per favore, Piacere) | Partial (Piacere in old L01) | Full set: Come sta?, Per favore, Piacere, Buona giornata, Arrivederci |
| Suo (formal possessive) | Not in old | Present: Qual e il Suo cognome? |
| Lei with studia/vive/abita | Not in old | Present: Lei studia italiano, Lei abita in Italia, Lei vive a Roma |
| Ha bisogno di (formal need) | Not in old | Present: Ha bisogno di aiuto? |
| Desidera (formal offer) | Not in old | Present: Desidera un caffe? |

### Notable differences

- **OLD**: The Lei register is completely absent. There is no formal/informal contrast anywhere in 1200+ sentences.
- **NEW**: Every concept is introduced first in tu, then immediately in Lei. This tu/Lei pairing is the core pedagogical mechanism. Lines 2-19 are 9 pairs (18 sentences) of identical meaning in tu and Lei.
- **NEW** includes survival formulas essential for real-world Italian: "Come sta?", "Desidera un caffe?", "Ha bisogno di aiuto?", "Qual e il Suo cognome?". None of these appear in the old pack.

### Gaps

- **New lacks**: Lei imperative (Parli! Venga!) -- correctly deferred to A16 per plan.
- **New lacks**: Loro (formal plural) -- acceptable omission for A1 level.
- **Old has**: Nothing comparable.

### Assessment

A05 fills a critical gap. The old pack had zero coverage of the formal register, which is essential for real Italian interaction. 44 sentences with systematic tu/Lei contrast and survival formulas is well-scoped. The restriction to only Indicativo forms (no Congiuntivo imperative) is correct per the plan.

---

## Lesson A06: Essere vs Avere (states)

- **New file**: lesson_06_A06.csv (55 sentences)
- **Old ZIP mapping**: NO dedicated lesson. Essere and avere appear in old lesson 01 but mixed with all other verbs. State expressions (ho fame, ho freddo) appear scattered.

### Sentence count: Old ~0 (dedicated) vs New 55

### Coverage comparison

| Grammar point | Old lessons | New A06 |
|---|---|---|
| Essere + adjective (property): Sono stanco | Partial (sono in old L01 scattered) | Dedicated block: Sono stanco, Sei stanca, E felice, etc. (lines 3-22) |
| Essere + noun (identity): Sono studente | Partial (Sono studente in old L01) | Present: Sono studente, Sei dottore, E casalinga (lines 10-13) |
| Essere + location: Siete in Italia | Not in old systematically | Present: Siete in Italia, E a casa (lines 14-15) |
| Essere + nationality: Sono italiano | Not in old | Present: Sono italiano (line 16) |
| Avere + sensation (physical): Ho fame | Partial (ho molta paura in old L01) | Systematic: Ho fame, Hai sete, Ha freddo, Ho caldo, Abbiamo sonno, Avete paura (lines 28-40) |
| Avere + abstract: Ho ragione | Present (Tu hai ragione in old L01) | Present: Ho ragione, Hai torto (lines 35-36) |
| Avere + age: Ho 20 anni | Not in old | Present: Ho 20 anni, Ha 30 anni (lines 37-38) |
| Avere + pain: Ho mal di testa | Not in old (La testa fa male in old L01) | Present: Ho mal di testa, Hai mal di denti (lines 39-40) |
| Avere + speed: Ho fretta | Not in old | Present: Hanno fretta (line 34) |
| Negation with essere states | Sparse | Systematic: Non sono stanco, Non sei triste (lines 23-26) |
| Negation with avere states | Sparse | Systematic: Non ho fame, Non hai sete (lines 41-48) |
| Essere vs Avere contrast | Not in old | Dedicated contrast section (lines 49-55): Sono stanco -> Ho fame, etc. |

### Notable differences

- **OLD**: Mixed essere/avere usage across hundreds of sentences. "Ho molta paura" (old L01), "Tu hai ragione" (old L01), but never contrasted with essere states. The student has no way to understand why "I am cold" is "Ho freddo" (have cold) not "Sono freddo" (am cold -- different meaning).
- **NEW**: Clear structural split -- "ESSERE: property, 'to be something'" (lines 2-26) vs "AVERE: sensation, 'to have something'" (lines 27-48) vs "CONTRAST: essere vs avere" (lines 49-55). The contrast section is pedagogically critical for Russian speakers whose "mne holodno" (Dative) maps to neither essere nor avere directly.
- **NEW** includes age (Ho 20 anni) and pain (Ho mal di testa) -- practical everyday expressions absent from the old pack's systematic teaching.

### Gaps

- **New lacks**: Extended essere states (essere arrabbiato, essere curioso) -- vocabulary expansion, not core grammar.
- **New lacks**: Ho sonno vs Sono assonnato contrast -- nuance for later.
- **Old has**: Some avere expressions scattered (ho molta paura, ho una buona idea, ho un figlio) but these are "having possessions" not "having states" -- different grammar point.

### Assessment

A06 addresses a key confusion point for Russian speakers. The essere/avere split for states is one of the first obstacles, and the old pack did nothing to address it. The 6-sentence contrast section at the end (lines 49-55) is the most valuable part. Good scope at 55 sentences.

---

## Lesson A07: Domande (W-questions)

- **New file**: lesson_07_A07.csv (53 sentences)
- **Old ZIP mapping**: NO dedicated lesson. Questions appear in old lessons but only as occasional yes/no or inversion questions. W-questions are not systematically covered.

### Sentence count: Old ~0 (dedicated) vs New 53

### Coverage comparison

| Grammar point | Old lessons | New A07 |
|---|---|---|
| Chi (who): Chi sei? Chi e quello? | Not in old | Lines 2-3, 28, 39, 42, 50 |
| Che cosa / cosa (what): Cosa fai? | Not in old | Lines 4-5, 29, 40, 43, 51 |
| A chi (to whom): A chi pensi? | Not in old | Lines 6-7, 30 |
| Di chi (whose): Di chi e questo libro? | Not in old | Lines 8-9, 31 |
| Con chi (with whom): Con chi vai? | Not in old | Lines 10-11, 32 |
| Dove (where): Dove vivi? | Not in old systematically | Lines 12-14, 33, 41, 44, 52 |
| Di dove (from where): Di dove sei? | Not in old | Line 15 |
| Quando (when): Quando arrivi? | Not in old | Lines 16-17, 34, 45, 53 |
| Come (how): Come ti chiami? | Present in old L02 (Come ti chiami?) | Lines 18-20, 35, 46 |
| Perche (why): Perche piangi? | Not in old | Lines 21-22, 36, 47 |
| Quanto/quanti (how much/many): Quanti anni hai? | Not in old | Lines 23-24, 37, 48 |
| Quale (which): Qual e il ristorante? | Not in old | Lines 25-26, 38, 49 |
| Che ore sono (what time) | Not in old | Line 25 (Che ore sono?) |
| Che giorno e (what day) | Not in old | Line 49 (Che giorno e oggi?) |
| voi form questions | Not in old | Lines 29-37 (systematic voi set) |
| Dov'e (contracted dove+essere) | Not in old | Lines 41, 44, 52 |

### Notable differences

- **OLD**: Questions are essentially absent from the old pack. The only interrogative forms are the occasional yes/no question. No W-questions are systematically taught.
- **NEW**: Covers 12 question words with both tu and voi forms. Each question word gets 2-6 sentences. The tu set (lines 2-27) is followed by a voi set (lines 28-38) for key question words, then mixed practice (lines 39-53).
- **NEW** properly introduces dove vs di dove (where vs from where), cosa vs cos'e (what vs what is it), and quanto vs quanti (how much vs how many).
- **NEW** teaches contracted forms: Dov'e (dove + e), Cos'e (cosa + e).

### Gaps

- **New lacks**: Perche as "because" (answer form) -- only question form shown. Acceptable for scope.
- **New lacks**: Quanto + adjective (Quanto costa? is present but Quanto e grande? is not).
- **New lacks**: Compound questions (Chi e la persona che...?) -- correctly deferred to relative clauses (B22).

### Assessment

A07 fills another critical gap. The old pack had essentially zero question training. 53 sentences covering all major W-question words with tu and voi forms is well-scoped. The lesson directly addresses the plan's note about "Italian has no do-support" by teaching natural question formation through inversion.

---

## Lesson A08: C'è / Ci sono

- **New file**: lesson_08_A08.csv (46 sentences)
- **Old ZIP mapping**: Old lesson 01 line 48: "C'e tanta gente" and old lesson 04 line 20: "C'e tanta gente" / "C'era poca gente". These are the only c'e/ci sono sentences in the entire old pack.

### Sentence count: Old ~2 vs New 46

### Coverage comparison

| Grammar point | Old lessons | New A08 |
|---|---|---|
| C'e + singular: C'e una scuola qui | Only "c'e tanta gente" | Systematic: C'e una scuola qui, C'e una banca li, C'e un teatro qui (lines 2-4) |
| Ci sono + plural: Ci sono studenti in classe | Not in old | Systematic: Ci sono piatti vegetariani, Ci sono musei, Ci sono studenti (lines 5-7, 14-16) |
| Questions with c'e/ci sono | Not in old | Present: C'e una scuola qui? Ci sono musei? (lines 2-7) |
| C'e + uncountable: C'e speranza qui | Not in old | Present: C'e speranza qui, C'e senso, C'e acqua qui (lines 10-12, 26-27, 31) |
| Location with c'e: C'e un divano in soggiorno | Not in old | Systematic: C'e un divano in soggiorno, C'e un frigorifero in cucina, etc. (lines 17-21) |
| Existence in nature: Ci sono pesci nel mare | Not in old | Present: Ci sono pesci nel mare, Ci sono funghi nel bosco (lines 29-30) |
| essere vs c'e contrast | Not in old | Critical contrast: Il libro e qui / C'e un libro qui (lines 32-37) |
| Negation: Non c'e / Non ci sono | Not in old | Systematic: Non c'e un banco, Non ci sono studenti, Non c'e acqua (lines 38-44) |
| essere for known vs c'e for new | Not in old | Explicit: La scuola e in citta / C'e una scuola in citta (lines 34-35) |

### Notable differences

- **OLD**: Only 2 sentences out of 1200 use c'e/ci sono. The existential construction is virtually absent.
- **NEW**: The essere vs c'e contrast section (lines 32-37) is pedagogically critical. "Il libro e qui" (the book is here -- known entity, definite article) vs "C'e un libro qui" (there's a book here -- new entity, indefinite article) directly teaches the article-existence connection that the plan specifies.
- **NEW** includes negative forms (Non c'e, Non ci sono) and natural environment sentences (pesci nel mare, funghi nel bosco, alberi, nuvole).

### Gaps

- **New lacks**: C'era / C'erano (past tense) -- correctly deferred to B01 (Imperfetto).
- **New lacks**: Ci sara / Ci saranno (future) -- correctly deferred to B07.
- **New lacks**: C'e ne / Ce n'e (with ne pronoun) -- correctly deferred to B14.

### Assessment

A08 fills yet another gap. The old pack had essentially zero existential construction training. 46 sentences covering c'e/ci sono in questions, affirmations, negations, with the critical essere contrast, is well-scoped.

---

## Summary for Block A (A01-A08)

### Sentence counts

| Lesson | New sentences | Old dedicated sentences | Old thematic matches |
|---|---|---|---|
| A01 Presente Indicativo | 71 | ~183 (old L01, broad) | ~50 Presente-specific |
| A02 Articoli | 68 | 0 | ~0 |
| A03 Sostantivi genere/numero | 56 | 0 | ~0 |
| A04 Aggettivi | 58 | 0 | ~0 (scattered adjective usage) |
| A05 Forma di cortesia | 44 | 0 | ~0 |
| A06 Essere vs Avere | 55 | 0 | ~5 scattered state expressions |
| A07 Domande (W-questions) | 53 | 0 | ~0 |
| A08 C'e / Ci sono | 46 | 0 | ~2 |
| **Total new** | **451** | | |

### Expansion factor

- **Old total relevant sentences**: ~50 (only A01 topic overlaps; A02-A08 had zero or near-zero dedicated coverage)
- **New total**: 451
- **Expansion factor**: ~9x for topics A02-A08 (from ~7 sentences to 322 sentences)
- For A01 specifically: 71 focused sentences vs 183 unfocused = 0.39x in raw count but much higher in pedagogical density

### Key observations

1. **Topic-based vs tense-based organization**: The old pack organized by verb tense (Presente, Imperfetto, Passato Prossimo, Futuro, Condizionale, Mixed). This meant fundamental grammar topics (articles, noun gender/number, adjectives, formal register, question words, existential constructions) were NEVER taught. The new plan organizes by grammar topic, ensuring every foundational concept gets dedicated practice.

2. **7 of 8 topics are entirely new**: Only A01 (Presente Indicativo) has a direct old equivalent. A02-A08 cover grammar points that were completely absent from the old pack. This means the new course adds ~322 sentences of coverage for topics that previously had zero or near-zero practice material.

3. **Pedagogical density is much higher**: Old lesson 01 had 183 sentences covering 50+ verbs with vocabulary breadth. New A01 has 71 sentences covering 5 verb groups with full paradigm depth. Every new sentence is designed to build one specific grammar automatism. The old sentences, while correct, were more vocabulary exposure than grammar drill.

4. **Minimal pairs are the key innovation**: The new lessons consistently use minimal pairs (il libro / un libro, ragazzo / ragazza / ragazzi / ragazze, Sono stanco / Ho fame, Tu parli / Lei parla, Il libro e qui / C'e un libro qui). The old pack never used this technique.

5. **Russian speaker targeting**: The new lessons explicitly address Russian-speaker-specific confusion points:
   - A02: Russian has no articles -> systematic article training
   - A03: Russian noun declension maps differently to Italian gender -> systematic gender/number
   - A04: Russian adjective agreement differs -> position-based meaning shifts
   - A05: Russian formal "Vy" maps to Lei -> tu/Lei contrast
   - A06: Russian "mne holodno" (Dative) vs Italian "ho freddo" (Avere) -> essere/avere state contrast
   - A07: Russian question words don't use do-support -> natural question formation
   - A08: Russian "est'" maps to both essere and c'e -> explicit contrast

6. **Vocabulary trade-off**: The old pack had much broader vocabulary (cooking, sports, travel, daily routines, nature, warfare, politics, etc.). The new pack sacrifices vocabulary breadth for grammar depth. This is the correct trade-off at the foundational level -- vocabulary will expand naturally through later lessons and the story component.

7. **Old content not lost**: Most old lesson 01 vocabulary (cucinare, mangiare, viaggiare, nuotare, etc.) is not in the new A01-A08 lessons, but this vocabulary will reappear in later lessons (B-series for tenses) and in story content. The verbs from old lesson 01 that are NOT in new A01 (amare, volere, potere, dovere, preferire, giocare, cucinare) are correctly placed in later lessons per the plan (modals in A14, etc.).
