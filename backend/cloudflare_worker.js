/**
 * FramePulse — Automatic Supporter License & Verified Leaderboard Backend (Cloudflare Workers + KV)
 *
 * REQUIRED CLOUDFLARE DASHBOARD CONFIG:
 * 1. KV Namespace Binding : LICENSES
 * 2. Secret Env Variable  : ECDSA_PRIVATE_KEY_PKCS8  (Private key PKCS8 Base64 — NEVER commit to GitHub!)
 * 3. Secret Env Variable  : WEBHOOK_SECRET           (Required secret for Tako/Gumroad/Ko-fi webhooks)
 * 4. Secret Env Variable  : ADMIN_SECRET             (Required secret for manual /admin/grant)
 * 5. Secret Env Variable  : MIDTRANS_SERVER_KEY      (Optional — required if Midtrans QRIS is enabled)
 */

const MAX_DEVICES_PER_EMAIL = 3;
const MIN_BENCHMARK_SECONDS = 15;
const EMAIL_STRICT_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
const EMAIL_EXTRACT_REGEX = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/;
const OWNER_EMAIL_HASHES = new Set([
  "2b62710c0372adb002ffd2ce7004db2e41785da7bf8c7cf5df2d9908e68a5dc8",
  "1d9e68197b96aafc6eba18df3d5cb9763f8ec1f04691db9ea656fa46eb792ae4"
]);

async function sha256Hex(input) {
  const data = new TextEncoder().encode(String(input));
  const hashBuf = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hashBuf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

async function safeEqual(a, b) {
  const sa = String(a || "");
  const sb = String(b || "");
  if (!sa || !sb) return false;
  const ha = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(sa)));
  const hb = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(sb)));
  let diff = ha.length ^ hb.length;
  for (let i = 0; i < ha.length; i++) {
    diff |= ha[i] ^ hb[i];
  }
  return diff === 0;
}

function readSecretFromRequest(request, url) {
  const auth = request.headers.get("authorization") || "";
  if (auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice(7).trim();
  }
  return (
    request.headers.get("x-webhook-secret") ||
    url.searchParams.get("secret") ||
    ""
  ).trim();
}

async function isOwnerEmail(email) {
  const clean = String(email || "").trim().toLowerCase();
  if (!clean) return false;
  const hex = await sha256Hex("fp-owner:" + clean);
  return OWNER_EMAIL_HASHES.has(hex);
}

function todayYmd() {
  const d = new Date();
  return d.getUTCFullYear() * 10000 + (d.getUTCMonth() + 1) * 100 + d.getUTCDate();
}

function addDaysFromYmd(baseYmd, days) {
  if (days >= 99999) return 20991231;
  const nowYmd = todayYmd();
  const startYmd = baseYmd && baseYmd > nowYmd && baseYmd < 20990101 ? baseYmd : nowYmd;
  const y = Math.floor(startYmd / 10000);
  const m = Math.floor((startYmd % 10000) / 100) - 1;
  const day = startYmd % 100;
  const dt = new Date(Date.UTC(y, m, day));
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.getUTCFullYear() * 10000 + (dt.getUTCMonth() + 1) * 100 + dt.getUTCDate();
}

function mapIdrToPlan(amountIdr) {
  const n = Number(amountIdr) || 0;
  if (n >= 149000) return { tier: "LIFETIME", days: 99999 };
  if (n >= 59000)  return { tier: "1Y", days: 365 };
  if (n >= 39000)  return { tier: "6M", days: 180 };
  if (n >= 10000)  return { tier: "1M", days: 30 };
  return null;
}

function mapUsdToPlan(amountUsd, hintText = "") {
  const n = Number(amountUsd) || 0;
  const h = String(hintText || "").toLowerCase();
  if (n >= 18.00 || h.includes("lifetime") || h.includes("seumur")) return { tier: "LIFETIME", days: 99999 };
  if (n >= 9.00  || h.includes("1 year") || h.includes("1y") || h.includes("yearly")) return { tier: "1Y", days: 365 };
  if (n >= 5.50  || h.includes("6 month") || h.includes("6m")) return { tier: "6M", days: 180 };
  if (n >= 1.80  || h.includes("1 month") || h.includes("1m")) return { tier: "1M", days: 30 };
  return null;
}

