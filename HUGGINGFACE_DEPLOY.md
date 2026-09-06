# Hugging Face Spaces Deployment Guide

## 3-Minute Setup (No Credit Card Required)

### Step 1: Create a Hugging Face Account (1 min)
1. Go to https://huggingface.co/join
2. Sign up with email or GitHub OAuth
3. Verify email if prompted

### Step 2: Create a New Space (1 min)
1. Go to https://huggingface.co/new-space
2. **Space name:** `schedmate-backend` (must be unique — try `schedmate-backend-jervies` if taken)
3. **License:** MIT
4. **SDK:** Docker
5. **Space hardware:** CPU basic (FREE)
6. Click **Create Space**

### Step 3: Push Your Code to the Space (1 min)

In your terminal, from the `backend/` directory:

```bash
# Create a fresh clone of the Space repo
git clone https://huggingface.co/spaces/YOUR_USERNAME/schedmate-backend hf-space
cd hf-space

# Copy the Dockerfile and code from your project
# (these are already in your repo, just copy them in)
cp /c/Users/HP/AndroidStudioProjects/edukasyon/backend/Dockerfile .
cp /c/Users/HP/AndroidStudioProjects/edukasyon/backend/.dockerignore .
cp -r /c/Users/HP/AndroidStudioProjects/edukasyon/backend/* . 2>/dev/null || true

# Remove .git (we're inside a new git repo)
rm -rf .git

# Add HF Space metadata
cat > README.md << 'EOF'
---
title: SchedMate Backend
sdk: docker
emoji: 🤖
colorFrom: blue
colorTo: purple
short_description: Secure AI proxy backend for SchedMate Android app
---
EOF
cat README.md
cat /c/Users/HP/AndroidStudioProjects/edukasyon/backend/hf-space-readme.md >> README.md

# Commit and push
git add .
git commit -m "Initial deploy of SchedMate backend"
git push https://YOUR_USERNAME:YOUR_HF_TOKEN@huggingface.co/spaces/YOUR_USERNAME/schedmate-backend main
```

### Step 4: Add Environment Variables
After the Space is created:
1. Go to your Space page → **Settings** tab
2. Scroll to **Variables and secrets**
3. Click **New variable** and add each:

| Variable name | Type | Value |
|---------------|------|-------|
| `AI_API_KEY` | Secret | *(your HCN API key)* |
| `AI_BASE_URL` | Variable | `https://api.hcnsec.cn/v1` |
| `TEXT_MODEL` | Variable | `auto` |
| `VISION_MODEL` | Variable | `MiniMax-M3` |
| `SAFETY_REQUEST_TIMEOUT_MS` | Variable | `90000` |
| `SAFETY_SCHEDULE_ANALYSIS_REQUEST_TIMEOUT_MS` | Variable | `300000` |
| `SAFETY_RATE_PER_MIN` | Variable | `60` |
| `SAFETY_DAILY_QUOTA` | Variable | `2000` |
| `SAFETY_HOURLY_QUOTA` | Variable | `200` |

### Step 5: Get Your URL

Your Space URL will be:
```
https://YOUR_USERNAME-schedmate-backend.hf.space
```

Test it:
```bash
curl https://YOUR_USERNAME-schedmate-backend.hf.space/health
```

### Step 6: Optional — Keep Space Awake

Free Spaces sleep after **48 hours** of inactivity. To prevent this:

1. Sign up at https://uptimerobot.com (free)
2. Add a new monitor: HTTP(s) GET, every 30 minutes
3. URL: `https://YOUR_USERNAME-schedmate-backend.hf.space/health`

This keeps the Space active 24/7.

### Step 7: Send Me the URL

Once your Space is live, message me the URL. I'll update the Android app and rebuild the APK in 30 seconds.

---

## Troubleshooting

**Build fails?**
- Check Space logs: click **Logs** tab on the Space page
- Most common: missing env vars → add them in Settings

**Container keeps restarting?**
- Check that `AI_API_KEY` is correct
- Visit `https://your-space.hf.space/health` to see the error

**Space won't sleep?**
- It will sleep after 48 hours without requests — use UptimeRobot to keep it awake

## Cost

- **$0/month** — Hugging Face Spaces free tier includes:
  - 2 vCPU, 16 GB RAM, 50 GB disk
  - Sleeps after 48 hours (UptimeRobot keeps it awake)
  - No credit card required
  - Unlimited Spaces (one per project)

## Architecture

- **Runtime:** Node.js 18 via Docker
- **Process:** Long-running Express server on port 7860
- **In-memory state:** Map for async schedule scan jobs (5-min TTL)
- **Health endpoint:** `/health`
- **API routes:** `/api/ai/*`
