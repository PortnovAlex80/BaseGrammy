"""
Step 2: Filter out words already in drills, rank-limit to ~6000 target,
and generate clean lemma files for CSV generation.
"""
import sys
import os
import csv

sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(__file__)

# Read v2 raw lemmas
raw_file = os.path.join(BASE, 'v2_lemmas_raw.txt')
with open(raw_file, 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

# Parse raw: rank, original, lemma, POS, tag
# Build unique lemma->lowest rank mapping for target POS
entries = []
for line in lines[1:]:  # skip header
    parts = line.split('\t')
    if len(parts) < 4:
        continue
    rank, original, lemma, pos = int(parts[0]), parts[1], parts[2], parts[3]
    tag = parts[4] if len(parts) > 4 else ''
    entries.append((rank, original, lemma, pos, tag))

# Dedup: (lemma, pos) -> keep lowest rank
lemma_map = {}
for rank, original, lemma, pos, tag in entries:
    key = (lemma.lower(), pos)
    if key not in lemma_map or rank < lemma_map[key][0]:
        lemma_map[key] = (rank, original, lemma, pos, tag)

print(f"Total unique (lemma, POS): {len(lemma_map)}")

# Target POS
target_pos = {'NOUN', 'VERB', 'ADJ', 'ADV'}
target = {k: v for k, v in lemma_map.items() if k[1] in target_pos}
print(f"Target POS unique lemmas: {len(target)}")

# Sort by rank
sorted_targets = sorted(target.values(), key=lambda x: x[0])

# Read existing drill words to exclude
drill_dir = r'D:\Development\BaseGrammy\app\src\main\assets\grammarmate\vocab\it'
drill_words = set()

drill_files = {
    'it_drill_nouns.csv': ('noun', 1),
    'it_drill_verbs.csv': ('verb', 1),
    'it_drill_adjectives.csv': ('adjective', 1),
    'it_drill_adverbs.csv': ('adverb', 1),
    'it_drill_numbers.csv': ('number', 1),
    'it_drill_pronouns.csv': ('pronoun', 1),
}

for fname, (pos_type, word_col) in drill_files.items():
    fpath = os.path.join(drill_dir, fname)
    if not os.path.exists(fpath):
        print(f"  SKIP (not found): {fname}")
        continue
    with open(fpath, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader, None)
        count = 0
        for row in reader:
            if len(row) > word_col:
                word = row[word_col].strip().lower()
                if word:
                    drill_words.add(word)
                    count += 1
        print(f"  {fname}: {count} words loaded")

print(f"\nTotal existing drill words: {len(drill_words)}")

# Filter: exclude words already in drills
new_words = []
already_in_drill = 0
for rank, original, lemma, pos, tag in sorted_targets:
    if lemma.lower() in drill_words:
        already_in_drill += 1
        continue
    new_words.append((rank, original, lemma, pos, tag))

print(f"Already in drills (excluded): {already_in_drill}")
print(f"New words needed: {len(new_words)}")

# Take top 6000 by rank (to fill to 6000 total)
# But first count how many are already in drills per POS
from collections import Counter
drill_pos_count = Counter()
for rank, original, lemma, pos, tag in sorted_targets:
    if lemma.lower() in drill_words:
        drill_pos_count[pos] += 1

print("\nAlready covered by POS:")
for p, c in drill_pos_count.most_common():
    print(f"  {p}: {c}")

new_pos_count = Counter(pos for _, _, _, pos, _ in new_words)
print("\nNew words by POS (all ranks):")
for p, c in new_pos_count.most_common():
    print(f"  {p}: {c}")

# Write clean lemma file for ALL new words (not rank-limited yet)
output = os.path.join(BASE, 'v2_new_lemmas.txt')
with open(output, 'w', encoding='utf-8') as f:
    f.write("rank\toriginal\tlemma\tPOS\n")
    for rank, original, lemma, pos, tag in new_words:
        f.write(f"{rank}\t{original}\t{lemma}\t{pos}\n")

print(f"\nWritten {len(new_words)} new lemmas to v2_new_lemmas.txt")

# Also write a summary of top 6000 target
# We want ~6000 TOTAL (existing + new), so new = 6000 - existing
total_existing = already_in_drill
new_needed = max(0, 6000 - total_existing)
print(f"\nTarget: 6000 total = {total_existing} existing + {new_needed} new")
print(f"Available new words: {len(new_words)} (by rank {new_words[0][0]}-{new_words[-1][0]})")

if new_needed < len(new_words):
    # Take top new_needed by rank
    top_new = new_words[:new_needed]
    print(f"Taking top {new_needed} by rank (rank range {top_new[0][0]}-{top_new[-1][0]})")
else:
    top_new = new_words
    print(f"Taking all {len(new_words)} new words")

top_pos = Counter(pos for _, _, _, pos, _ in top_new)
print("Selected new words by POS:")
for p, c in top_pos.most_common():
    print(f"  {p}: {c}")