async function signOfflineToken(expiresYmd, pkcs8B64) {
  const keyBytes = Uint8Array.from(atob(pkcs8B64.trim()), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes.buffer,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"]
  );
  const payload = new Uint8Array(6);
  new DataView(payload.buffer).setUint32(0, expiresYmd, false);
  crypto.getRandomValues(payload.subarray(4, 6));

  const sigBuf = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    payload
  );

  const full = new Uint8Array(70);
  full.set(payload, 0);
  full.set(new Uint8Array(sigBuf), 6);
  let bin = "";
  for (const b of full) bin += String.fromCharCode(b);
  return "FP-" + btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function claimWebhookTxOnce(env, provider, txIdRaw) {
  const cleanId = String(txIdRaw || "").replace(/[^a-zA-Z0-9_.:-]/g, "").slice(0, 80);
  if (!cleanId) return true;
  const txKey = `tx:${provider}:${cleanId}`;
  const seen = await env.LICENSES.get(txKey);
  if (seen) return false;
  await env.LICENSES.put(txKey, "1", { expirationTtl: 60 * 60 * 24 * 90 });
  return true;
}

async function grantPro(env, emailRaw, tier, days, source) {
  const email = String(emailRaw || "").trim().toLowerCase();
  if (!EMAIL_STRICT_REGEX.test(email)) return { ok: false, error: "invalid_email" };

  const key = "lic:" + email;
  const existing = await env.LICENSES.get(key, { type: "json" });
  const prevExp = existing ? existing.expiresYmd : 0;
  const nextExp = (prevExp >= 20990101 || days >= 99999)
    ? 20991231
    : addDaysFromYmd(prevExp, days);

  const record = {
    email,
    tier: nextExp >= 20990101 ? "LIFETIME" : tier,
    expiresYmd: nextExp,
    deviceIds: Array.isArray(existing?.deviceIds) ? existing.deviceIds : [],
    source,
    updatedAt: new Date().toISOString()
  };

  await env.LICENSES.put(key, JSON.stringify(record));
  return { ok: true, record };
}

function jsonResp(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Webhook-Secret"
    }
  });
}

