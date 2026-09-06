# Deploy SchedMate Backend to Galaxy Cloud

## 3-Minute Setup

### 1. Sign Up (1 minute)
1. Open https://my.galaxycloud.app/signup
2. Click **"Sign up with GitHub"**
3. Authorize with your GitHub account (`jerviesLachnica`)
4. Done — you're logged in

### 2. Create App (2 minutes)
1. Click **"New App"** (top right)
2. Select **"Node.js"**
3. Choose your repository: **`jerviesLachnica/edukasyon`**
4. Set **Root Directory** to: **`backend`**
5. Click **"Deploy"**

Galaxy auto-detects `package.json` and builds immediately.

### 3. Add Environment Variables (before/after deploy)
While the build runs, click **"Environment"** tab and add these:

| Key | Value |
|-----|-------|
| `AI_API_KEY` | *(your HCN API key from Render)* |
| `AI_BASE_URL` | `https://api.hcnsec.cn/v1` |
| `TEXT_MODEL` | `auto` |
| `VISION_MODEL` | `MiniMax-M3` |
| `PORT` | `8080` |
| `SAFETY_REQUEST_TIMEOUT_MS` | `90000` |
| `SAFETY_SCHEDULE_ANALYSIS_REQUEST_TIMEOUT_MS` | `300000` |
| `SAFETY_RATE_PER_MIN` | `60` |
| `SAFETY_DAILY_QUOTA` | `2000` |
| `SAFETY_HOURLY_QUOTA` | `200` |

**To get your `AI_API_KEY`:**
- Go to your Render dashboard → Environment tab → copy the `AI_API_KEY` value

### 4. Get Your URL
Once deploy finishes (~2-3 min), you'll see your live URL:
```
https://<your-app-name>.galaxycloud.app
```

Example: `https://schedmate-backend.galaxycloud.app`

---

## Send Me the URL

Once you have your Galaxy URL, message me the full URL and I'll:
1. Update `build.gradle.kts` to point to it
2. Rebuild the APK
3. Verify it works end-to-end

Done ✓