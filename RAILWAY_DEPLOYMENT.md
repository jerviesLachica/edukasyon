# Railway Deployment Guide

## Quick Start (5 minutes)

### 1. Create Railway Project
```bash
# Install Railway CLI (if needed)
npm install -g @railway/cli

# Login to Railway
railway login

# Link to Railway project (creates new if needed)
cd backend
railway link

# Set project name (e.g., "schedmate-backend")
```

### 2. Configure Environment Variables
Railway will prompt you to set variables, or use the dashboard:

```bash
# Required
railway variables set AI_API_KEY=sk-...your-key...
railway variables set AI_BASE_URL=https://api.hcnsec.cn/v1
railway variables set VISION_MODEL=MiniMax-M3
railway variables set TEXT_MODEL=auto

# Optional but recommended
railway variables set SAFETY_REQUEST_TIMEOUT_MS=90000
railway variables set SAFETY_SCHEDULE_ANALYSIS_REQUEST_TIMEOUT_MS=300000
railway variables set SAFETY_RATE_PER_MIN=60
railway variables set SAFETY_DAILY_QUOTA=2000
```

### 3. Deploy
```bash
# Push to Railway (auto-deploys from git)
git push origin master

# Or trigger manual deploy
railway up
```

### 4. Get Public URL
```bash
# Show your Railway URL
railway status

# Example output:
# URL: https://your-project-xxx.railway.app
```

### 5. Update Android App
Edit `androidApp/build.gradle.kts`:
```kotlin
buildConfigField("String", "AI_BACKEND_URL", "\"https://your-project-xxx.railway.app/\"")
```

Then rebuild:
```bash
./gradlew :androidApp:assembleDebug
```

---

## Why Railway > Render

| Feature | Render | Railway |
|---------|--------|---------|
| **Free tier** | 15 min auto-sleep | $5/month credit (practically free) |
| **Startup time** | 30s+ (cold start) | ~5s |
| **Bandwidth** | 100 GB/month | Included in credit |
| **UI** | Clunky | Modern & fast |
| **Deploy speed** | Slower | Faster |
| **Environment** | Limited | Better |

---

## Troubleshooting

### "Build failed"
- Check `railway logs --follow` for errors
- Verify `package.json` has `"main": "server.js"` and `"start": "node server.js"`
- Ensure Node 18+ (Railway default)

### "Connection refused"
- Verify `AI_API_KEY` is set in Railway dashboard
- Check logs: `railway logs --follow`
- Confirm backend is running: `railway status`

### "Slow first request"
- First request wakes the dyno (normal on free tier)
- Subsequent requests are fast (~100ms to backend)
- Keep-alive headers in app will keep it warm

---

## Local Testing Before Deploy

```bash
cd backend

# Install dependencies
npm install

# Set env vars
export AI_API_KEY=sk-...
export AI_BASE_URL=https://api.hcnsec.cn/v1
export VISION_MODEL=MiniMax-M3

# Run locally
npm start

# Test endpoint
curl -X POST http://localhost:8080/api/ai/schedule-analysis \
  -H "Content-Type: application/json" \
  -d '{"imageBase64":"..."}'
```

---

## Monitoring

### View Logs
```bash
railway logs --follow
```

### View Metrics
- CPU, memory, network in Railway dashboard
- Live logs with filters

### Health Check
```bash
curl https://your-project-xxx.railway.app/health
```

---

## Cost Estimate

- **Free tier**: $5/month credit
- **Usage**: Backend runs ~24/7, typical usage = $2–4/month
- **Result**: Essentially free for SchedMate

---

## Next Steps

1. Create Railway account: https://railway.app/
2. Run `railway link` in the backend directory
3. Set environment variables
4. Push to master to trigger deploy
5. Update Android BuildConfig with new URL
6. Rebuild and test APK

Done! Your backend is now on Railway with better uptime and no sleep limits.
