from PIL import Image, ImageDraw
import math

SIZE = 512
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

INDIGO_DARK = (40, 50, 140)
INDIGO_LIGHT = (100, 115, 210)
INDIGO_GLOW = (120, 140, 230)
WHITE = (255, 255, 255)
CYAN = (80, 200, 255)

center = SIZE // 2
radius = SIZE // 2 - 4

# Circular background gradient
for y in range(SIZE):
    for x in range(SIZE):
        dist = math.sqrt((x - center)**2 + (y - center)**2)
        if dist <= radius:
            t = dist / radius
            r = int(INDIGO_DARK[0] + (INDIGO_LIGHT[0] - INDIGO_DARK[0]) * (1 - t) * 0.6)
            g = int(INDIGO_DARK[1] + (INDIGO_LIGHT[1] - INDIGO_DARK[1]) * (1 - t) * 0.6)
            b = int(INDIGO_DARK[2] + (INDIGO_LIGHT[2] - INDIGO_DARK[2]) * (1 - t) * 0.6)
            img.putpixel((x, y), (r, g, b, 255))

# Head
head_size = 140
head_cx, head_cy = center, center - 15
for y in range(head_cy - head_size, head_cy + head_size + 1):
    for x in range(head_cx - head_size, head_cx + head_size + 1):
        dx = abs(x - head_cx)
        dy = abs(y - head_cy)
        corner_r = 40
        in_rect = dx <= head_size and dy <= head_size
        in_corner = dx > head_size - corner_r and dy > head_size - corner_r
        if in_rect and (not in_corner or (dx - (head_size - corner_r))**2 + (dy - (head_size - corner_r))**2 <= corner_r**2):
            t_y = (y - (head_cy - head_size)) / (2 * head_size)
            r = int(INDIGO_LIGHT[0] + 30 * (1 - t_y))
            g = int(INDIGO_LIGHT[1] + 35 * (1 - t_y))
            b = int(INDIGO_LIGHT[2] + 50 * (1 - t_y))
            img.putpixel((x, y), (r, g, b, 255))

# Inner face panel
inner_size = 100
for y in range(head_cy - inner_size, head_cy + inner_size + 1):
    for x in range(head_cx - inner_size, head_cx + inner_size + 1):
        dx = abs(x - head_cx)
        dy = abs(y - head_cy)
        corner_r = 30
        in_rect = dx <= inner_size and dy <= inner_size
        in_corner = dx > inner_size - corner_r and dy > inner_size - corner_r
        if in_rect and (not in_corner or (dx - (inner_size - corner_r))**2 + (dy - (inner_size - corner_r))**2 <= corner_r**2):
            img.putpixel((x, y), (35, 45, 120, 255))

# Eyes
eye_y = head_cy - 25
eye_spacing = 48
eye_r = 24

for side in [-1, 1]:
    ex = head_cx + side * eye_spacing
    ey = eye_y
    for y in range(ey - eye_r - 10, ey + eye_r + 11):
        for x in range(ex - eye_r - 10, ex + eye_r + 11):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                dist = math.sqrt((x - ex)**2 + (y - ey)**2)
                if eye_r <= dist <= eye_r + 10:
                    alpha = int(180 * (1 - (dist - eye_r) / 10))
                    img.putpixel((x, y), (*INDIGO_GLOW, alpha))
    for y in range(ey - eye_r, ey + eye_r + 1):
        for x in range(ex - eye_r, ex + eye_r + 1):
            if 0 <= x < SIZE and 0 <= y < SIZE and (x - ex)**2 + (y - ey)**2 <= eye_r**2:
                img.putpixel((x, y), (20, 25, 70, 255))
    pupil_r = 14
    for y in range(ey - pupil_r, ey + pupil_r + 1):
        for x in range(ex - pupil_r, ex + pupil_r + 1):
            if 0 <= x < SIZE and 0 <= y < SIZE and (x - ex)**2 + (y - ey)**2 <= pupil_r**2:
                img.putpixel((x, y), CYAN)
    for spot in [(-5, -6, 6), (5, -3, 4)]:
        sx, sy, sr = ex + spot[0], ey + spot[1], spot[2]
        for y in range(sy - sr, sy + sr + 1):
            for x in range(sx - sr, sx + sr + 1):
                if 0 <= x < SIZE and 0 <= y < SIZE and (x - sx)**2 + (y - sy)**2 <= sr**2:
                    img.putpixel((x, y), WHITE)

# SMILE - use ImageDraw arc for a clean upward curve
draw = ImageDraw.Draw(img)
mouth_width = 80
mouth_depth = 20
mouth_y = head_cy + 38
bbox = [head_cx - mouth_width, mouth_y - mouth_depth, head_cx + mouth_width, mouth_y + mouth_depth]
draw.arc(bbox, start=0, end=180, fill=CYAN, width=5)

# Rosy cheeks
cheek_y = head_cy + 5
cheek_x_offset = 72
cheek_r = 16
for side in [-1, 1]:
    cx = head_cx + side * cheek_x_offset
    cy = cheek_y
    for y in range(cy - cheek_r, cy + cheek_r + 1):
        for x in range(cx - cheek_r, cx + cheek_r + 1):
            if 0 <= x < SIZE and 0 <= y < SIZE and (x - cx)**2 + (y - cy)**2 <= cheek_r**2:
                img.putpixel((x, y), (255, 130, 150, 80))

# Antenna
ant_top = head_cy - head_size - 18
for y in range(ant_top + 8, head_cy - head_size):
    t = (y - (ant_top + 8)) / (head_cy - head_size - ant_top - 8)
    x = head_cx + int(3 * t)
    for w in range(2):
        if 0 <= x + w < SIZE and 0 <= y < SIZE:
            img.putpixel((x + w, y), (*INDIGO_LIGHT, 255))
for y in range(ant_top - 8, ant_top + 9):
    for x in range(head_cx - 8, head_cx + 9):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            if (x - head_cx)**2 + (y - ant_top)**2 <= 64:
                glow = int(200 + 55 * (1 - math.sqrt((x - head_cx)**2 + (y - ant_top)**2) / 8))
                img.putpixel((x, y), (min(255, glow), min(255, glow + 20), 255))

# Neck
neck_y = head_cy + head_size + 5
for y in range(neck_y, neck_y + 20):
    for x in range(head_cx - 40, head_cx + 41):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            t = (y - neck_y) / 20
            w = int(40 * (1 - t * 0.5))
            if abs(x - head_cx) <= w:
                r = int(INDIGO_DARK[0] + 20 * (1 - t))
                g = int(INDIGO_DARK[1] + 25 * (1 - t))
                b = int(INDIGO_DARK[2] + 40 * (1 - t))
                img.putpixel((x, y), (r, g, b, 255))

# Outer glow
for y in range(max(0, center - radius - 18), min(SIZE, center + radius + 19)):
    for x in range(max(0, center - radius - 18), min(SIZE, center + radius + 19)):
        dx = x - center
        dy = y - center
        dist = math.sqrt(dx**2 + dy**2)
        if radius + 1 <= dist <= radius + 18:
            alpha = int(70 * (1 - (dist - radius) / 18))
            existing = img.getpixel((x, y))
            if existing[3] == 0:
                img.putpixel((x, y), (*INDIGO_GLOW[:3], alpha))

output_path = r"C:\Users\HP\AndroidStudioProjects\edukasyon\androidApp\src\main\res\drawable-nodpi\jarvis_avatar.png"
img.save(output_path, "PNG")
print("Saved to:", output_path)