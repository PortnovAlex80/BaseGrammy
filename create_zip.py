import zipfile
import os

base_path = 'd:/Development/BaseGrammy/app/src/main/assets/grammarmate/packs/fix_allegory/'
zip_path = 'd:/Development/BaseGrammy/app/src/main/assets/grammarmate/packs/ALLEGORY_PACK.zip'

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(base_path):
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, base_path)
            z.write(file_path, arcname)

print(f'Created ZIP with {len([f for _, _, files in os.walk(base_path) for f in files])} files')
