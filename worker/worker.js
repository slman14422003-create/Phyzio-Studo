const WORKERS_AI_ALIASES = {
  "@cf/meta/llama-3.3-70b-instruct": "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
};

const FAST_CHAIN = [
  { provider: "workers-ai", model: "@cf/meta/llama-3.1-8b-instruct" },
  { provider: "groq", model: "llama-3.1-8b-instant" },
  { provider: "workers-ai", model: "@cf/meta/llama-3.2-3b-instruct" },
  { provider: "gemini", model: "gemini-2.0-flash" },
  { provider: "openrouter", model: "meta-llama/llama-3.3-70b-instruct:free" },
];

const CLINICAL_CHAIN = [
  { provider: "workers-ai", model: "@cf/meta/llama-3.3-70b-instruct-fp8-fast" },
  { provider: "groq", model: "llama-3.3-70b-versatile" },
  { provider: "gemini", model: "gemini-2.0-flash" },
  { provider: "openrouter", model: "meta-llama/llama-3.3-70b-instruct:free" },
  { provider: "workers-ai", model: "@cf/mistralai/mistral-small-3.1-24b-instruct" },
  { provider: "workers-ai", model: "@cf/google/gemma-3-12b-it" },
  { provider: "workers-ai", model: "@cf/meta/llama-3.1-8b-instruct" },
];

const PROVIDERS = {
  groq: { url: "https://api.groq.com/openai/v1/chat/completions", key: "GROQ_API_KEY" },
  gemini: { url: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", key: "GEMINI_API_KEY" },
  openrouter: { url: "https://openrouter.ai/api/v1/chat/completions", key: "OPENROUTER_API_KEY" },
};

const MAX_SYSTEM_CHARS = 150000;
const MAX_MESSAGE_CHARS = 30000;
const DEFAULT_MAX_TOKENS = 2048;
const ATTEMPT_TIMEOUT_MS = 70000;
const TRANSIENT_BLOCK_MS = 60 * 1000;

const blocked = new Map();

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...CORS, ...extra },
  });
}

function nextUtcMidnight() {
  const d = new Date();
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1, 0, 0, 5);
}

function keyOf(c) {
  return c.provider + ":" + c.model;
}

function isBlocked(c) {
  const until = blocked.get(keyOf(c));
  if (!until) return false;
  if (Date.now() >= until) {
    blocked.delete(keyOf(c));
    return false;
  }
  return true;
}

function classifyError(err) {
  const text = String((err && (err.message || err.error || err)) || "").toLowerCase();
  const status = err && (err.status || err.code);
  if (
    text.includes("4006") ||
    text.includes("neurons") ||
    text.includes("daily free allocation") ||
    text.includes("quota") ||
    text.includes("insufficient_quota") ||
    text.includes("exceeded your current") ||
    text.includes("billing")
  ) {
    return "quota";
  }
  if (status === 429 || text.includes("rate limit") || text.includes("too many requests") || text.includes("capacity")) {
    return "rate";
  }
  if (status === 404 || text.includes("no such model") || text.includes("model not found") || text.includes("does not exist")) {
    return "missing";
  }
  if (status >= 500 || text.includes("timeout") || text.includes("overloaded") || text.includes("unavailable")) {
    return "transient";
  }
  return "other";
}

function markBlocked(c, kind) {
  if (kind === "quota" || kind === "missing") {
    blocked.set(keyOf(c), kind === "quota" ? nextUtcMidnight() : Date.now() + 6 * 3600 * 1000);
  } else if (kind === "rate" || kind === "transient") {
    blocked.set(keyOf(c), Date.now() + TRANSIENT_BLOCK_MS);
  }
}

function withTimeout(promise, ms) {
  let timer;
  const t = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error("timeout")), ms);
  });
  return Promise.race([promise, t]).finally(() => clearTimeout(timer));
}

function extractText(result) {
  if (result == null) return "";
  if (typeof result === "string") return result;
  if (typeof result.response === "string") return result.response;
  if (result.response && typeof result.response === "object") {
    return extractText(result.response);
  }
  if (typeof result.reply === "string") return result.reply;
  if (Array.isArray(result.choices) && result.choices.length > 0) {
    const m = result.choices[0].message;
    if (m && typeof m.content === "string") return m.content;
    if (typeof result.choices[0].text === "string") return result.choices[0].text;
  }
  if (typeof result.result === "string") return result.result;
  return "";
}

