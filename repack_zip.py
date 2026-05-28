#!/usr/bin/env python3
import zipfile
import os
import sys

def create_zip(source_dir, output_zip):
    """Create ZIP file with forward slashes."""
    with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                file_path = os.path.join(root, file)
                arcname = os.path.relpath(file_path, source_dir)
                # Ensure forward slashes
                arcname = arcname.replace('\\', '/')
                print(f"Adding: {arcname}")
                zipf.write(file_path, arcname)

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: repack_zip.py <source_dir> <output_zip>")
        sys.exit(1)

    source_dir = sys.argv[1]
    output_zip = sys.argv[2]

    create_zip(source_dir, output_zip)
    print(f"Created: {output_zip}")
