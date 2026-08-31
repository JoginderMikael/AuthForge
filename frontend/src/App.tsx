import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { decodeJwt, errorMessage, jsonRequest, request } from "./api";
import type {
  ActivityEntry,
  ApiResult,
  AuthResponse,
  ClientRegistrationResponse,
  HealthResponse,
  HealthState,
  MachineTokenResponse,
  TokenResponse,
  ValidationResponse,
} from "./types";

type View = "client" | "user" | "social";
type Notice = { kind: "success" | "error" | "info"; message: string } | null;

const backendUrl = (import.meta.env.VITE_AUTHFORGE_API_URL || "http://localhost:8082").replace(/\/$/, "");

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function compactToken(token?: string) {
  if (!token) return "Not captured yet";
  if (token.length < 30) return token;
  return `${token.slice(0, 14)}…${token.slice(-10)}`;
}

function copyToClipboard(value: string, onNotice: (notice: Notice) => void) {
  void navigator.clipboard
    .writeText(value)
    .then(() => onNotice({ kind: "success", message: "Copied to clipboard" }))
    .catch(() => onNotice({ kind: "error", message: "Clipboard access was not available" }));
}

function App() {
  const [view, setView] = useState<View>("client");
  const [health, setHealth] = useState<HealthState>("checking");
  const [activities, setActivities] = useState<ActivityEntry[]>([]);
  const [notice, setNotice] = useState<Notice>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const [client, setClient] = useState<ClientRegistrationResponse | null>(null);
  const [clientName, setClientName] = useState("Local test application");
  const [redirectUri, setRedirectUri] = useState("http://localhost:5173");
  const [scopes, setScopes] = useState("api.read, api.write");
  const [bootstrapToken, setBootstrapToken] = useState("");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [machineScope, setMachineScope] = useState("api.read");
  const [machineToken, setMachineToken] = useState<MachineTokenResponse | null>(null);

  const [email, setEmail] = useState("ada@example.com");
  const [password, setPassword] = useState("securePassword123");
  const [firstName, setFirstName] = useState("Ada");
  const [lastName, setLastName] = useState("Lovelace");
  const [userToken, setUserToken] = useState<TokenResponse | null>(null);
  const [validation, setValidation] = useState<boolean | null>(null);

  const [exchangeCode, setExchangeCode] = useState("");

  const recordRequest = useCallback(<T,>(method: string, path: string, result: ApiResult<T>) => {
    setActivities((current) => [
      {
        id: Date.now() + Math.random(),
        method,
        path,
        status: result.status,
        ok: result.ok,
        durationMs: result.durationMs,
        createdAt: new Date(),
        response: result.data,
      },
      ...current,
    ].slice(0, 20));
  }, []);

  const checkHealth = useCallback(async () => {
    setHealth("checking");
    const result = await request<HealthResponse>("/actuator/health");
    recordRequest("GET", "/actuator/health", result);
    setHealth(result.ok && result.data?.status?.toUpperCase() === "UP" ? "online" : "offline");
  }, [recordRequest]);

  useEffect(() => {
    void checkHealth();
  }, [checkHealth]);

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(null), 4200);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const userClaims = useMemo(
    () => (userToken?.accessToken ? decodeJwt(userToken.accessToken) : null),
    [userToken],
  );
  const machineClaims = useMemo(
    () => (machineToken?.access_token ? decodeJwt(machineToken.access_token) : null),
    [machineToken],
  );

  async function provisionClient(event: FormEvent) {
    event.preventDefault();
    setBusy("client");
    const path = "/api/clients/register";
    const result = await request<ClientRegistrationResponse>(
      path,
      jsonRequest(
        "POST",
        {
          name: clientName,
          redirectUri: redirectUri || null,
          scopes: scopes.split(",").map((scope) => scope.trim()).filter(Boolean),
        },
        { "X-AuthForge-Bootstrap-Token": bootstrapToken },
      ),
    );
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setClient(result.data);
    setClientId(result.data.clientId);
    setClientSecret(result.data.clientSecret);
    setMachineScope(result.data.scopes?.[0] || "api.read");
    setNotice({ kind: "success", message: "Client provisioned — save the secret while it is visible" });
  }

  async function requestMachineToken(event: FormEvent) {
    event.preventDefault();
    setBusy("machine-token");
    const path = "/oauth2/token";
    const body = new URLSearchParams({ grant_type: "client_credentials", scope: machineScope });
    const result = await request<MachineTokenResponse>(path, {
      method: "POST",
      headers: {
        Authorization: `Basic ${btoa(`${clientId}:${clientSecret}`)}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body,
    });
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setMachineToken(result.data);
    setNotice({ kind: "success", message: "Machine access token issued" });
  }

  async function registerUser(event: FormEvent) {
    event.preventDefault();
    setBusy("register");
    const path = "/auth/register";
    const result = await request<AuthResponse>(
      path,
      jsonRequest("POST", { email, password, firstName, lastName, clientId }),
    );
    recordRequest("POST", path, result);
    setBusy(null);
    setNotice({
      kind: result.ok ? "success" : "error",
      message: result.ok ? result.data.message : errorMessage(result.data, result.status),
    });
  }

  async function loginUser(event: FormEvent) {
    event.preventDefault();
    setBusy("login");
    const path = "/auth/login";
    const result = await request<TokenResponse>(
      path,
      jsonRequest("POST", { email, password, clientId }),
    );
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setUserToken(result.data);
    setValidation(null);
    setNotice({ kind: "success", message: `Signed in as ${result.data.email || email}` });
  }

  async function refreshUserToken() {
    if (!userToken?.refreshToken) return;
    setBusy("refresh");
    const path = "/auth/refresh-token";
    const result = await request<TokenResponse>(
      path,
      jsonRequest("POST", { refreshToken: userToken.refreshToken }),
    );
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setUserToken(result.data);
    setValidation(null);
    setNotice({ kind: "success", message: "Tokens rotated successfully" });
  }

  async function validateUserToken() {
    if (!userToken?.accessToken) return;
    setBusy("validate");
    const path = "/auth/validate";
    const result = await request<ValidationResponse>(path, {
      method: "POST",
      headers: { Authorization: `Bearer ${userToken.accessToken}` },
    });
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setValidation(result.data.valid);
    setNotice({
      kind: result.data.valid ? "success" : "error",
      message: result.data.valid ? "The access token is valid" : "The access token is invalid",
    });
  }

  function startSocialLogin(provider: "google" | "github") {
    if (!clientId) {
      setNotice({ kind: "error", message: "Enter or provision a client ID first" });
      return;
    }
    const url = `${backendUrl}/auth/oauth2/authorize/${provider}?clientId=${encodeURIComponent(clientId)}`;
    window.open(url, "_blank", "noopener,noreferrer");
    setNotice({ kind: "info", message: `Opened ${provider} sign-in in a new tab` });
  }

  async function exchangeSocialCode(event: FormEvent) {
    event.preventDefault();
    setBusy("exchange");
    const path = "/auth/oauth2/exchange";
    const result = await request<TokenResponse>(path, jsonRequest("POST", { code: exchangeCode }));
    recordRequest("POST", path, result);
    setBusy(null);

    if (!result.ok) {
      setNotice({ kind: "error", message: errorMessage(result.data, result.status) });
      return;
    }

    setUserToken(result.data);
    setClientId(result.data.clientId || clientId);
    setView("user");
    setNotice({ kind: "success", message: "One-time code exchanged for user tokens" });
  }

  const selectedToken = userToken?.accessToken || machineToken?.access_token;

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="AuthForge Test Console home">
          <span className="brand-mark">AF</span>
          <span>
            <strong>AuthForge</strong>
            <small>Test console</small>
          </span>
        </a>
        <div className="backend-status">
          <span className={`status-dot ${health}`} aria-hidden="true" />
          <span>
            <small>Backend</small>
            <strong>{health === "checking" ? "Checking…" : health === "online" ? "Online" : "Offline"}</strong>
          </span>
          <button className="text-button" type="button" onClick={() => void checkHealth()}>
            Retry
          </button>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div>
            <p className="eyebrow">Local identity workbench</p>
            <h1>Test every auth path.<br /><span>See every response.</span></h1>
          </div>
          <div className="hero-note">
            <span className="hero-note-index">01—03</span>
            <p>Provision a tenant, authenticate a user, then inspect token claims without leaving the browser.</p>
            <code>{backendUrl}</code>
          </div>
        </section>

        <nav className="journey-nav" aria-label="Test journey">
          <JourneyButton number="01" label="Client setup" active={view === "client"} complete={Boolean(client)} onClick={() => setView("client")} />
          <JourneyButton number="02" label="User tokens" active={view === "user"} complete={Boolean(userToken)} onClick={() => setView("user")} />
          <JourneyButton number="03" label="Social OAuth" active={view === "social"} complete={false} onClick={() => setView("social")} />
        </nav>

        <div className="workspace-grid">
          <section className="workbench" aria-live="polite">
            {view === "client" && (
              <div className="view-stack">
                <SectionHeading
                  kicker="Tenant boundary"
                  title="Provision a test client"
                  description="Use the bootstrap token from your local .env. The generated client secret is returned once."
                />
                <form className="form-grid" onSubmit={(event) => void provisionClient(event)}>
                  <Field label="Client name" htmlFor="client-name">
                    <input id="client-name" value={clientName} onChange={(event) => setClientName(event.target.value)} required />
                  </Field>
                  <Field label="Redirect URI" htmlFor="redirect-uri" hint="Optional">
                    <input id="redirect-uri" type="url" value={redirectUri} onChange={(event) => setRedirectUri(event.target.value)} />
                  </Field>
                  <Field label="Scopes" htmlFor="scopes" hint="Comma-separated" wide>
                    <input id="scopes" value={scopes} onChange={(event) => setScopes(event.target.value)} required />
                  </Field>
                  <Field label="Bootstrap token" htmlFor="bootstrap-token" hint="Never stored" wide>
                    <input
                      id="bootstrap-token"
                      type="password"
                      value={bootstrapToken}
                      onChange={(event) => setBootstrapToken(event.target.value)}
                      autoComplete="off"
                      required
                    />
                  </Field>
                  <div className="form-action wide">
                    <button className="primary-button" disabled={busy !== null} type="submit">
                      {busy === "client" ? "Provisioning…" : "Provision client"}<span>↗</span>
                    </button>
                  </div>
                </form>

                {client && (
                  <div className="credential-card">
                    <div className="credential-card-head">
                      <span className="success-mark">✓</span>
                      <div><small>Client ready</small><strong>{client.name}</strong></div>
                    </div>
                    <SecretRow label="Client ID" value={client.clientId} onCopy={() => copyToClipboard(client.clientId, setNotice)} />
                    <SecretRow label="Client secret" value={client.clientSecret} onCopy={() => copyToClipboard(client.clientSecret, setNotice)} sensitive />
                  </div>
                )}

                <div className="divider" />
                <SectionHeading
                  kicker="Service authentication"
                  title="Issue a machine token"
                  description="Call the standards-based client_credentials endpoint using HTTP Basic authentication."
                  compact
                />
                <form className="form-grid" onSubmit={(event) => void requestMachineToken(event)}>
                  <Field label="Client ID" htmlFor="machine-client-id">
                    <input id="machine-client-id" value={clientId} onChange={(event) => setClientId(event.target.value)} required />
                  </Field>
                  <Field label="Client secret" htmlFor="machine-client-secret">
                    <input id="machine-client-secret" type="password" value={clientSecret} onChange={(event) => setClientSecret(event.target.value)} required />
                  </Field>
                  <Field label="Requested scope" htmlFor="machine-scope" wide>
                    <input id="machine-scope" value={machineScope} onChange={(event) => setMachineScope(event.target.value)} required />
                  </Field>
                  <div className="form-action wide">
                    <button className="secondary-button" disabled={busy !== null} type="submit">
                      {busy === "machine-token" ? "Requesting…" : "Request machine token"}<span>→</span>
                    </button>
                  </div>
                </form>
                {machineToken && <TokenInspector title="Machine access token" token={machineToken.access_token} claims={machineClaims} onCopy={() => copyToClipboard(machineToken.access_token, setNotice)} />}
              </div>
            )}

            {view === "user" && (
              <div className="view-stack">
                <SectionHeading
                  kicker="Tenant user flow"
                  title="Register and sign in"
                  description="Both operations use the same email, password, and active client context below."
                />
                <div className="shared-context">
                  <Field label="Client ID" htmlFor="user-client-id" wide>
                    <input id="user-client-id" value={clientId} onChange={(event) => setClientId(event.target.value)} placeholder="authforge_…" required />
                  </Field>
                  <Field label="Email" htmlFor="user-email">
                    <input id="user-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required />
                  </Field>
                  <Field label="Password" htmlFor="user-password">
                    <input id="user-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
                  </Field>
                </div>

                <div className="dual-actions">
                  <form className="action-card" onSubmit={(event) => void registerUser(event)}>
                    <div className="action-card-number">A</div>
                    <h3>Create user</h3>
                    <p>Add this identity to the active client with the default user role.</p>
                    <div className="mini-grid">
                      <Field label="First name" htmlFor="first-name">
                        <input id="first-name" value={firstName} onChange={(event) => setFirstName(event.target.value)} />
                      </Field>
                      <Field label="Last name" htmlFor="last-name">
                        <input id="last-name" value={lastName} onChange={(event) => setLastName(event.target.value)} />
                      </Field>
                    </div>
                    <button className="secondary-button full" disabled={busy !== null} type="submit">
                      {busy === "register" ? "Registering…" : "Register user"}<span>→</span>
                    </button>
                  </form>
                  <form className="action-card dark" onSubmit={(event) => void loginUser(event)}>
                    <div className="action-card-number">B</div>
                    <h3>Sign in</h3>
                    <p>Verify the credentials and capture the user access and refresh tokens.</p>
                    <div className="signin-summary">
                      <span>{email || "Email required"}</span>
                      <span>{clientId ? compactToken(clientId) : "Client required"}</span>
                    </div>
                    <button className="accent-button full" disabled={busy !== null} type="submit">
                      {busy === "login" ? "Signing in…" : "Sign in & inspect"}<span>↗</span>
                    </button>
                  </form>
                </div>

                {userToken && (
                  <>
                    <div className="token-actions">
                      <div>
                        <small>Signed in</small>
                        <strong>{userToken.email}</strong>
                        <span>{userToken.roles?.join(" · ")}</span>
                      </div>
                      <button className="secondary-button small" type="button" disabled={busy !== null} onClick={() => void validateUserToken()}>
                        {busy === "validate" ? "Validating…" : "Validate token"}
                      </button>
                      <button className="secondary-button small" type="button" disabled={busy !== null} onClick={() => void refreshUserToken()}>
                        {busy === "refresh" ? "Rotating…" : "Rotate tokens"}
                      </button>
                      {validation !== null && <span className={`validation-chip ${validation ? "valid" : "invalid"}`}>{validation ? "Valid" : "Invalid"}</span>}
                    </div>
                    <TokenInspector title="User access token" token={userToken.accessToken} claims={userClaims} onCopy={() => copyToClipboard(userToken.accessToken, setNotice)} />
                    <SecretRow label="Refresh token" value={userToken.refreshToken} onCopy={() => copyToClipboard(userToken.refreshToken, setNotice)} sensitive />
                  </>
                )}
              </div>
            )}

            {view === "social" && (
              <div className="view-stack">
                <SectionHeading
                  kicker="Provider authentication"
                  title="Test social sign-in"
                  description="Start the provider redirect, then exchange the one-time code returned by AuthForge."
                />
                <div className="social-context">
                  <Field label="Client ID" htmlFor="social-client-id" wide>
                    <input id="social-client-id" value={clientId} onChange={(event) => setClientId(event.target.value)} placeholder="authforge_…" required />
                  </Field>
                </div>
                <div className="provider-grid">
                  <button className="provider-button" type="button" onClick={() => startSocialLogin("google")}>
                    <span className="provider-letter google">G</span>
                    <span><small>Continue with</small><strong>Google</strong></span>
                    <b>↗</b>
                  </button>
                  <button className="provider-button" type="button" onClick={() => startSocialLogin("github")}>
                    <span className="provider-letter github">GH</span>
                    <span><small>Continue with</small><strong>GitHub</strong></span>
                    <b>↗</b>
                  </button>
                </div>
                <div className="flow-callout">
                  <span>How it returns</span>
                  <p>The backend callback displays a short-lived code. Copy it, return here, and exchange it within one minute.</p>
                </div>
                <form className="exchange-form" onSubmit={(event) => void exchangeSocialCode(event)}>
                  <Field label="One-time exchange code" htmlFor="exchange-code" wide>
                    <textarea id="exchange-code" value={exchangeCode} onChange={(event) => setExchangeCode(event.target.value)} rows={3} placeholder="Paste the code returned by /auth/oauth2/callback" required />
                  </Field>
                  <button className="primary-button" disabled={busy !== null} type="submit">
                    {busy === "exchange" ? "Exchanging…" : "Exchange for tokens"}<span>→</span>
                  </button>
                </form>
              </div>
            )}
          </section>

          <aside className="console-panel">
            <div className="console-head">
              <div><span className="live-dot" />Live activity</div>
              <button type="button" onClick={() => setActivities([])} disabled={!activities.length}>Clear</button>
            </div>
            <div className="console-body">
              {activities.length === 0 ? (
                <div className="empty-console"><span>_</span><p>API responses will appear here as you test each flow.</p></div>
              ) : activities.map((activity) => <ActivityItem key={activity.id} activity={activity} />)}
            </div>
            <div className="console-foot">
              <span>Active token</span>
              <code>{compactToken(selectedToken)}</code>
            </div>
          </aside>
        </div>
      </main>

      <footer>
        <span>AUTHFORGE / LOCAL TEST CONSOLE</span>
        <span>Secrets remain in this browser tab only.</span>
      </footer>

      {notice && <div className={`toast ${notice.kind}`} role="status"><span>{notice.kind === "success" ? "✓" : notice.kind === "error" ? "!" : "i"}</span>{notice.message}</div>}
    </div>
  );
}

function JourneyButton({ number, label, active, complete, onClick }: { number: string; label: string; active: boolean; complete: boolean; onClick: () => void }) {
  return (
    <button className={`${active ? "active" : ""}`} type="button" onClick={onClick} aria-current={active ? "step" : undefined}>
      <span>{complete ? "✓" : number}</span><strong>{label}</strong><b>→</b>
    </button>
  );
}

function SectionHeading({ kicker, title, description, compact = false }: { kicker: string; title: string; description: string; compact?: boolean }) {
  return (
    <div className={`section-heading ${compact ? "compact" : ""}`}>
      <p>{kicker}</p><h2>{title}</h2><span>{description}</span>
    </div>
  );
}

function Field({ label, htmlFor, hint, wide = false, children }: { label: string; htmlFor: string; hint?: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <label className={`field ${wide ? "wide" : ""}`} htmlFor={htmlFor}>
      <span>{label}{hint && <small>{hint}</small>}</span>{children}
    </label>
  );
}

function SecretRow({ label, value, onCopy, sensitive = false }: { label: string; value: string; onCopy: () => void; sensitive?: boolean }) {
  const [revealed, setRevealed] = useState(!sensitive);
  return (
    <div className="secret-row">
      <span>{label}</span>
      <code>{revealed ? value : "••••••••••••••••••••••••"}</code>
      {sensitive && <button type="button" onClick={() => setRevealed((current) => !current)}>{revealed ? "Hide" : "Reveal"}</button>}
      <button type="button" onClick={onCopy}>Copy</button>
    </div>
  );
}

function TokenInspector({ title, token, claims, onCopy }: { title: string; token: string; claims: Record<string, unknown> | null; onCopy: () => void }) {
  return (
    <div className="token-inspector">
      <div className="token-inspector-head"><span>{title}</span><button type="button" onClick={onCopy}>Copy token</button></div>
      <code className="token-value">{compactToken(token)}</code>
      <div className="claims-head"><span>Decoded JWT payload</span><small>Local decode · signature not verified here</small></div>
      <pre>{claims ? formatJson(claims) : "Token is not a JWT or could not be decoded."}</pre>
    </div>
  );
}

function ActivityItem({ activity }: { activity: ActivityEntry }) {
  return (
    <details className="activity-item">
      <summary>
        <span className={`activity-status ${activity.ok ? "ok" : "failed"}`} />
        <code>{activity.method}</code>
        <strong>{activity.path}</strong>
        <small>{activity.status || "ERR"} · {activity.durationMs}ms</small>
      </summary>
      <pre>{formatJson(activity.response)}</pre>
      <time>{activity.createdAt.toLocaleTimeString()}</time>
    </details>
  );
}

export default App;
