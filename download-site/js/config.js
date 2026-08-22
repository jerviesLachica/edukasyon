/**
 * SchedMate download page configuration.
 * Update version and APK path when releasing a new build.
 */
const APP_CONFIG = {
  name: "SchedMate",
  tagline: "Your AI student companion.",
  description:
    "Your offline-first AI student companion — schedule, planner, notes, grades, and an AI tutor in one app.",

  // ThemePresets.Coral — matches SchedMate brand (Theme.kt / ThemeCustomization.kt)
  themeColor: "#F97316",

  icon: "assets/app-icon.png",

  version: "1.2.0",
  packageId: "com.edukasyon.studentai",

  downloads: {
    ios: {
      enabled: false,
      label: "App Store",
      url: "",
    },
    android: {
      enabled: false,
      label: "Google Play",
      url: "",
    },
    androidApk: {
      enabled: true,
      label: "Download APK",
      url: "https://github.com/jerviesLachica/edukasyon/releases/download/v1.2.0/schedmate-1.2.0.apk",
      hint: "Android 8+ · Signed release build · Enable 'Install unknown apps' if prompted",
    },
    windows: {
      enabled: false,
      label: "Windows",
      url: "",
    },
    mac: {
      enabled: false,
      label: "macOS",
      url: "",
    },
  },

  features: [
    {
      icon: "📚",
      title: "Offline-first",
      text: "Your schedule, tasks, and notes stay on your device — works without Wi‑Fi.",
    },
    {
      icon: "🤖",
      title: "AI tutor",
      text: "Chat, summarize, flashcards, quizzes, and assignment breakdown powered by AI.",
    },
    {
      icon: "📅",
      title: "Schedule & planner",
      text: "Classes, assignments, exams, and a unified calendar in one dashboard.",
    },
    {
      icon: "📊",
      title: "Grades & focus",
      text: "Weighted grade tracking, exam readiness, and focus sessions to stay on track.",
    },
  ],

  screenshots: [
    { src: "assets/screenshot-1.svg", alt: "SchedMate home dashboard" },
    { src: "assets/screenshot-2.svg", alt: "SchedMate planner view" },
    { src: "assets/screenshot-3.svg", alt: "SchedMate AI tutor" },
  ],

  footer: {
    copyright: `© ${new Date().getFullYear()} SchedMate · Edukasyon`,
    links: [
      { label: "Support", url: "mailto:lachicajervies@gmail.com" },
    ],
  },
};
