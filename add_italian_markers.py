#!/usr/bin/env python3
"""
Add Italian language markers to story files.
Wraps italicized Italian words in {it}...{/it} tags.
"""

import re
import sys

# Common Italian infinitives and words from the stories
ITALIAN_WORDS = {
    # Infinitives
    'essere', 'vedere', 'sapere', 'andare', 'restare', 'sparire',
    'venire', 'parlare', 'capire', 'finire', 'partire', 'mangiare',
    'camminare', 'studiare', 'viaggiare', 'togliere', 'avere', 'volere',
    'potere', 'dovere', 'dire', 'fare', 'dire', 'correre', 'scrivere',
    'leggere', 'sentire', 'capire', 'uscire', 'entrare', 'pensare',
    'credere', 'conoscere', 'sapere', 'volere', 'potere', 'dovere',

    # Words and phrases
    'vedo', 'vede', 'vedi', 'vediamo', 'vedete', 'vedono',
    'sono', 'sei', 'è', 'siamo', 'siete', 'sono',
    'ho', 'hai', 'ha', 'abbiamo', 'avete', 'hanno',
    'vado', 'vai', 'va', 'andiamo', 'andate', 'vanno',
    'vengo', 'vieni', 'viene', 'veniamo', 'venite', 'vengono',
    'parlo', 'parli', 'parla', 'parliamo', 'parlate', 'parlano',
    'penso', 'pensi', 'pensa', 'pensiamo', 'pensate', 'pensano',
    'credo', 'credi', 'crede', 'crediamo', 'credete', 'credono',

    # Question words
    'chi', 'cosa', 'dove', 'quando', 'come', 'perché', 'quanto',

    # Articles and prepositions
    'il', 'lo', 'la', 'i', 'gli', 'le',
    'un', 'uno', 'una',
    'di', 'a', 'da', 'in', 'con', 'su', 'per', 'tra', 'fra',

    # Nouns and examples
    'luce', 'stella', 'piacere', 'tempo', 'mondo', 'lingua', 'nome',
    'gente', 'amore', 'vita', 'morte', 'tempo', 'notte', 'giorno',

    # Adjectives
    'bello', 'bella', 'belli', 'belle',
    'caldo', 'calda', 'caldi', 'calde',
    'freddo', 'fredda', 'freddi', 'fredde',
    'grande', 'grandi', 'piccolo', 'piccola', 'piccoli', 'piccole',
}

def process_story(content):
    """Process story content and add Italian markers."""

    # Pattern for italic text: *text*
    # We need to be careful not to match markdown headers or other patterns

    lines = content.split('\n')
    result = []

    for line in lines:
        # Skip code blocks
        if line.strip().startswith('```'):
            result.append(line)
            continue

        # Process italic patterns in the line
        # Pattern: *word(s)* where word(s) might be Italian
        processed_line = line

        # Find all italic segments
        italic_pattern = r'\*([^*]+)\*'

        def replace_italic(match):
            text = match.group(1).strip()

            # Check if this is Italian (single word or known phrase)
            words = text.split()

            # Simple heuristic: if all words are Italian-like or it's a known phrase
            is_italian = False

            if len(words) == 1 and words[0].lower() in ITALIAN_WORDS:
                is_italian = True
            elif len(words) <= 3:  # Short phrases (max 3 words)
                # Check if contains Italian words
                italian_count = sum(1 for w in words if w.lower().strip('.,!?;:') in ITALIAN_WORDS)
                if italian_count > 0 or any(w[0].isupper() and w.lower().strip('.,!?;:') in ITALIAN_WORDS for w in words):
                    is_italian = True

            if is_italian:
                # Preserve original punctuation and spacing
                return f'{{{{it}}}}{text}{{{{/it}}}}'
            else:
                # Keep original markdown
                return match.group(0)

        processed_line = re.sub(italic_pattern, replace_italic, processed_line)
        result.append(processed_line)

    return '\n'.join(result)

def main():
    if len(sys.argv) < 3:
        print("Usage: add_italian_markers.py <input_file> <output_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    processed = process_story(content)

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(processed)

    print(f"Processed {input_file} -> {output_file}")

    # Show some examples
    sample_lines = processed.split('\n')[:20]
    print("\nSample output:")
    for line in sample_lines:
        if '{it}' in line:
            print(line)

if __name__ == '__main__':
    main()
