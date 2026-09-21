"""
Deploy SchedMate Backend to Hugging Face Spaces
"""
import os
import sys
import shutil
import tempfile
from pathlib import Path
from huggingface_hub import HfApi

REPO_ID = "jerviesLachica/schedmate-backend"
BACKEND_DIR = Path(__file__).resolve().parent.parent

def load_env_file(filepath):
    secrets = {}
    if not filepath.exists():
        return secrets
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip()
            if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                val = val[1:-1]
            if key:
                secrets[key] = val
    return secrets

def deploy(token):
    api = HfApi(token=token)

    print(f"[*] Authenticating with Hugging Face...")
    try:
        user_info = api.whoami()
        print(f"[✓] Logged in as: {user_info.get('name', 'User')}")
    except Exception as e:
        print(f"[X] Authentication failed: {e}")
        return False

    print(f"[*] Making Space '{REPO_ID}' Public (required for Android app API access)...")
    try:
        api.update_repo_settings(repo_id=REPO_ID, private=False, repo_type="space")
        print(f"[✓] Space is now Public. (Secrets remain 100% private in Space settings).")
    except Exception as e:
        print(f"[!] Warning updating visibility: {e}")

    # 1. Read secrets from backend/.env
    env_path = BACKEND_DIR / ".env"
    secrets = load_env_file(env_path)
    # Ensure PORT is 7860 for HF Spaces
    secrets["PORT"] = "7860"
    secrets["RENDER_EXTERNAL_URL"] = f"https://{REPO_ID.replace('/', '-')}.hf.space"

    print(f"[*] Syncing {len(secrets)} secrets to Space Settings...")
    for key, val in secrets.items():
        if not val:
            continue
        try:
            api.add_space_secret(repo_id=REPO_ID, key=key, value=val)
            print(f"    + Added secret: {key}")
        except Exception as e:
            print(f"    ! Error setting secret {key}: {e}")

    # 2. Prepare files to upload in a clean staging directory
    print(f"[*] Staging backend files for upload...")
    with tempfile.TemporaryDirectory() as staging_dir:
        staging = Path(staging_dir)

        # Copy README from hf-space-readme.md (which contains the required YAML header)
        readme_src = BACKEND_DIR / "hf-space-readme.md"
        if readme_src.exists():
            readme_content = readme_src.read_text(encoding="utf-8")
            # Ensure app_port is in frontmatter
            if "app_port: 7860" not in readme_content:
                readme_content = readme_content.replace("sdk: docker", "sdk: docker\napp_port: 7860")
            (staging / "README.md").write_text(readme_content, encoding="utf-8")

        # Copy Dockerfile
        shutil.copy2(BACKEND_DIR / "Dockerfile", staging / "Dockerfile")
        shutil.copy2(BACKEND_DIR / "package.json", staging / "package.json")
        shutil.copy2(BACKEND_DIR / "package-lock.json", staging / "package-lock.json")
        shutil.copy2(BACKEND_DIR / "server.js", staging / "server.js")
        shutil.copy2(BACKEND_DIR / ".dockerignore", staging / ".dockerignore")

        # Copy directories
        dirs_to_copy = ["abuse", "ai", "auth", "config", "prompts", "safety", "updates", "util", "validation"]
        for d in dirs_to_copy:
            src_sub = BACKEND_DIR / d
            if src_sub.exists():
                shutil.copytree(src_sub, staging / d)

        print(f"[*] Uploading files to Hugging Face Space...")
        api.upload_folder(
            repo_id=REPO_ID,
            folder_path=str(staging),
            repo_type="space",
            commit_message="Deploy SchedMate Backend to HF Spaces",
        )
        print(f"[✓] Backend files uploaded successfully!")

    print(f"[*] Space build triggered!")
    print(f"    Space URL: https://huggingface.co/spaces/{REPO_ID}")
    print(f"    API URL:   https://{REPO_ID.replace('/', '-')}.hf.space/health")
    return True

if __name__ == "__main__":
    token = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("HF_TOKEN")
    if not token:
        token = input("Enter your Hugging Face Access Token (with write permissions): ").strip()
    if not token:
        print("Token is required to deploy.")
        sys.exit(1)
    deploy(token)