async function callWorkersAi(env, model, messages, maxTokens) {
  if (!env.AI) throw Object.assign(new Error("AI binding missing"), { status: 404 });
  const result = await withTimeout(
    env.AI.run(model, { messages, max_tokens: maxTokens, temperature: 0.3 }),
    ATTEMPT_TIMEOUT_MS
  );
  return extractText(result);
}

async function callOpenAiCompatible(env, providerName, model, messages, maxTokens) {
  const p = PROVIDERS[providerName];
  const key = env[p.key];
  if (!key) throw Object.assign(new Error("not configured"), { skip: true });
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ATTEMPT_TIMEOUT_MS);
  try {
    const res = await fetch(p.url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + key,
        ...(providerName === "openrouter" ? { "X-Title": "Phizyo Studio" } : {}),
      },
      body: JSON.stringify({ model, messages, max_tokens: maxTokens, temperature: 0.3 }),
      signal: controller.signal,
    });
    const raw = await res.text();
    let data = null;
    try {
      data = JSON.parse(raw);
    } catch (e) {
      data = null;
    }
    if (!res.ok) {
      const msg = (data && data.error && (data.error.message || data.error)) || raw.slice(0, 300);
      throw Object.assign(new Error(String(msg)), { status: res.status });
    }
    return extractText(data);
  } finally {
    clearTimeout(timer);
  }
}

function buildChain(requestedModel, env) {
  const req = requestedModel ? WORKERS_AI_ALIASES[requestedModel] || requestedModel : null;
  const fastNames = new Set(["@cf/meta/llama-3.1-8b-instruct", "@cf/meta/llama-3.2-3b-instruct"]);
  const base = req && fastNames.has(req) ? FAST_CHAIN : CLINICAL_CHAIN;
  const chain = [];
  if (req && req.startsWith("@cf/")) chain.push({ provider: "workers-ai", model: req });
  for (const c of base) {
    if (chain.some((x) => keyOf(x) === keyOf(c))) continue;
    chain.push(c);
  }
  return chain.filter((c) => c.provider === "workers-ai" || env[PROVIDERS[c.provider].key]);
}

async function generate(env, body) {
  const system = String(body.system || "").slice(0, MAX_SYSTEM_CHARS);
  const message = String(body.message || "").slice(0, MAX_MESSAGE_CHARS);
  const maxTokens = Math.min(Number(body.max_tokens) || Number(env.MAX_TOKENS) || DEFAULT_MAX_TOKENS, 4096);
  const messages = [];
  if (system.trim()) messages.push({ role: "system", content: system });
  messages.push({ role: "user", content: message });

  const chain = buildChain(body.model, env);
  const errors = [];
  let attempted = 0;

  for (const c of chain) {
    if (isBlocked(c)) continue;
    attempted++;
    try {
      const text =
        c.provider === "workers-ai"
          ? await callWorkersAi(env, c.model, messages, maxTokens)
          : await callOpenAiCompatible(env, c.provider, c.model, messages, maxTokens);
      if (text && text.trim()) {
        return { reply: text.trim(), provider: c.provider, model: c.model, fallback: attempted > 1 };
      }
      errors.push(keyOf(c) + ": empty reply");
      markBlocked(c, "transient");
    } catch (err) {
      if (err && err.skip) {
        attempted--;
        continue;
      }
      const kind = classifyError(err);
      markBlocked(c, kind);
      errors.push(keyOf(c) + ": " + kind + " - " + String((err && err.message) || err).slice(0, 160));
    }
  }
  return { error: "All models failed", details: errors, allBlocked: attempted === 0 };
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

    if (request.method === "GET") {
      const chain = buildChain(null, env).map((c) => keyOf(c));
      return json({ status: "ok", chain, blocked: [...blocked.keys()].filter((k) => blocked.get(k) > Date.now()) });
    }

    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

    let body;
    try {
      body = await request.json();
    } catch (e) {
      return json({ error: "Invalid JSON body" }, 400);
    }
    if (!body || typeof body.message !== "string" || !body.message.trim()) {
      return json({ error: "Field message is required" }, 400);
    }

    const out = await generate(env, body);
    if (out.error) {
      const detail = out.details.length ? out.details.join(" | ") : "no model available";
      return json({ error: out.error + ": " + detail }, out.allBlocked ? 503 : 502);
    }
    return json(out, 200, { "X-Model-Used": out.provider + ":" + out.model });
  },
};
