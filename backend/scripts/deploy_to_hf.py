"""
Deploy SchedMate Backend to Hugging Face Spaces
"""
import os
import sys
import shutil
import tempfile
from pathlib import Path
from huggingface_hub import HfApi

SPACE_NAME = "schedmate-backend"
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
        username = user_info.get("name")
        print(f"[✓] Logged in as: {username}")
    except Exception as e:
        print(f"[X] Authentication failed: {e}")
        return False

    repo_id = f"{username}/{SPACE_NAME}"
    hf_subdomain = f"{username.lower()}-{SPACE_NAME.lower()}"
    hf_space_url = f"https://{hf_subdomain}.hf.space"

    print(f"[*] Ensuring Space '{repo_id}' exists (Docker SDK, Public)...")
    try:
        api.create_repo(
            repo_id=repo_id,
            repo_type="space",
            space_sdk="docker",
            private=False,
            exist_ok=True,
        )
        print(f"[✓] Space created/verified: https://huggingface.co/spaces/{repo_id}")
    except Exception as e:
        print(f"[!] Warning on create_repo: {e}")

    try:
        api.update_repo_settings(repo_id=repo_id, private=False, repo_type="space")
    except Exception as e:
        pass

    # 1. Read secrets from backend/.env
    env_path = BACKEND_DIR / ".env"
    secrets = load_env_file(env_path)
    secrets["PORT"] = "7860"
    secrets["RENDER_EXTERNAL_URL"] = hf_space_url

    print(f"[*] Syncing {len(secrets)} secrets to Space Settings...")
    for key, val in secrets.items():
        if not val:
            continue
        try:
            api.add_space_secret(repo_id=repo_id, key=key, value=val)
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
            repo_id=repo_id,
            folder_path=str(staging),
            repo_type="space",
            commit_message="Deploy SchedMate Backend to HF Spaces",
        )
        print(f"[✓] Backend files uploaded successfully!")

    print(f"[*] Space build triggered!")
    print(f"    Space Web: {hf_space_url}")
    print(f"    Health:    {hf_space_url}/health")
    return True

if __name__ == "__main__":
    token = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("HF_TOKEN")
    if not token:
        token = input("Enter your Hugging Face Access Token: ").strip()
    if not token:
        print("Token is required to deploy.")
        sys.exit(1)
    deploy(token)