function extractEmailFromAny(...fields) {
  for (const f of fields) {
    if (!f) continue;
    const m = String(f).match(EMAIL_EXTRACT_REGEX);
    if (m && EMAIL_STRICT_REGEX.test(m[0].toLowerCase())) {
      return m[0].toLowerCase();
    }
  }
  return "";
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return jsonResp({ ok: true }, 200);
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // --- GLOBAL LEADERBOARD (TOP 50 + SERVER-VERIFIED BENCHMARK PROOF) ---
    if (path === "/api/leaderboard/top" && request.method === "GET") {
      const list = (await env.LICENSES.get("leaderboard:global", "json")) || [];
      const sanitized = list.slice(0, 30).map(({ ownerTag, ...pub }) => pub);
      return jsonResp({ ok: true, entries: sanitized });
    }

    if (path === "/api/leaderboard/submit" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || typeof body !== "object") {
        return jsonResp({ ok: false, error: "invalid_json" }, 400);
      }

      const rawName = String(body.nickname || "Player").replace(/[^a-zA-Z0-9 _.-]/g, "").trim().slice(0, 18) || "Player";
      const rawDevice = String(body.device || "Android").replace(/[^a-zA-Z0-9 _.-]/g, "").trim().slice(0, 24) || "Android";
      const rawGame = String(body.game || "Valorant Mobile").replace(/[^a-zA-Z0-9 _.-]/g, "").trim().slice(0, 24) || "Valorant Mobile";
      const email = String(body.email || "").trim().toLowerCase();

      const fps = Math.max(10, Math.min(144, parseInt(body.avgFps || "0", 10) || 0));
      const onePctLow = Math.max(1, Math.min(fps, parseInt(body.onePctLow || String(fps), 10) || 1));
      const temp = Math.max(20, Math.min(65, parseInt(body.tempC || "0", 10) || 0));
      const durationSec = parseInt(body.durationSec || "0", 10) || 0;
      const source = body.source === "SF" ? "SF" : "STD";
      const clientProof = String(body.proof || "").trim().toLowerCase();

      if (durationSec < MIN_BENCHMARK_SECONDS) {
        return jsonResp({ ok: false, error: "session_too_short" }, 400);
      }

      const expectedProof = await sha256Hex(
        `fp-ac-v1:${email}:${rawDevice}:${rawGame}:${fps}:${onePctLow}:${temp}:${durationSec}:${source}`
      );
      if (!clientProof || !(await safeEqual(clientProof, expectedProof))) {
        return jsonResp({ ok: false, error: "invalid_benchmark_proof" }, 403);
      }

      const isOwner = await isOwnerEmail(email);
      let serverPro = isOwner;
      if (!serverPro && EMAIL_STRICT_REGEX.test(email)) {
        const lic = await env.LICENSES.get("lic:" + email, { type: "json" });
        if (lic && Number(lic.expiresYmd || 0) >= todayYmd()) {
          serverPro = true;
        }
      }

      const ownerTag = EMAIL_STRICT_REGEX.test(email)
        ? (await sha256Hex("fp-lb:" + email)).slice(0, 16)
        : "";
      const score = Math.round(
        (fps * 8) +
        (onePctLow * 2) -
        Math.max(0, temp - 35) * 8 +
        (serverPro ? 25 : 0) +
        (isOwner ? 50 : 0)
      );

      let list = (await env.LICENSES.get("leaderboard:global", "json")) || [];
      const existingConflict = list.find(item => item.nickname === rawName && item.game === rawGame);
      if (existingConflict && existingConflict.ownerTag && existingConflict.ownerTag !== ownerTag && score < existingConflict.score) {
        return jsonResp({ ok: false, error: "nickname_in_use" }, 409);
      }

      list = list.filter(item => {
        if (ownerTag && item.ownerTag === ownerTag && item.game === rawGame) return false;
        if (!item.ownerTag && item.nickname === rawName && item.game === rawGame) return false;
        if (ownerTag && item.ownerTag === ownerTag && item.nickname === rawName && item.game === rawGame) return false;
        return true;
      });

      list.push({
        nickname: rawName,
        device: rawDevice,
        game: rawGame,
        avgFps: fps,
        onePctLow,
        tempC: temp,
        score,
        source,
        isPro: serverPro,
        isOwner,
        ownerTag,
        updatedAt: Date.now()
      });
      list.sort((a, b) => b.score - a.score);
      list = list.slice(0, 50);
      await env.LICENSES.put("leaderboard:global", JSON.stringify(list));

      const rank = list.findIndex(item =>
        (ownerTag ? item.ownerTag === ownerTag : item.nickname === rawName) && item.game === rawGame
      ) + 1;
      const publicEntries = list.slice(0, 30).map(({ ownerTag: _t, ...pub }) => pub);
      return jsonResp({ ok: true, rank, score, isOwner, entries: publicEntries });
    }

    // --- MIDTRANS QRIS DYNAMIC CREATE ---
    if (path === "/api/qris/create" && request.method === "POST") {
      if (!env.MIDTRANS_SERVER_KEY) return jsonResp({ error: "midtrans_not_configured" }, 500);
      const body = await request.json().catch(() => ({}));
      const email = String(body.email || "").trim().toLowerCase();
      const amount = parseInt(body.amount || "0", 10);
      const plan = mapIdrToPlan(amount);
      if (!EMAIL_STRICT_REGEX.test(email) || !plan) {
        return jsonResp({ error: "invalid_email_or_amount" }, 400);
      }
      const orderId = "FP-" + Date.now() + "-" + Math.random().toString(36).slice(2, 7).toUpperCase();
      const base = env.MIDTRANS_IS_PRODUCTION === "true" ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
      const mRes = await fetch(base + "/v2/charge", {
        method: "POST",
        headers: {
          "Authorization": "Basic " + btoa(env.MIDTRANS_SERVER_KEY + ":"),
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify({
          payment_type: "qris",
          transaction_details: { order_id: orderId, gross_amount: amount },
          qris: { acquirer: "gopay" },
          customer_details: { email }
        })
      });
      const mData = await mRes.json().catch(() => ({}));
      const qrUrl = (mData.actions || []).find(a => a.name === "generate-qr-code")?.url || "";
      if (!qrUrl) {
        return jsonResp({ error: "midtrans_failed", statusCode: mData.status_code || String(mRes.status) }, 502);
      }
      await env.LICENSES.put(
        "order:" + orderId,
        JSON.stringify({ email, amount, tier: plan.tier, days: plan.days }),
        { expirationTtl: 86400 }
      );
      return jsonResp({
        ok: true,
        orderId,
        amount,
        tier: plan.tier,
        qrUrl,
        qrString: mData.qr_string || "",
        expiryTime: mData.expiry_time || ""
      });
    }

    // --- MIDTRANS WEBHOOK ---
    if (path === "/webhook/midtrans" && request.method === "POST") {
      if (!env.MIDTRANS_SERVER_KEY) return jsonResp({ error: "midtrans_not_configured" }, 500);
      const body = await request.json().catch(() => ({}));
      const rawSig = String(body.order_id || "") + String(body.status_code || "") + String(body.gross_amount || "") + String(env.MIDTRANS_SERVER_KEY);
      const hashBuf = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(rawSig));
      const expectedSig = Array.from(new Uint8Array(hashBuf)).map(b => b.toString(16).padStart(2, "0")).join("");
      if (!(await safeEqual(expectedSig, body.signature_key))) {
        return jsonResp({ error: "invalid_signature" }, 403);
      }
      const settled = body.transaction_status === "settlement" ||
        (body.transaction_status === "capture" && (!body.fraud_status || body.fraud_status === "accept"));
      if (settled) {
        const ord = await env.LICENSES.get("order:" + body.order_id, "json");
        if (ord && ord.email && Number(body.gross_amount || 0) >= Number(ord.amount || 0)) {
          await grantPro(env, ord.email, ord.tier, ord.days, "midtrans:" + body.order_id);
          await env.LICENSES.delete("order:" + body.order_id);
        }
      }
      return jsonResp({ ok: true });
    }

    // 1. App Auto-Check Endpoint: GET /api/check?email=...&device=...
    if (path === "/api/check" && request.method === "GET") {
      if (!env.ECDSA_PRIVATE_KEY_PKCS8) {
        return jsonResp({ active: false, reason: "signing_not_configured" }, 500);
      }
      const email = (url.searchParams.get("email") || "").trim().toLowerCase();
      const device = (url.searchParams.get("device") || "").replace(/[^a-fA-F0-9a-zA-Z_-]/g, "").slice(0, 32);
      if (!EMAIL_STRICT_REGEX.test(email)) {
        return jsonResp({ active: false, reason: "invalid_email" }, 400);
      }

      if (await isOwnerEmail(email)) {
        const token = await signOfflineToken(20991231, env.ECDSA_PRIVATE_KEY_PKCS8);
        return jsonResp({ active: true, tier: "OWNER", expiresYmd: 20991231, token });
      }

      const key = "lic:" + email;
      const rec = await env.LICENSES.get(key, { type: "json" });
      if (!rec) {
        return jsonResp({ active: false, reason: "not_found" });
      }
      if (rec.expiresYmd < todayYmd()) {
        return jsonResp({ active: false, reason: "expired", expiresYmd: rec.expiresYmd });
      }

      if (device) {
        const list = Array.isArray(rec.deviceIds) ? rec.deviceIds : [];
        if (!list.includes(device)) {
          if (list.length >= MAX_DEVICES_PER_EMAIL) {
            return jsonResp({ active: false, reason: "device_limit" });
          }
          list.push(device);
          rec.deviceIds = list;
          await env.LICENSES.put(key, JSON.stringify(rec));
        }
      }

      const token = await signOfflineToken(rec.expiresYmd, env.ECDSA_PRIVATE_KEY_PKCS8);
      return jsonResp({
        active: true,
        tier: rec.tier,
        expiresYmd: rec.expiresYmd,
        token
      });
    }

    // 2. Tako Webhook (Indonesia): POST /webhook/tako
    if (path === "/webhook/tako" && request.method === "POST") {
      const provided = readSecretFromRequest(request, url);
      if (!env.WEBHOOK_SECRET || !(await safeEqual(provided, env.WEBHOOK_SECRET))) {
        return jsonResp({ ok: false, error: "unauthorized" }, 401);
      }
      const data = await request.json().catch(() => null);
      if (!data) return jsonResp({ ok: false, error: "bad_request" }, 400);

      const txId = data.id || data.transaction_id || data.data?.id || "";
      if (!(await claimWebhookTxOnce(env, "tako", txId))) {
        return jsonResp({ ok: true, duplicate: true });
      }

      const amount = data.amount ?? data.total_amount ?? data.unit_price ?? data.data?.amount ?? 0;
      const email = extractEmailFromAny(
        data.email,
        data.supporter_email,
        data.gifterEmail,
        data.data?.email,
        data.message,
        data.note,
        data.data?.message
      );
      const plan = mapIdrToPlan(amount);
      if (!email || !plan) {
        return jsonResp({ ok: true, ignored: true, reason: !email ? "no_email_in_payload" : "below_min_amount" });
      }
      const granted = await grantPro(env, email, plan.tier, plan.days, "tako");
      return jsonResp(granted);
    }

    // 3. Gumroad Ping Webhook (Global): POST /webhook/gumroad
    if (path === "/webhook/gumroad" && request.method === "POST") {
      const provided = readSecretFromRequest(request, url);
      if (!env.WEBHOOK_SECRET || !(await safeEqual(provided, env.WEBHOOK_SECRET))) {
        return jsonResp({ ok: false, error: "unauthorized" }, 401);
      }
      const ct = request.headers.get("content-type") || "";
      let email = "", priceCents = 0, variant = "", txId = "";
      if (ct.includes("application/json")) {
        const j = await request.json().catch(() => null);
        if (!j) return jsonResp({ ok: false, error: "bad_request" }, 400);
        email = extractEmailFromAny(j.email, j.purchaser_email);
        priceCents = Number(j.price || 0);
        variant = String(j.variants || j.product_name || "");
        txId = String(j.sale_id || "");
      } else {
        const fd = await request.formData().catch(() => null);
        if (!fd) return jsonResp({ ok: false, error: "bad_request" }, 400);
        email = extractEmailFromAny(fd.get("email"), fd.get("purchaser_email"));
        priceCents = Number(fd.get("price") || 0);
        variant = String(fd.get("variants[Tier]") || fd.get("variants[Plan]") || fd.get("product_name") || "");
        txId = String(fd.get("sale_id") || "");
      }
      if (!(await claimWebhookTxOnce(env, "gumroad", txId))) {
        return jsonResp({ ok: true, duplicate: true });
      }
      const plan = mapUsdToPlan(priceCents / 100, variant);
      if (!email || !plan) {
        return jsonResp({ ok: true, ignored: true });
      }
      const granted = await grantPro(env, email, plan.tier, plan.days, "gumroad");
      return jsonResp(granted);
    }

    // 4. Ko-fi Webhook (Global Alternative): POST /webhook/kofi
    if (path === "/webhook/kofi" && request.method === "POST") {
      const provided = readSecretFromRequest(request, url);
      if (!env.WEBHOOK_SECRET || !(await safeEqual(provided, env.WEBHOOK_SECRET))) {
        return jsonResp({ ok: false, error: "unauthorized" }, 401);
      }
      const ct = (request.headers.get("content-type") || "").toLowerCase();
      let raw = "";
      if (ct.includes("multipart/form-data") || ct.includes("application/x-www-form-urlencoded")) {
        const fd = await request.formData().catch(() => null);
        if (!fd) return jsonResp({ ok: false, error: "bad_request" }, 400);
        raw = String(fd.get("data") || "");
      } else {
        raw = await request.text().catch(() => "");
      }
      let j;
      try {
        j = JSON.parse(raw || "invalid");
      } catch (_err) {
        return jsonResp({ ok: false, error: "bad_request" }, 400);
      }
      const txId = String(j.kofi_transaction_id || j.message_id || "");
      if (!(await claimWebhookTxOnce(env, "kofi", txId))) {
        return jsonResp({ ok: true, duplicate: true });
      }
      const email = extractEmailFromAny(j.email, j.message);
      const plan = mapUsdToPlan(Number(j.amount || 0), j.tier_name || j.message);
      if (!email || !plan) return jsonResp({ ok: true, ignored: true });
      const granted = await grantPro(env, email, plan.tier, plan.days, "kofi");
      return jsonResp(granted);
    }

    // 5. Developer Manual Grant: GET or POST /admin/grant
    if (path === "/admin/grant" && (request.method === "GET" || request.method === "POST")) {
      const provided = readSecretFromRequest(request, url);
      if (!env.ADMIN_SECRET || !(await safeEqual(provided, env.ADMIN_SECRET))) {
        return jsonResp({ ok: false, error: "unauthorized" }, 401);
      }
      const email = url.searchParams.get("email") || "";
      const planCode = (url.searchParams.get("plan") || "1Y").toUpperCase();
      const daysMap = { "1M": 30, "6M": 180, "1Y": 365, "LIFETIME": 99999 };
      const days = daysMap[planCode] || 365;
      const granted = await grantPro(env, email, planCode, days, "admin");
      return jsonResp(granted);
    }

    return jsonResp({ service: "FramePulse License & Telemetry API", status: "ok" });
  }
};
