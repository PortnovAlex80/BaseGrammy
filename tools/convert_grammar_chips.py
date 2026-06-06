#!/usr/bin/env python3
"""
Convert grammar chip markdown files to JSON format for embedding in packs.
"""

import os
import re
import json
from pathlib import Path

def parse_examples(examples_text):
    """Parse examples section into structured format."""
    if not examples_text:
        return []

    examples = []
    lines = examples_text.strip().split('\n')

    for line in lines:
        line = line.strip()
        if line.startswith('- '):
            # Extract Italian text from backticks
            match = re.search(r'`([^`]+)`', line)
            if match:
                it_text = match.group(1)
                # Extract Russian translation if available
                remaining = line.replace(match.group(0), '').strip()
                ru_text = ''
                if remaining.startswith('-'):
                    ru_text = remaining[1:].strip()
                elif remaining.startswith('—'):
                    ru_text = remaining[1:].strip()
                examples.append({'it': it_text, 'ru': ru_text, 'note': ''})

    return examples

def parse_grammar_chip(md_content):
    """Parse grammar chip markdown into structured data."""
    lines = md_content.strip().split('\n')

    # Extract title and key
    title_line = lines[0]
    key = title_line.replace('# ', '').split('-')[0].strip()
    title = title_line.replace('# ', '').strip()

    sections = {}
    current_section = None
    current_content = []

    section_mapping = {
        'Core Idea': 'coreIdea',
        'Суть': 'coreIdea',
        'РЎСѓС‚СЊ': 'coreIdea',
        'Form': 'form',
        'Forms': 'form',
        'Formula': 'form',
        'Формула': 'form',
        'Р¤РѕСЂРјСѓР»Р°': 'form',
        'Base': 'base',
        'База': 'base',
        'Р‘Р°Р·Р°': 'base',
        'Examples': 'examples',
        'Примеры': 'examples',
        'РџСЂРёРјРµСЂС‹': 'examples',
        'Watch Out': 'watchOut',
        "Don't Confuse": 'watchOut',
        'Не путать': 'watchOut',
        'РќРµ РїСѓС‚Р°С‚СЊ': 'watchOut',
    }

    for line in lines[1:]:
        if line.startswith('## '):
            # Save previous section
            if current_section and current_content:
                sections[current_section] = '\n'.join(current_content).strip()

            # Start new section
            section_name = line.replace('## ', '').strip()
            current_section = section_mapping.get(section_name, section_name)
            current_content = []
        elif current_section:
            current_content.append(line)

    # Save last section
    if current_section and current_content:
        sections[current_section] = '\n'.join(current_content).strip()

    return {
        'key': key,
        'title': title,
        'essence': sections.get('coreIdea', ''),
        'formula': sections.get('form', '') if sections.get('form') else None,
        'base': sections.get('base', '') if sections.get('base') else None,
        'examples': sections.get('examples', ''),
        'dontConfuse': sections.get('watchOut', '') if sections.get('watchOut') else None
    }

def main():
    chips_dir = Path('docs/lesson-methodology/italian/grammar_chips')
    output_dir = Path('docs/lesson-methodology/italian/grammar_chips_json')

    output_dir.mkdir(exist_ok=True)

    for md_file in sorted(chips_dir.glob('GRAMMAR_CHIP_*.md')):
        print(f"Converting {md_file.name}...")

        with open(md_file, 'r', encoding='utf-8') as f:
            content = f.read()

        chip_data = parse_grammar_chip(content)

        # Parse examples into structured format
        examples_text = chip_data.pop('examples', '')
        chip_data['examples'] = parse_examples(examples_text)

        # Extract chip number for filename
        match = re.search(r'GRAMMAR_CHIP_(\d+)', md_file.name)
        if match:
            chip_num = match.group(1)
            json_file = output_dir / f'grammar_chip_{chip_num}.json'

            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(chip_data, f, ensure_ascii=False, indent=2)

            print(f"  -> {json_file}")

if __name__ == '__main__':
    main()