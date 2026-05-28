import zipfile
import os

# Use absolute paths
base_dir = os.path.dirname(os.path.abspath(__file__))
source_dir = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'grammarmate', 'packs', 'fix_allegory')
output_zip = os.path.join(base_dir, 'app', 'src', 'main', 'assets', 'grammarmate', 'packs', 'ALLEGORY_PACK.zip')

print(f'Source dir: {source_dir}')
print(f'Output zip: {output_zip}')
print(f'Source exists: {os.path.exists(source_dir)}')

# Create ZIP with forward slashes
with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
    count = 0
    for root, dirs, files in os.walk(source_dir):
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, source_dir).replace('\\', '/')
            print(f'Adding: {arcname}')
            zf.write(file_path, arcname)
            count += 1

print(f'ZIP created: {output_zip} with {count} files')
