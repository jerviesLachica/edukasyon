# Galaxy Cloud Deploy Configuration for SchedMate Backend
# Project: edukasyon
# Service: backend

# Deploy steps:
# 1. Go to https://my.galaxycloud.app/signup
# 2. Sign up with GitHub (jerviesLachica)
# 3. Click "New App" -> "Node.js"
# 4. Connect repo: jerviesLachica/edukasyon
# 5. Set Root Directory: backend
# 6. Add these Environment Variables:

env:
  # Required AI Configuration
  AI_API_KEY: "<your-hcnsec-api-key>"
  AI_BASE_URL: "https://api.hcnsec.cn/v1"
  TEXT_MODEL: "auto"
  VISION_MODEL: "MiniMax-M3"
  
  # Server
  PORT: "8080"
  
  # Safety/Rate Limiting (for schedule scanner)
  SAFETY_REQUEST_TIMEOUT_MS: "90000"
  SAFETY_SCHEDULE_ANALYSIS_REQUEST_TIMEOUT_MS: "300000"
  SAFETY_RATE_PER_MIN: "60"
  SAFETY_BURST_PER_MIN: "10"
  SAFETY_DAILY_QUOTA: "2000"
  SAFETY_HOURLY_QUOTA: "200"
  
  # Optional: Firebase (if using auth)
  # FIREBASE_PROJECT_ID: "edukasyon-studentai"
  # FIREBASE_SERVICE_ACCOUNT: "<full-json-service-account>"
  
  # Optional: Web Search
  # TAVILY_API_KEY: "<your-tavily-key>"

# After deploy, your URL will be:
# https://<your-app-name>.galaxycloud.app
# 
# Then send me the URL and I'll update the Android app to use it.