import "dotenv/config";
import express from "express";

const app = express();
const port = Number(process.env.PORT || 3000);
const model = process.env.GEMINI_MODEL || "gemini-3.6-flash";

app.use(express.json({ limit: "32kb" }));
app.disable("x-powered-by");

app.get("/health", (_req, res) => res.json({ ok: true }));

app.post("/api/assistant/chat", async (req, res) => {
  const { message, context } = req.body ?? {};
  if (typeof message !== "string" || !message.trim()) {
    return res.status(400).json({ error: "message is required" });
  }
  if (!process.env.GEMINI_API_KEY) {
    return res.status(503).json({ error: "GEMINI_API_KEY is not configured" });
  }

  const prompt = [
    "You are Smart Life Manager, a concise and supportive student assistant.",
    "Answer in the user's language. Use only the supplied context for personal metrics; never invent data.",
    `Context: ${JSON.stringify(context ?? {})}`,
    `User message: ${message.trim()}`
  ].join("\n");

  try {
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(process.env.GEMINI_API_KEY)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] })
      }
    );
    const body = await response.json();
    if (!response.ok) return res.status(502).json({ error: body.error?.message || "Gemini request failed" });
    const reply = body.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!reply) return res.status(502).json({ error: "Gemini returned no text" });
    return res.json({ reply });
  } catch (error) {
    return res.status(502).json({ error: "AI provider unavailable" });
  }
});

app.listen(port, () => console.log(`Smart Life Manager AI API listening on ${port}`));
