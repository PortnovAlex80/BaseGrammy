"""
Lemmatize it_12500_frequency.txt using spaCy Italian model.
Produces clean lemma list with correct POS tagging.
Output: v2_lemmas_raw.txt (rank, original, lemma, POS, tag)
"""
import sys
import os

sys.stdout.reconfigure(encoding='utf-8')

FREQ_FILE = os.path.join(os.path.dirname(__file__), '..', '..', 'it_12500_frequency.txt')
OUTPUT_FILE = os.path.join(os.path.dirname(__file__), 'v2_lemmas_raw.txt')

# Load spaCy Italian model
print("Loading spaCy Italian model...")
import spacy
nlp = spacy.load('it_core_news_lg')
print("Model loaded.")

# Read frequency list
with open(FREQ_FILE, 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

print(f"Frequency list: {len(lines)} lines")

# Lemmatize each word
results = []
for rank_minus_1, line in enumerate(lines):
    word = line.strip()
    if not word:
        continue
    rank = rank_minus_1 + 1

    doc = nlp(word)
    if len(doc) == 0:
        continue

    token = doc[0]
    pos = token.pos_
    tag = token.tag_
    lemma = token.lemma_

    # Map POS to our categories
    # NOUN, VERB, ADJ, ADV are our targets
    # AUX (avere, essere) -> treat as VERB for our purposes
    if pos == 'AUX':
        pos = 'VERB'
    # PROPN (proper nouns) -> keep as NOUN if it's a real word
    # We'll filter names later

    results.append((rank, word, lemma, pos, tag))

# Write raw output
with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
    f.write("rank\toriginal\tlemma\tPOS\ttag\n")
    for rank, word, lemma, pos, tag in results:
        f.write(f"{rank}\t{word}\t{lemma}\t{pos}\t{tag}\n")

print(f"Written {len(results)} entries to {OUTPUT_FILE}")

# Quick stats
from collections import Counter
pos_counts = Counter(pos for _, _, _, pos, _ in results)
print("\nPOS distribution:")
for pos, count in pos_counts.most_common():
    print(f"  {pos}: {count}")

# Show lemma dedup stats
lemma_map = {}  # lemma -> lowest rank, POS
for rank, word, lemma, pos, tag in results:
    key = (lemma, pos)
    if key not in lemma_map or rank < lemma_map[key][0]:
        lemma_map[key] = (rank, word, lemma, pos, tag)

print(f"\nUnique (lemma, POS) pairs: {len(lemma_map)}")

# Target POS only
target_pos = {'NOUN', 'VERB', 'ADJ', 'ADV'}
target_lemmas = {k: v for k, v in lemma_map.items() if k[1] in target_pos}
print(f"Target POS (NOUN/VERB/ADJ/ADV) unique lemmas: {len(target_lemmas)}")

target_pos_counts = Counter(pos for (_, pos), _ in target_lemmas.items())
print("  Breakdown:")
for pos, count in target_pos_counts.most_common():
    print(f"    {pos}: {count}")
