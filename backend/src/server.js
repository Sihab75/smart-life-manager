import "dotenv/config";
import express from "express";
import { applicationDefault, cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { createLocationConsentRouter, createLocationApiRouter } from "./location-sharing.js";

const app = express();
const port = Number(process.env.PORT || 3000);
const trustedProxyHops = Number(process.env.TRUST_PROXY_HOPS || 0);
if (!Number.isSafeInteger(trustedProxyHops) || trustedProxyHops < 0) {
  throw new Error("TRUST_PROXY_HOPS must be a non-negative integer");
}
app.set("trust proxy", trustedProxyHops);
const providers = {
  gemini: { key: process.env.GEMINI_API_KEY, model: process.env.GEMINI_MODEL || "gemini-2.5-flash" },
  openai: { key: process.env.OPENAI_API_KEY, model: process.env.OPENAI_MODEL || "gpt-4o-mini" },
  deepseek: { key: process.env.DEEPSEEK_API_KEY, model: process.env.DEEPSEEK_MODEL || "deepseek-chat" }
};

app.use(express.json({ limit: "256kb" }));
app.disable("x-powered-by");
app.get("/health", (_req, res) => res.json({ ok: true }));

const apiRequestsByIp = new Map();
let requestsSinceCleanup = 0;
app.use("/api", (req, res, next) => {
  const now = Date.now();
  const key = req.ip;
  const current = apiRequestsByIp.get(key);
  const window = current && current.expiresAt > now
    ? current
    : { count: 0, expiresAt: now + 60000 };

  if (window.count >= 20) {
    res.set("Retry-After", String(Math.ceil((window.expiresAt - now) / 1000)));
    return res.status(429).json({ error: "Too many requests" });
  }

  window.count += 1;
  apiRequestsByIp.set(key, window);
  requestsSinceCleanup += 1;
  if (requestsSinceCleanup >= 256) {
    for (const [ip, entry] of apiRequestsByIp) {
      if (entry.expiresAt <= now) apiRequestsByIp.delete(ip);
    }
    requestsSinceCleanup = 0;
  }
  if (apiRequestsByIp.size > 10000) {
    const oldestIps = apiRequestsByIp.keys();
    for (let removed = 0; removed < 1000; removed += 1) {
      const oldest = oldestIps.next();
      if (oldest.done) break;
      apiRequestsByIp.delete(oldest.value);
    }
  }

  return next();
});

function firebaseAdminApp() {
  const projectId = process.env.FIREBASE_PROJECT_ID;
  if (!projectId) return null;

  const appName = "smart-life-manager-api";
  const existingApp = getApps().find((candidate) => candidate.name === appName);
  if (existingApp) return existingApp;

  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const credential = serviceAccountJson
    ? cert(JSON.parse(serviceAccountJson))
    : applicationDefault();
  return initializeApp({ credential, projectId }, appName);
}

function firebaseAdminAuth() {
  const firebaseApp = firebaseAdminApp();
  return firebaseApp ? getAuth(firebaseApp) : null;
}

async function requireFirebaseUser(req, res, next) {
  const authorization = req.get("authorization") || "";
  const tokenMatch = /^Bearer\s+(\S+)$/i.exec(authorization);
  if (!tokenMatch) {
    return res.status(401).json({ error: "Firebase sign-in is required" });
  }

  try {
    const auth = firebaseAdminAuth();
    if (!auth) {
      return res.status(503).json({ error: "Firebase authentication is not configured" });
    }
    req.firebaseUser = await auth.verifyIdToken(tokenMatch[1], true);
    return next();
  } catch (error) {
    const code = error && typeof error === "object" && "code" in error
      ? error.code
      : null;
    if (typeof code === "string" && code.startsWith("auth/")) {
      return res.status(401).json({ error: "Firebase ID token is invalid or expired" });
    }
    console.error("Firebase token verification is unavailable", error);
    return res.status(503).json({ error: "Firebase authentication is unavailable" });
  }
}

const backendDirectory = path.dirname(fileURLToPath(import.meta.url));
const locationPageDirectory = path.resolve(backendDirectory, "../public");
const locationPageCsp = [
  "default-src 'self'",
  "script-src 'self' https://www.gstatic.com https://www.google.com https://www.recaptcha.net",
  "style-src 'self' 'unsafe-inline'",
  "connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://firebaseinstallations.googleapis.com https://www.googleapis.com https://www.google.com https://www.recaptcha.net",
  "img-src 'self' data: https://www.gstatic.com https://www.google.com",
  "frame-src https://www.google.com https://recaptcha.google.com https://www.recaptcha.net",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'"
].join("; ");
let locationConsentRouter;
let locationApiRouter;
app.use("/location-assets", express.static(locationPageDirectory, {
  index: false,
  dotfiles: "deny",
  maxAge: "5m",
  setHeaders: (res, filePath) => {
    res.set("X-Content-Type-Options", "nosniff");
    res.set("Referrer-Policy", "no-referrer");
    if (path.extname(filePath).toLowerCase() === ".html") {
      res.set("Cache-Control", "no-store");
      res.set("Content-Security-Policy", locationPageCsp);
    }
  }
}));
app.get("/location-share", (_req, res) => {
  res.set("Cache-Control", "no-store");
  res.set("Referrer-Policy", "no-referrer");
  res.set("Content-Security-Policy", locationPageCsp);
  res.sendFile(path.join(locationPageDirectory, "location-share.html"));
});

app.use("/api/location-consent", (req, res, next) => {
  const firebaseApp = firebaseAdminApp();
  if (!firebaseApp) {
    return res.status(503).json({ error: "Location sharing is not configured" });
  }
  locationConsentRouter ??= createLocationConsentRouter(express, getFirestore(firebaseApp), {
    auth: getAuth(firebaseApp),
    phoneLookupSecret: process.env.PHONE_LOOKUP_HMAC_SECRET,
    firebaseWebConfig: {
      apiKey: process.env.PUBLIC_FIREBASE_API_KEY,
      authDomain: process.env.PUBLIC_FIREBASE_AUTH_DOMAIN,
      projectId: process.env.FIREBASE_PROJECT_ID,
      appId: process.env.PUBLIC_FIREBASE_APP_ID
    }
  });
  return locationConsentRouter(req, res, next);
});

app.use("/api", requireFirebaseUser);
app.use("/api/location", (req, res, next) => {
  const firebaseApp = firebaseAdminApp();
  if (!firebaseApp) {
    return res.status(503).json({ error: "Location sharing is not configured" });
  }
  locationApiRouter ??= createLocationApiRouter(express, getFirestore(firebaseApp), {
    publicBaseUrl: process.env.PUBLIC_BASE_URL,
    phoneLookupSecret: process.env.PHONE_LOOKUP_HMAC_SECRET,
    messaging: getMessaging(firebaseApp)
  });
  return locationApiRouter(req, res, next);
});

function normalizeProvider(provider) {
  return String(provider ?? "gemini").trim().toLowerCase();
}

async function callProvider(provider, prompt, system) {
  const providerId = normalizeProvider(provider);
  const configuration = providers[providerId];

  if (!configuration || !configuration.key) {
    throw new Error(providerId.toUpperCase() + "_API_KEY is not configured");
  }

  const request = providerId === "gemini"
    ? {
        url: "https://generativelanguage.googleapis.com/v1beta/models/" + configuration.model + ":generateContent?key=" + encodeURIComponent(configuration.key),
        body: { contents: [{ parts: [{ text: prompt }] }] },
        headers: { "Content-Type": "application/json" }
      }
    : {
        url: providerId === "openai"
          ? "https://api.openai.com/v1/chat/completions"
          : "https://api.deepseek.com/chat/completions",
        body: {
          model: configuration.model,
          messages: [
            { role: "system", content: system },
            { role: "user", content: prompt }
          ]
        },
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          Authorization: "Bearer " + configuration.key
        }
      };

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);
  try {
    const response = await fetch(request.url, {
      method: "POST",
      headers: request.headers,
      body: JSON.stringify(request.body),
      signal: controller.signal
    });

    const responseText = await response.text();
    let body;
    try {
      body = JSON.parse(responseText);
    } catch {
      body = { error: { message: responseText || providerId + " request failed" } };
    }

    if (!response.ok) {
      throw new Error(body.error?.message || providerId + " request failed");
    }

    return providerId === "gemini"
      ? body.candidates?.[0]?.content?.parts?.[0]?.text
      : body.choices?.[0]?.message?.content;
  } finally {
    clearTimeout(timeout);
  }
}

