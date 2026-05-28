import zipfile
import os

zip_path = 'd:/Development/BaseGrammy/app/src/main/assets/grammarmate/packs/ALLEGORY_PACK.zip'
extract_path = 'd:/Development/BaseGrammy/app/src/main/assets/grammarmate/packs/fix_allegory/'

with zipfile.ZipFile(zip_path, 'r') as z:
    for member in z.namelist():
        if not member.endswith('/'):
            # Fix backslashes to forward slashes
            fixed_name = member.replace('\\', '/')
            dest_path = os.path.join(extract_path, fixed_name)
            os.makedirs(os.path.dirname(dest_path), exist_ok=True)

            with z.open(member) as source:
                with open(dest_path, 'wb') as target:
                    target.write(source.read())

    print(f'Extracted {len([m for m in z.namelist() if not m.endswith("/")])} files')
