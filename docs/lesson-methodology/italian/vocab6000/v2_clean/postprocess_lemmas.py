"""
Post-process v2_lemmas_raw.txt:
1. Fix verbs that spaCy failed to lemmatize (vai->andare, sai->sapere, etc.)
2. Fix nouns that are actually other POS
3. Remove junk (names, interjections, truncated forms)
4. Produce clean v2_lemmas_clean.txt
"""
import sys
import os
import re

sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(__file__)

# ============================================================
# MANUAL VERB LEMMATIZATION DICTIONARY
# Covers conjugated forms that spaCy couldn't lemmatize
# ============================================================
VERB_LEMMAS = {
    # ESSERE (to be)
    'sono': 'essere', 'sei': 'essere', 'e': 'essere', 'era': 'essere',
    'erano': 'essere', 'sara': 'essere', 'sarai': 'essere', 'saro': 'essere',
    'saremo': 'essere', 'sarete': 'essere', 'saranno': 'essere',
    'sarei': 'essere', 'saresti': 'essere', 'sarebbe': 'essere',
    'saremmo': 'essere', 'sareste': 'essere', 'sarebbero': 'essere',
    'sii': 'essere', 'siate': 'essere', 'siano': 'essere',
    'fossi': 'essere', 'fosse': 'essere', 'fossimo': 'essere', 'fossero': 'essere',
    'fui': 'essere', 'fosti': 'essere', 'fu': 'essere', 'fummo': 'essere', 'foste': 'essere',
    'ero': 'essere', 'eri': 'essere', 'eravamo': 'essere', 'eravate': 'essere',
    'essendo': 'essere', 'stato': 'essere', 'stata': 'essere', 'stati': 'essere', 'state': 'essere',
    'é': 'essere',

    # AVERE (to have)
    'ho': 'avere', 'hai': 'avere', 'ha': 'avere', 'hanno': 'avere',
    'avevo': 'avere', 'avevi': 'avere', 'aveva': 'avere', 'avevamo': 'avere',
    'avevate': 'avere', 'avevano': 'avere', 'ebbi': 'avere', 'avesti': 'avere',
    'ebbe': 'avere', 'avemmo': 'avere', 'aveste': 'avere', 'ebbero': 'avere',
    'avrò': 'avere', 'avrai': 'avere', 'avra': 'avere', 'avremo': 'avere',
    'avrete': 'avere', 'avranno': 'avere',
    'avrei': 'avere', 'avresti': 'avere', 'avrebbe': 'avere',
    'avremmo': 'avere', 'avreste': 'avere', 'avrebbero': 'avere',
    'abbia': 'avere', 'abbiamo': 'avere', 'abbiate': 'avere',
    'avessi': 'avere', 'avesse': 'avere', 'avessimo': 'avere', 'avessero': 'avere',
    'avendo': 'avere', 'avuto': 'avere',

    # ANDARE (to go)
    'vado': 'andare', 'vai': 'andare', 'va': 'andare', 'andiamo': 'andare',
    'andate': 'andare', 'vanno': 'andare', 'andra': 'andare', 'andrai': 'andare',
    'andremo': 'andare', 'andrete': 'andare', 'andranno': 'andare',
    'andrei': 'andare', 'andresti': 'andare', 'andrebbe': 'andare',
    'andassimo': 'andare', 'andassero': 'andare',
    'andando': 'andare', 'andato': 'andare', 'andata': 'andare',
    'vattene': 'andare', 'andarsene': 'andare',

    # FARE (to do/make)
    'faccio': 'fare', 'fai': 'fare', 'fa': 'fare', 'facciamo': 'fare',
    'fate': 'fare', 'fanno': 'fare', 'farò': 'fare', 'farai': 'fare',
    'fara': 'fare', 'faremo': 'fare', 'farete': 'fare', 'faranno': 'fare',
    'farei': 'fare', 'faresti': 'fare', 'farebbe': 'fare',
    'faccia': 'fare', 'facciano': 'fare',
    'facendo': 'fare', 'fatto': 'fare', 'fatta': 'fare', 'fatti': 'fare', 'fatte': 'fare',
    'fammi': 'fare', 'dammi': 'dare', 'dimmi': 'dire',
    'farcela': 'fare', 'farlo': 'fare', 'farla': 'fare', 'farli': 'fare', 'farle': 'fare',

    # DIRE (to say/tell)
    'dico': 'dire', 'dici': 'dire', 'dice': 'dire', 'diciamo': 'dire',
    'dite': 'dire', 'dicono': 'dire', 'dirò': 'dire', 'dirai': 'dire',
    'dira': 'dire', 'diremo': 'dire', 'direte': 'dire', 'diranno': 'dire',
    'direi': 'dire', 'diresti': 'dire', 'direbbe': 'dire',
    'dica': 'dire', 'dicano': 'dire',
    'dicessi': 'dire', 'dicesse': 'dire', 'dicessimo': 'dire', 'dicessero': 'dire',
    'dicendo': 'dire', 'detto': 'dire',

    # POTERE (can/to be able)
    'posso': 'potere', 'puoi': 'potere', 'puo': 'potere', 'può': 'potere',
    'possiamo': 'potere', 'potete': 'potere', 'possono': 'potere',
    'potro': 'potere', 'potrai': 'potere', 'potra': 'potere',
    'potremo': 'potere', 'potrete': 'potere', 'potranno': 'potere',
    'potrei': 'potere', 'potresti': 'potere', 'potrebbe': 'potere',
    'potremmo': 'potere', 'potreste': 'potere', 'potrebbero': 'potere',
    'possa': 'potere', 'possano': 'potere',
    'potessi': 'potere', 'potesse': 'potere', 'potessimo': 'potere', 'potessero': 'potere',
    'potendo': 'potere', 'potuto': 'potere',

    # DOVERE (must/to have to)
    'devo': 'dovere', 'devi': 'dovere', 'deve': 'dovere', 'dobbiamo': 'dovere',
    'dovete': 'dovere', 'devono': 'dovere', 'dovro': 'dovere', 'dovrai': 'dovere',
    'dovra': 'dovere', 'dovremo': 'dovere', 'dovrete': 'dovere', 'dovranno': 'dovere',
    'debbiamo': 'dovere',
    'dovrei': 'dovere', 'dovresti': 'dovere', 'dovrebbe': 'dovere',
    'dovremmo': 'dovere', 'dovreste': 'dovere', 'dovrebbero': 'dovere',
    'dev': 'dovere', 'dov': 'dovere',
    'debba': 'dovere', 'debbano': 'dovere',
    'dovessi': 'dovere', 'dovesse': 'dovere', 'dovessimo': 'dovere', 'dovessero': 'dovere',
    'dovendo': 'dovere', 'dovuto': 'dovere',

    # VOLERE (to want)
    'voglio': 'volere', 'vuoi': 'volere', 'vuole': 'volere', 'vogliamo': 'volere',
    'volete': 'volere', 'vogliono': 'volere', 'vro': 'volere', 'vorrai': 'volere',
    'vorra': 'volere', 'vorremo': 'volere', 'vorrete': 'volere', 'vorranno': 'volere',
    'vorrei': 'volere', 'vorresti': 'volere', 'vorrebbe': 'volere',
    'vorremmo': 'volere', 'vorreste': 'volere', 'vorrebbero': 'volere',
    'voglia': 'volere', 'vogliano': 'volere',
    'volessi': 'volere', 'volesse': 'volere', 'volessimo': 'volere', 'volessero': 'volere',
    'volendo': 'volere', 'voluto': 'volere',
    'volevi': 'volere', 'voleva': 'volere', 'volevamo': 'volere',

    # SAPERE (to know)
    'so': 'sapere', 'sai': 'sapere', 'sa': 'sapere', 'sappiamo': 'sapere',
    'sapete': 'sapere', 'sanno': 'sapere', 'sapro': 'sapere', 'saprai': 'sapere',
    'sapra': 'sapere', 'sapremo': 'sapere', 'saprete': 'sapere', 'sapranno': 'sapere',
    'saprei': 'sapere', 'sapresti': 'sapere', 'saprebbe': 'sapere',
    'sappia': 'sapere', 'sappiano': 'sapere',
    'sapessi': 'sapere', 'sapesse': 'sapere', 'sapessimo': 'sapere', 'sapessero': 'sapere',
    'sapendo': 'sapere', 'saputo': 'sapere',

    # STARE (to stay/stand)
    'sto': 'stare', 'stai': 'stare', 'sta': 'stare', 'stiamo': 'stare',
    'state': 'stare', 'stanno': 'stare', 'staro': 'stare', 'starai': 'stare',
    'stara': 'stare', 'staremo': 'stare', 'starete': 'stare', 'staranno': 'stare',
    'starei': 'stare', 'staresti': 'stare', 'starebbe': 'stare',
    'stia': 'stare', 'stiano': 'stare',
    'stessi': 'stare', 'stesse': 'stare', 'stessimo': 'stare', 'stessero': 'stare',
    'stavo': 'stare', 'stavi': 'stare', 'stava': 'stare', 'stavamo': 'stare', 'stavate': 'stare',
    'stando': 'stare', 'stato': 'essere',  # stato -> essere (past participle)

    # VENIRE (to come)
    'vengo': 'venire', 'vieni': 'venire', 'viene': 'venire', 'veniamo': 'venire',
    'venite': 'venire', 'vengono': 'venire', 'verro': 'venire', 'verrai': 'venire',
    'verra': 'venire', 'verremo': 'venire', 'verrete': 'venire', 'verranno': 'venire',
    'venissi': 'venire', 'venisse': 'venire', 'venissimo': 'venire', 'venissero': 'venire',
    'venga': 'venire', 'vengano': 'venire',
    'venendo': 'venire', 'venuto': 'venire',

    # RIESCIRE (to manage/succeed)
    'riesco': 'riuscire', 'riesci': 'riuscire', 'riesce': 'riuscire',
    'riusciamo': 'riuscire', 'riuscite': 'riuscire', 'riescono': 'riuscire',
    'riuscito': 'riuscire',

    # DARE (to give)
    'do': 'dare', 'dai': 'dare', 'da': 'dare', 'diamo': 'dare',
    'date': 'dare', 'danno': 'dare', 'daro': 'dare', 'darai': 'dare',
    'dara': 'dare', 'daremo': 'dare', 'darete': 'dare', 'daranno': 'dare',
    'darei': 'dare', 'daresti': 'dare', 'darebbe': 'dare',
    'dia': 'dare', 'diano': 'dare',
    'dessi': 'dare', 'desse': 'dare', 'dessimo': 'dare', 'dessero': 'dare',
    'dando': 'dare', 'dato': 'dare', 'data': 'dare', 'dati': 'dare', 'date': 'dare',

    # IMMAGINARE (to imagine)
    'immagino': 'immaginare',

    # INTENDERE (to understand/mean)
    'intendi': 'intendere',

    # ASCOLTARE (to listen) + pronoun
    'ascoltami': 'ascoltare',

    # Common verb+pronoun clusters -> infinitive
    'trovarlo': 'trovare', 'trovarla': 'trovare',
    'vederlo': 'vedere', 'vederla': 'vedere',
    'sentirlo': 'sentire', 'sentirla': 'sentire',
    'dirlo': 'dire', 'dirla': 'dire',
    'farlo': 'fare', 'farla': 'fare',
    'farglielo': 'fare', 'dirglielo': 'dire',
    'comprarlo': 'comprare', 'comprarla': 'comprare',
    'prenderlo': 'prendere', 'prenderla': 'prendere',
    'mangiarlo': 'mangiare', 'mangiarla': 'mangiare',
    'scriverlo': 'scrivere', 'scriverla': 'scrivere',
    'leggerlo': 'leggere', 'leggerla': 'leggere',
    'chiamarlo': 'chiamare', 'chiamarla': 'chiamare',
    'conoscerlo': 'conoscere', 'conoscerla': 'conoscere',
    'guardarlo': 'guardare', 'guardarla': 'guardare',
    'lasciarlo': 'lasciare', 'lasciarla': 'lasciare',
    'perderlo': 'perdere', 'perderla': 'perdere',
    'capirlo': 'capire', 'capirla': 'capire',
    'amarlo': 'amare', 'amarla': 'amare',
    'farti': 'fare', 'farmi': 'fare', 'fargli': 'fare',
    'dirti': 'dire', 'dirmi': 'dire', 'dirgli': 'dire',
    'darti': 'dare', 'darmi': 'dare', 'dargli': 'dare',
    'capirmi': 'capire', 'capirti': 'capire',
    'crederlo': 'credere', 'crederla': 'credere',
    'scusami': 'scusare', 'scusami': 'scusare',
    'smettila': 'smettere',
    'andiamo': 'andare',
    'coraggio': 'coraggere',  # actually interjection
}