function validateProvider(provider, res) {
  const providerId = normalizeProvider(provider);

  if (!Object.prototype.hasOwnProperty.call(providers, providerId)) {
    res.status(400).json({ error: "Unsupported AI provider" });
    return false;
  }

  if (!providers[providerId].key) {
    res.status(503).json({ error: providerId.toUpperCase() + "_API_KEY is not configured" });
    return false;
  }

  return true;
}

app.post("/api/assistant/chat", async (req, res) => {
  const { message, context, provider = "gemini" } = req.body ?? {};
  const providerId = normalizeProvider(provider);

  if (typeof message !== "string" || !message.trim()) {
    return res.status(400).json({ error: "message is required" });
  }

  if (!validateProvider(providerId, res)) {
    return;
  }

  const prompt = [
    "You are Smart Life Manager, a concise and supportive student assistant.",
    "Answer in the user's language. Use only the supplied context for personal metrics; never invent data.",
    "When asked about today, present the supplied todayPlan as a clear time-ordered plan.",
    "Context: " + JSON.stringify(context ?? {}),
    "User message: " + message.trim()
  ].join("\n");

  try {
    const reply = await callProvider(providerId, prompt, "You are Smart Life Manager, a concise and supportive student assistant.");
    if (!reply) {
      return res.status(502).json({ error: "AI provider returned no text" });
    }
    return res.json({ reply });
  } catch (_error) {
    return res.status(502).json({ error: "AI provider unavailable" });
  }
});

app.post("/api/document/action", async (req, res) => {
  const { action, title, text, provider = "gemini" } = req.body ?? {};
  const providerId = normalizeProvider(provider);

  if (typeof action !== "string" || !action.trim() || typeof title !== "string") {
    return res.status(400).json({ error: "action and title are required" });
  }

  if (!validateProvider(providerId, res)) {
    return;
  }

  const prompt = [
    "You are a helpful study assistant. Use only the supplied document text and say when the text is unavailable.",
    "Action: " + action.trim(),
    "Document title: " + title.trim(),
    "Document text: " + String(text || "").slice(0, 30000)
  ].join("\n");

  try {
    const result = await callProvider(providerId, prompt, "You are a helpful study assistant.");
    if (!result) {
      return res.status(502).json({ error: "AI provider returned no text" });
    }
    return res.json({ result });
  } catch (_error) {
    return res.status(502).json({ error: "AI provider unavailable" });
  }
});

app.listen(port, () => console.log("Smart Life Manager AI API listening on " + port));
