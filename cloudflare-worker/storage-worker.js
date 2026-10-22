// Cloudflare Worker: وسيط بين التطبيق ومستودع GitHub لتخزين الملفات.
// التطبيق لا يعرف توكن GitHub أبدًا؛ التوكن سرّ داخل الووركر فقط.
//
// Routes (نفس عقد CloudStorageClient):
//   GET    /files            -> [{name,size,uploadedMs}]
//   PUT    /files/{name}     -> رفع/استبدال (الجسم = بايتات الملف)
//   GET    /files/{name}     -> تنزيل الملف
//   DELETE /files/{name}     -> حذف
//   POST   /files/rename     -> {"from","to"}
//
// Secrets/Vars: GITHUB_TOKEN (secret)، GITHUB_REPO، GITHUB_BRANCH، GITHUB_DIR،
// SHARED_SECRET (secret، اختياري: لو ضبطته لازم يطابق Token في التطبيق).

const API = "https://api.github.com";
const MAX_BYTES = 25 * 1024 * 1024;
const DATE_LOOKUP_LIMIT = 40; // حد الطلبات الفرعية لجلب تاريخ آخر تعديل

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, PUT, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (env.SHARED_SECRET && req.headers.get("Authorization") !== "Bearer " + env.SHARED_SECRET) {
      return json({ error: "unauthorized" }, 401);
    }
    if (!env.GITHUB_TOKEN || !env.GITHUB_REPO) {
      return json({ error: "GITHUB_TOKEN / GITHUB_REPO غير مضبوطين في الووركر" }, 500);
    }
    try {
      return await route(req, env);
    } catch (e) {
      return json({ error: String((e && e.message) || e) }, 500);
    }
  },
};

function cfg(env) {
  const dir = (env.GITHUB_DIR || "files").replace(/^\/+|\/+$/g, "");
  return { repo: env.GITHUB_REPO, branch: env.GITHUB_BRANCH || "main", dir };
}

function gh(env, path, init = {}) {
  return fetch(API + path, {
    ...init,
    headers: {
      Authorization: "Bearer " + env.GITHUB_TOKEN,
      "User-Agent": "semo-files-worker",
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(init.headers || {}),
    },
  });
}

function dirPath(env) {
  const c = cfg(env);
  return `/repos/${c.repo}/contents/${c.dir.split("/").map(encodeURIComponent).join("/")}`;
}
function filePath(env, name) {
  return `${dirPath(env)}/${encodeURIComponent(name)}`;
}
function refQ(env) {
  return "?ref=" + encodeURIComponent(cfg(env).branch);
}

function validName(name) {
  return name && !name.includes("/") && !name.includes("\\") && name !== "." && name !== ".." && name.length <= 200;
}

async function route(req, env) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/\/+$/, "");
  if (path === "/files" && req.method === "GET") return list(env);
  if (path === "/files/rename" && req.method === "POST") return rename(req, env);
  if (path.startsWith("/files/")) {
    const name = decodeURIComponent(path.slice("/files/".length));
    if (!validName(name)) return json({ error: "اسم ملف غير صالح" }, 400);
    if (req.method === "PUT") return put(req, env, name);
    if (req.method === "GET") return get(env, name);
    if (req.method === "DELETE") return del(env, name);
  }
  return json({ error: "not found" }, 404);
}

async function getMeta(env, name) {
  const res = await gh(env, filePath(env, name) + refQ(env));
  if (res.status === 404) return null;
  if (!res.ok) throw new Error("GitHub " + res.status + ": " + (await res.text()));
  const j = await res.json();
  return Array.isArray(j) ? null : j;
}

async function list(env) {
  const c = cfg(env);
  const res = await gh(env, dirPath(env) + refQ(env));
  if (res.status === 404) return json([]);
  if (!res.ok) throw new Error("GitHub " + res.status + ": " + (await res.text()));
  const items = (await res.json()).filter((x) => x.type === "file" && x.name !== ".gitkeep");
  const out = await Promise.all(
    items.map(async (x, i) => {
      let ms = 0;
      if (i < DATE_LOOKUP_LIMIT) {
        try {
          const r = await gh(
            env,
            `/repos/${c.repo}/commits?path=${encodeURIComponent(c.dir + "/" + x.name)}&sha=${encodeURIComponent(c.branch)}&per_page=1`
          );
          if (r.ok) {
            const cj = await r.json();
            if (cj[0]) ms = Date.parse(cj[0].commit.committer.date) || 0;
          }
        } catch (_) {}
      }
      return { name: x.name, size: x.size, uploadedMs: ms };
    })
  );
  return json(out);
}

function toBase64(buf) {
  const bytes = new Uint8Array(buf);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return btoa(bin);
}

async function putBytes(env, name, buf) {
  if (buf.byteLength > MAX_BYTES) return json({ error: "too large" }, 413);
  const content = toBase64(buf);
  const c = cfg(env);
  for (let attempt = 0; attempt < 2; attempt++) {
    const meta = await getMeta(env, name);
    const body = { message: "upload " + name, content, branch: c.branch };
    if (meta) body.sha = meta.sha;
    const res = await gh(env, filePath(env, name), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (res.ok) return json({ ok: true, name });
    if ((res.status === 409 || res.status === 422) && attempt === 0) continue; // تعارض sha: أعد المحاولة
    throw new Error("GitHub " + res.status + ": " + (await res.text()));
  }
  return json({ error: "conflict" }, 409);
}

async function put(req, env, name) {
  return putBytes(env, name, await req.arrayBuffer());
}

async function get(env, name) {
  const res = await gh(env, filePath(env, name) + refQ(env), {
    headers: { Accept: "application/vnd.github.raw+json" },
  });
  if (res.status === 404) return json({ error: "not found" }, 404);
  if (!res.ok) throw new Error("GitHub " + res.status + ": " + (await res.text()));
  const headers = { ...CORS, "Content-Type": "application/octet-stream" };
  const len = res.headers.get("Content-Length");
  if (len) headers["Content-Length"] = len;
  return new Response(res.body, { status: 200, headers });
}

async function del(env, name) {
  const meta = await getMeta(env, name);
  if (!meta) return json({ error: "not found" }, 404);
  const res = await gh(env, filePath(env, name), {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: "delete " + name, sha: meta.sha, branch: cfg(env).branch }),
  });
  if (!res.ok) throw new Error("GitHub " + res.status + ": " + (await res.text()));
  return json({ ok: true });
}

async function rename(req, env) {
  const { from, to } = await req.json();
  if (!validName(from) || !validName(to)) return json({ error: "اسم ملف غير صالح" }, 400);
  if (await getMeta(env, to)) return json({ error: "يوجد ملف بنفس الاسم" }, 409);
  const res = await gh(env, filePath(env, from) + refQ(env), {
    headers: { Accept: "application/vnd.github.raw+json" },
  });
  if (res.status === 404) return json({ error: "not found" }, 404);
  if (!res.ok) throw new Error("GitHub " + res.status + ": " + (await res.text()));
  const r = await putBytes(env, to, await res.arrayBuffer());
  if (!r.ok) return r;
  return del(env, from);
}
