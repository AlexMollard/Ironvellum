const main = document.querySelector("main");
const consent = location.pathname.startsWith("/consent/");
const requestId = consent ? location.pathname.split("/").pop() : null;
const consentApi = "/consent/api/" + requestId;
const scopes = new Map([
  ["training:read", "Read synced completed workouts and logged sets"],
  ["progress:read", "Read synced training progress"],
  ["savedworkouts:read", "Read synced saved workouts"],
  ["workoutdrafts:create", "Suggest workout drafts for review on your phone"],
]);
const tools = new Map([
  ["list_workouts", "Listed workouts"], ["get_workout", "Read a workout"],
  ["get_progress", "Read progress"], ["list_saved_workouts", "Read saved workouts"],
  ["search_movements", "Searched movements"], ["create_workout_draft", "Suggested a workout"],
  ["get_workout_draft", "Checked a draft"],
]);
const disclosure = "Selected synced data goes to this AI provider. Your phone may have newer training. Disconnecting stops future access. The provider may keep data already shared.";
let config, request, accountSession, expiryTimer, stopped = false;
let managementRevision = 0;

function node(tag, text, parent = main) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = text;
  parent.append(el);
  return el;
}
function button(label, action, parent = main, secondary = false) {
  const el = node("button", label, parent);
  el.type = "button";
  if (secondary) el.className = "secondary";
  el.addEventListener("click", action);
  return el;
}
function heading(title) {
  main.replaceChildren();
  const h = node("h1", title);
  h.tabIndex = -1;
  h.focus();
}
function notice(text, parent = main) {
  const p = node("p", text, parent);
  p.setAttribute("role", "status");
  return p;
}
function endRequest() {
  stopped = true;
  accountSession = undefined;
  clearTimeout(expiryTimer);
  heading("Connection request unavailable");
  node("p", "This request expired, was replaced or has already been decided. Start a new connection from your AI app.");
}
function current() {
  if (stopped) return false;
  if (consent && request && Date.now() >= request.expiresAtMs) { endRequest(); return false; }
  return true;
}
class Unavailable extends Error {}
class RequestEnded extends Error {}
async function json(url, options = {}) {
  const response = await fetch(url, {cache: "no-store", redirect: "error", credentials: "same-origin", ...options});
  if (!response.ok) {
    if (url === consentApi && [400, 403, 404].includes(response.status)) throw new RequestEnded();
    throw new Unavailable();
  }
  return response.json();
}
function post(body, headers, credentials = "omit") {
  return {method: "POST", credentials, headers: {"Content-Type": "application/json", ...headers}, body: JSON.stringify(body)};
}
function rpc(path, args) {
  return json(config.backendUrl + "/rest/v1/rpc/" + path,
    post(args, {apikey: config.publicKey, Authorization: "Bearer " + accountSession.token}));
}
const date = ms => new Date(ms).toLocaleString();
function issue(err) {
  if (err instanceof RequestEnded || !current()) { endRequest(); return; }
  heading("Couldn’t reach Ironvellum");
  notice("Check your connection and try again.");
  button("Try again", () => location.reload());
}
async function start() {
  config = await json("/ui-config");
  if (consent) {
    request = await json(consentApi);
    if (request.requestId !== requestId || !request.scopes.every(s => scopes.has(s))) throw new Unavailable();
    if (!current()) return;
    expiryTimer = setTimeout(endRequest, Math.max(0, request.expiresAtMs - Date.now()));
  }
  login();
}
function login() {
  heading("Sign in to Ironvellum");
  node("p", consent ? "Review the permissions requested by " + request.clientName + " (unverified)." : "Manage access to your synced training.");
  node("p", "Your Ironvellum sign-in stays with Ironvellum.");
  const form = node("form");
  function field(label, type, complete) {
    const input = node("input", undefined, node("label", label, form));
    input.type = type;
    input.autocomplete = complete;
    input.required = true;
    return input;
  }
  const email = field("Email", "email", "username");
  const password = field("Password", "password", "current-password");
  const submit = node("button", "Sign in", form);
  submit.type = "submit";
  const provider = config.googleCallback ? button("Continue with Google", () => authenticate(google), form, true) : null;
  const status = notice("", form);
  async function authenticate(getSession) {
    submit.disabled = true;
    if (provider) provider.disabled = true;
    status.textContent = "Signing in…";
    try {
      const session = await getSession();
      if (!current()) return;
      if (!session.access_token) throw new Unavailable();
      accountSession = {token: session.access_token};
      password.value = "";
      if (consent) choices();
      else await manage();
    } catch (err) {
      password.value = "";
      if (err instanceof RequestEnded) { endRequest(); return; }
      status.textContent = "Sign-in was unsuccessful. Check your sign-in details or try again.";
    } finally {
      submit.disabled = false;
      if (provider) provider.disabled = false;
    }
  }
  form.addEventListener("submit", event => {
    event.preventDefault();
    authenticate(() => json(config.backendUrl + "/auth/v1/token?grant_type=password",
      post({email: email.value, password: password.value}, {apikey: config.publicKey})));
  });
}
async function google() {
  // The original tab holds the verifier and a reference to this one popup.
  const popup = window.open("about:blank", "_blank", "popup,width=520,height=720");
  if (!popup) throw new Unavailable();
  popup.opener = null;
  const encode = bytes => btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
  try {
    const verifier = encode(crypto.getRandomValues(new Uint8Array(32)));
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
    const url = new URL("/auth/v1/authorize", config.backendUrl);
    url.search = new URLSearchParams({provider: "google", redirect_to: config.googleCallback,
      code_challenge: encode(new Uint8Array(hash)), code_challenge_method: "s256"});
    popup.location.replace(url.href);
    const deadline = Date.now() + 300000;
    while (!popup.closed && current() && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 250));
      let value;
      try { value = new URL(popup.location.href); } catch { continue; }
      if (value.origin + value.pathname !== config.googleCallback) continue;
      const code = value.searchParams.get("code");
      if (!code || value.hash) throw new Unavailable();
      popup.close();
      if (consent && (await json(consentApi)).csrf !== request.csrf) throw new RequestEnded();
      return await json(config.backendUrl + "/auth/v1/token?grant_type=pkce",
        post({auth_code: code, code_verifier: verifier}, {apikey: config.publicKey}));
    }
    throw new Unavailable();
  } finally { popup.close(); }
}
function choices() {
  heading("Connect your AI");
  node("h2", request.clientName);
  node("p", "Unverified client name · Request expires " + date(request.expiresAtMs));
  const group = node("fieldset");
  node("legend", "Choose permissions", group);
  const checks = request.scopes.map(scope => {
    const label = node("label", undefined, group);
    label.className = "choice";
    const box = node("input", undefined, label);
    box.type = "checkbox";
    box.value = scope;
    box.checked = true;
    node("span", scopes.get(scope), label);
    return box;
  });
  node("p", disclosure);
  node("p", "Private notes, photos and raw health measurements are excluded.");
  const allow = button("Allow selected permissions", () => decide(true));
  const deny = button("Deny", () => decide(false), main, true);
  const status = notice("");
  group.addEventListener("change", () => { allow.disabled = !checks.some(box => box.checked); });
  async function decide(approved) {
    if (!current()) return;
    allow.disabled = deny.disabled = group.disabled = true;
    status.textContent = "Sending your decision…";
    try {
      const selected = approved ? checks.filter(box => box.checked).map(box => box.value) : [];
      const result = await json(consentApi, post({approved, scopes: selected, csrf: request.csrf},
        {Authorization: "Bearer " + accountSession.token}, "same-origin"));
      clearTimeout(expiryTimer);
      stopped = true;
      accountSession = undefined;
      heading(approved ? "Connection allowed" : "Connection denied");
      notice("Returning to your AI app…");
      location.replace(result.redirectUrl);
    } catch (err) { issue(err); }
  }
}
async function manage(message = "") {
  const owner = accountSession;
  if (!owner || stopped) return;
  const revision = ++managementRevision;
  const sameSession = () => !stopped && accountSession === owner;
  const latest = () => sameSession() && revision === managementRevision;
  heading("AI connections");
  const loading = notice("Loading connections…");
  try {
    const data = await rpc("ironvellum_mcp_connections", {});
    if (!latest()) return;
    if (data.schemaVersion !== 1 || data.ok !== true || !data.resourceUrl ||
        !Array.isArray(data.connections) || !Array.isArray(data.recentAccess)) throw new Unavailable();
    loading.remove();
    if (message) notice(message);
    node("p", "Use this server address in your compatible AI app.");
    node("code", data.resourceUrl);
    node("p", disclosure);
    node("h2", "Connections");
    if (!data.connections.length) node("p", "No connections yet.");
    for (const grant of data.connections) {
      const card = node("section");
      node("h2", grant.clientName, card);
      node("p", "Unverified client name", card);
      node("p", grant.scopes.map(s => scopes.get(s) || "Unrecognized permission").join(" · "), card);
      node("p", grant.revokedAtMs != null ? "Disconnected " + date(grant.revokedAtMs) : "Access expires " + date(grant.expiresAtMs), card);
      if (grant.revokedAtMs != null || grant.expiresAtMs <= Date.now()) continue;
      const disconnect = button("Disconnect " + grant.clientName, async () => {
        if (!latest()) return;
        disconnect.disabled = true;
        try {
          const result = await rpc("ironvellum_mcp_revoke_connection", {p_grant_id: grant.id});
          if (result.schemaVersion !== 1 || result.ok !== true || result.revoked !== true) throw new Unavailable();
          // The committed change supersedes any read started while disconnection was pending.
          if (sameSession()) await manage("Disconnected " + grant.clientName + ".");
        } catch {
          if (!latest()) return;
          notice("Couldn’t confirm disconnection. Refresh to check access.", card);
          disconnect.disabled = false;
        }
      }, card, true);
    }
    node("h2", "Recent access");
    if (!data.recentAccess.length) node("p", "No recent access.");
    for (const row of data.recentAccess) {
      const card = node("section");
      node("strong", row.clientName, card);
      node("p", (tools.get(row.tool) || "Tool request") + " · " + (row.result === "ok" ? "Allowed" : "Unsuccessful") + " · " + date(row.atMs), card);
    }
    button("Refresh", () => manage());
    button("Leave account", () => { accountSession = undefined; location.reload(); }, main, true);
  } catch {
    if (!latest()) return;
    loading.textContent = "AI connections are unavailable on this backend. Check your connection and try again.";
    button("Try again", () => manage());
    button("Sign in again", () => location.reload(), main, true);
  }
}
window.addEventListener("pagehide", () => { stopped = true; accountSession = undefined; clearTimeout(expiryTimer); });
window.addEventListener("pageshow", event => { if (event.persisted) location.reload(); });
start().catch(issue);
