import sys, glob, os
from PIL import Image, ImageDraw
files = sys.argv[2:]
out = sys.argv[1]
cols = 2
tw, th = 960, 540
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (tw * cols, th * rows), (0, 0, 0))
for i, fp in enumerate(files):
    im = Image.open(fp).convert("RGB").resize((tw, th))
    d = ImageDraw.Draw(im); d.rectangle((0, 0, 110, 34), fill=(0, 0, 0)); d.text((8, 8), os.path.basename(fp), fill=(255, 255, 0))
    sheet.paste(im, ((i % cols) * tw, (i // cols) * th))
sheet.save(out)
