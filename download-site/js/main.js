const ICONS = {
  ios: `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>`,
  android: `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85a.637.637 0 0 0-.83.22l-1.88 3.24a11.463 11.463 0 0 0-8.94 0L5.65 5.67a.643.643 0 0 0-.87-.2.617.617 0 0 0-.22.83l1.84 3.18C4.74 11.28 3.5 13.42 3.5 16h17c0-2.58-1.23-4.72-2.9-6.52M7 14.5a1 1 0 1 1 0-2 1 1 0 0 1 0 2m10 0a1 1 0 1 1 0-2 1 1 0 0 1 0 2"/></svg>`,
  windows: `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 5.5L10.5 4.2v7.6H3V5.5zm0 13V13.8h7.5V20L3 18.5zM13.5 12.1V4.2L21 3v9.1h-7.5zm0 1.4H21V21l-7.5-1.5V13.5z"/></svg>`,
  mac: `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>`,
  download: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`,
};

const PLATFORM_ORDER = [
  { key: "ios", icon: "ios" },
  { key: "android", icon: "android" },
  { key: "androidApk", icon: "android" },
  { key: "windows", icon: "windows" },
  { key: "mac", icon: "mac" },
];

function detectPlatform() {
  const ua = navigator.userAgent.toLowerCase();
  if (/iphone|ipad|ipod/.test(ua)) return "ios";
  if (/android/.test(ua)) return "androidApk";
  if (/win/.test(ua)) return "windows";
  if (/mac/.test(ua)) return "mac";
  return null;
}

function applyConfig() {
  const { name, tagline, description, icon, version, footer, themeColor } = APP_CONFIG;

  document.title = `Download ${name}`;
  document.querySelector('meta[name="description"]').content =
    `${tagline} — Download ${name} for iOS, Android, Windows, and macOS.`;

  if (themeColor) {
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) themeMeta.content = themeColor;
  }

  document.querySelectorAll("[data-app-name]").forEach((el) => {
    el.textContent = name;
  });

  document.querySelectorAll("[data-app-version]").forEach((el) => {
    el.textContent = version;
  });

  const descEl = document.querySelector("[data-app-description]");
  if (descEl) descEl.textContent = description;

  const copyEl = document.querySelector("[data-footer-copy]");
  if (copyEl) copyEl.textContent = footer.copyright;

  document.querySelectorAll(".logo__icon, .footer__brand img").forEach((el) => {
    el.src = icon;
  });
}

function renderDownloadButtons() {
  const container = document.getElementById("download-buttons");
  const platformHint = document.getElementById("platform-hint");
  const downloadNote = document.getElementById("download-note");
  const detected = detectPlatform();

  const enabledPlatforms = PLATFORM_ORDER.filter(
    ({ key }) => APP_CONFIG.downloads[key]?.enabled
  );

  if (enabledPlatforms.length === 0) {
    platformHint.textContent = "Downloads coming soon";
    return;
  }

  if (detected) {
    const match = APP_CONFIG.downloads[detected];
    if (match?.enabled) {
      platformHint.textContent = `Recommended for your device`;
    }
  }

  enabledPlatforms.forEach(({ key, icon }) => {
    const platform = APP_CONFIG.downloads[key];
    const btn = document.createElement("a");
    btn.href = platform.url;
    btn.className = "download-btn";
    btn.setAttribute("data-platform", key);

    const isPrimary = key === detected;
    if (isPrimary) {
      btn.classList.add("download-btn--primary", "download-btn--highlight");
    }

    if (key === "androidApk" || key === "windows" || key === "mac") {
      btn.setAttribute("download", "");
    }

    if (platform.url.startsWith("http")) {
      btn.target = "_blank";
      btn.rel = "noopener noreferrer";
    }

    const iconKey = key === "androidApk" ? "download" : icon;
    btn.innerHTML = `${ICONS[iconKey] || ICONS.download}<span>${platform.label}</span>`;

    btn.addEventListener("mouseenter", () => {
      if (platform.hint) downloadNote.textContent = platform.hint;
    });
    btn.addEventListener("mouseleave", () => {
      downloadNote.textContent = "";
    });

    container.appendChild(btn);
  });
}

function renderFeatures() {
  const grid = document.getElementById("features-grid");
  APP_CONFIG.features.forEach(({ icon, title, text }, index) => {
    const card = document.createElement("article");
    card.className = "feature-card";
    card.style.animationDelay = `${120 + index * 70}ms`;
    card.innerHTML = `
      <div class="feature-card__icon">${icon}</div>
      <h3>${escapeHtml(title)}</h3>
      <p>${escapeHtml(text)}</p>
    `;
    grid.appendChild(card);
  });

  requestAnimationFrame(() => {
    grid.querySelectorAll(".feature-card").forEach((card) => {
      card.classList.add("is-visible");
    });
  });
}

function renderScreenshots() {
  const track = document.getElementById("screenshots-track");
  APP_CONFIG.screenshots.forEach(({ src, alt }) => {
    const card = document.createElement("div");
    card.className = "screenshot-card";
    card.innerHTML = `<img src="${escapeHtml(src)}" alt="${escapeHtml(alt)}" loading="lazy" />`;
    track.appendChild(card);
  });
}

function renderFooterLinks() {
  const nav = document.getElementById("footer-links");
  APP_CONFIG.footer.links.forEach(({ label, url }) => {
    const a = document.createElement("a");
    a.href = url;
    a.textContent = label;
    nav.appendChild(a);
  });
}

function setupQrCode() {
  if (/Mobi|Android/i.test(navigator.userAgent)) return;

  const apk = APP_CONFIG.downloads.androidApk;
  if (!apk?.enabled) return;

  // QR points to this page so phone users land on the download button.
  showQrCode(window.location.href.split("#")[0]);
}

function showQrCode(url) {
  const block = document.getElementById("qr-block");
  const container = document.getElementById("qr-code");
  block.hidden = false;

  const img = document.createElement("img");
  img.src = `https://api.qrserver.com/v1/create-qr-code/?size=168x168&data=${encodeURIComponent(url)}`;
  img.alt = "QR code to download the app";
  img.width = 84;
  img.height = 84;
  container.appendChild(img);
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

document.addEventListener("DOMContentLoaded", () => {
  applyConfig();
  renderDownloadButtons();
  renderFeatures();
  renderScreenshots();
  renderFooterLinks();
  setupQrCode();
});
