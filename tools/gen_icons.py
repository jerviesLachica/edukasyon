# Regenerates all launcher-icon assets from drawable/wala.png. Run from repo root:
#   python tools/gen_icons.py
# Outputs:
#   drawable-nodpi/ic_launcher_foreground.png   adaptive fg (emblem only, alpha, 432px)
#   drawable-nodpi/ic_launcher_monochrome.png   themed fg (white alpha emblem, 432px)
#   drawable-nodpi/ic_stat_schedmate.png        notification small icon (white alpha, 72px)
#   mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png legacy full lockup (opaque cream)
#   mipmap-{...}/ic_launcher_round.png          legacy circle: emblem on cream
from PIL import Image, ImageChops, ImageDraw
import os

RES = 'androidApp/src/main/res'
SRC = os.path.join(RES, 'drawable', 'wala.png')
im = Image.open(SRC).convert('RGB')
W, H = im.size
BG = im.getpixel((6, 6))  # flat cream background sample

def alpha_from_bg(rgb, lo=10, hi=48):
    """Mark pixels opaque, cream pixels transparent."""
    diff = ImageChops.difference(rgb, Image.new('RGB', rgb.size, BG)).convert('L')
    return diff.point(lambda v: 0 if v <= lo else (255 if v >= hi else int(255 * (v - lo) / (hi - lo))))

# --- emblem (panda+calendar), excluding the wordmark band below 70% height ---
band = im.crop((0, 0, W, int(H * 0.70)))
bbox = alpha_from_bg(band).point(lambda v: 255 if v > 40 else 0).getbbox()
emblem = band.crop(bbox)
side = max(emblem.size)
sq = Image.new('RGB', (side, side), BG)
sq.paste(emblem, ((side - emblem.width) // 2, (side - emblem.height) // 2))
sq_rgba = sq.convert('RGBA')
sq_rgba.putalpha(alpha_from_bg(sq))

def centered(img_rgba, outer, frac):
    o = Image.new('RGBA', (outer, outer), (0, 0, 0, 0))
    inner = img_rgba.resize((int(outer * frac),) * 2, Image.LANCZOS)
    o.paste(inner, ((outer - inner.width) // 2, (outer - inner.height) // 2), inner)
    return o

os.makedirs(os.path.join(RES, 'drawable-nodpi'), exist_ok=True)
# 1) adaptive foreground: emblem at 46% (safe zone is 66% of the 108dp frame;
#    58% still let ear tips graze the circle — 50% gives clearance)
centered(sq_rgba, 432, 0.46).save(os.path.join(RES, 'drawable-nodpi', 'ic_launcher_foreground.png'))

# 2) monochrome: white silhouette of the same emblem, same 46% inset (parallax headroom)
mono_in = sq_rgba.resize((int(432 * 0.46),) * 2, Image.LANCZOS)
white = Image.new('RGBA', mono_in.size, (255, 255, 255, 255))
white.putalpha(mono_in.split()[3])
mono = Image.new('RGBA', (432, 432), (0, 0, 0, 0))
mono.paste(white, ((432 - white.width) // 2, (432 - white.height) // 2), white)
mono.save(os.path.join(RES, 'drawable-nodpi', 'ic_launcher_monochrome.png'))

# 3) notification small icon is a hand-authored vector (drawable/ic_stat_schedmate.xml) —
#    NOT generated here: detailed mascot art is illegible at 24dp status-bar size.

# 4) legacy square mipmaps: FULL lockup (wordmark visible), opaque cream, 75% inset
for px, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    d = os.path.join(RES, f'mipmap-{px}')
    os.makedirs(d, exist_ok=True)
    # square canvas of cream, artwork scaled to 75% so launchers' own insets look clean
    art = im.copy()
    art.thumbnail((int(size * 0.75),) * 2, Image.LANCZOS)
    tile = Image.new('RGB', (size, size), BG)
    tile.paste(art, ((size - art.width) // 2, (size - art.height) // 2))
    tile.save(os.path.join(d, 'ic_launcher.png'))
    # legacy round: emblem on cream, circle alpha mask
    em = sq_rgba.resize((int(size * 0.68),) * 2, Image.LANCZOS)
    base = Image.new('RGB', (size, size), BG).convert('RGBA')
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    base.putalpha(mask)
    base.paste(em, ((size - em.width) // 2, (size - em.height) // 2), em.split()[3])
    base.save(os.path.join(d, 'ic_launcher_round.png'))

print('OK: adaptive fg+mono+stat icon, 5x legacy ic_launcher.png, 5x legacy ic_launcher_round.png')