# ============================================================
# WORDS TO REMOVE (not real Italian words, or wrong POS)
# ============================================================
REMOVE_WORDS = {
    # Interjections/fillers
    'be', 'beh', 'ehm', 'wow', 'oh', 'ah', 'uh', 'boh', 'mah',
    'vabbè', 'vabbe', 'dai', 'dopo', 'gia',
    # Truncated/glitched forms
    'cia', 'co', 'do', 'ia', 'ta', 'va', 'na', 'da',
    # English words in frequency list
    'house', 'white', 'love', 'enterprise', 'chance', 'life',
    'online', 'college', 'single', 'barbecue', 'little', 'alpha',
    'delta', 'one', 'negro', 'club', 'west', 'east', 'yes', 'no',
    'baby', 'body', 'black', 'blue', 'street', 'king', 'music',
    'rock', 'sport', 'super', 'top', 'tv', 'video', 'web',
    'fast', 'food', 'full', 'happy', 'home', 'hotel', 'power',
    'prime', 'red', 'running', 'smart', 'star', 'story', 'team',
    'test', 'time', 'water', 'world', 'new', 'open', 'play',
    # Not useful for vocab drill
    'ok', 'okay', 'grazie', 'ciao', 'perche', 'cosi', 'cioe',
    'piu', 'puo', 'po', 'cio', 'gia',
}

