#!/usr/bin/env python3
import os
import shutil
import sys
import zipfile

zip_path, out_dir = sys.argv[1], sys.argv[2]

os.makedirs(out_dir, exist_ok=True)
with zipfile.ZipFile(zip_path) as zf:
    zf.extractall(out_dir)

dirs = [d for d in os.listdir(out_dir)
        if os.path.isdir(os.path.join(out_dir, d)) and d != "lib"]
assert len(dirs) == 1, "esperava uma unica pasta jre no zip, achei: %s" % dirs

jre = os.path.join(out_dir, "jre")
os.makedirs(jre, exist_ok=True)
src = os.path.join(out_dir, dirs[0])
for entry in os.listdir(src):
    shutil.move(os.path.join(src, entry), os.path.join(jre, entry))
os.rmdir(src)