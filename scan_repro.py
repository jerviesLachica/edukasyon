"""Synthetic class-schedule scan repro: builds a PNG timetable, POSTs it to
/live or local /api/ai/schedule-analysis, prints status + body."""
import base64
import io
import json
import sys
import urllib.request

TARGET = sys.argv[1] if len(sys.argv) > 1 else "https://studentai-backend-ha0z.onrender.com"

def build_schedule_png():
    from PIL import Image, ImageDraw
    W, H = 1200, 800
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 90], fill=(30, 60, 120))
    d.text((40, 25), "CLASS SCHEDULE - 1st Semester", fill="white")
    days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
    rows = [
        ("08:00-09:30", "Programming 2", "Juan Santos", "Rm 304"),
        ("10:00-11:30", "Database Management", "Maria Cruz", "Rm 201"),
        ("13:00-14:30", "Calculus I", "Jose Reyes", "Rm 105"),
    ]
    y = 110
    d.text((40, y), "TIME", fill="black")
    for i, day in enumerate(days):
        d.text((220 + i * 190, y), day, fill="black")
    y += 40
    for t, subj, teacher, room in rows:
        d.text((40, y), t, fill="black")
        d.text((220, y), f"{subj} / {teacher} / {room}", fill="black")
        y += 60
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()

def main():
    b64 = build_schedule_png()
    print(f"image bytes: {len(base64.b64decode(b64))}")
    payload = json.dumps({"imageBase64": b64}).encode()
    req = urllib.request.Request(
        TARGET + "/api/ai/schedule-analysis",
        data=payload,
        headers={"Content-Type": "application/json", "X-Device-Id": "hermes-repro-1"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=150) as res:
            print("STATUS:", res.status)
            print(res.read().decode()[:3000])
    except Exception as e:
        body = ""
        try:
            body = e.read().decode()[:3000]  # HTTPError
        except Exception:
            pass
        print("ERROR:", type(e).__name__, getattr(e, "code", ""), str(e)[:500])
        if body:
            print("BODY:", body)

main()