# Proper names (common ones that appear in frequency list)
# We'll detect these via first-letter capitalization check later

# ============================================================
# PROCESS
# ============================================================

# Read raw
with open(os.path.join(BASE, 'v2_lemmas_raw.txt'), 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

entries = []
for line in lines[1:]:  # skip header
    parts = line.split('\t')
    if len(parts) < 4:
        continue
    rank = int(parts[0])
    original = parts[1]
    lemma = parts[2]
    pos = parts[3]
    tag = parts[4] if len(parts) > 4 else ''
    entries.append((rank, original, lemma, pos, tag))

print(f"Read {len(entries)} entries")

# Fix verbs using manual dictionary
fixed = 0
for i, (rank, orig, lemma, pos, tag) in enumerate(entries):
    if orig in VERB_LEMMAS:
        new_lemma = VERB_LEMMAS[orig]
        entries[i] = (rank, orig, new_lemma, 'VERB', tag)
        fixed += 1
    elif lemma in VERB_LEMMAS and pos == 'VERB':
        new_lemma = VERB_LEMMAS[lemma]
        entries[i] = (rank, orig, new_lemma, 'VERB', tag)
        fixed += 1

print(f"Fixed {fixed} verb lemmas using manual dictionary")

# Now dedup again: (lemma, POS) -> lowest rank
lemma_map = {}
for rank, orig, lemma, pos, tag in entries:
    # Skip non-target POS
    if pos not in ('NOUN', 'VERB', 'ADJ', 'ADV'):
        continue

    # Skip removed words
    if orig.lower() in REMOVE_WORDS or lemma.lower() in REMOVE_WORDS:
        continue

    key = (lemma.lower(), pos)
    if key not in lemma_map or rank < lemma_map[key][0]:
        lemma_map[key] = (rank, orig, lemma, pos, tag)

print(f"After dedup + filter: {len(lemma_map)} unique (lemma, POS)")

# Additional cleanup: remove if lemma == original and is conjugated verb form
# (verbs we couldn't fix)
still_conjugated = 0
to_remove = []
for key, (rank, orig, lemma, pos, tag) in lemma_map.items():
    if pos == 'VERB' and lemma == orig:
        # Check if it looks like a conjugated form (doesn't end in -re)
        if not lemma.endswith('re'):
            # Try one more thing: common Italian endings
            # -o, -i, -a, -iamo, -ate, -ono, -avo, -evo, -ivo, etc.
            if any(lemma.endswith(e) for e in ['o', 'i', 'a', 'e', 'no', 'te']):
                to_remove.append(key)
                still_conjugated += 1

for key in to_remove:
    del lemma_map[key]

print(f"Removed {still_conjugated} still-conjugated verbs")

# Stats
from collections import Counter
pos_counts = Counter(pos for (_, pos), _ in lemma_map.items())
print("\nFinal clean lemma distribution:")
for pos, count in pos_counts.most_common():
    print(f"  {pos}: {count}")

# Sort by rank
sorted_entries = sorted(lemma_map.values(), key=lambda x: x[0])

# Write clean output
output = os.path.join(BASE, 'v2_lemmas_clean.txt')
with open(output, 'w', encoding='utf-8') as f:
    f.write("rank\toriginal\tlemma\tPOS\n")
    for rank, orig, lemma, pos, tag in sorted_entries:
        f.write(f"{rank}\t{orig}\t{lemma}\t{pos}\n")

print(f"\nWritten {len(sorted_entries)} clean lemmas to v2_lemmas_clean.txt")
print(f"Rank range: {sorted_entries[0][0]}-{sorted_entries[-1][0]}")
