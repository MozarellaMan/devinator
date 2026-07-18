# DevinBridge

An event-driven automation that receives GitHub webhook events, orchestrates
[Devin AI](https://devin.ai) sessions to remediate labelled issues, and reports the resulting pull request back on the
issue — with a live status dashboard.

When an issue is labelled **`devin-fix`**, DevinBridge starts a Devin session asking it to fix that issue and open a PR,
then polls the session to completion. It is driven by a **dual trigger**: GitHub webhooks (low-latency push) *and* a
60-second fallback scan that pulls labelled issues via the GitHub API (a safety net for missed or failed webhook
deliveries). Both feed one idempotent entry point, so an issue is never worked twice.

## Stack

Java 21 · Gradle · Javalin 6 · Jackson · JUnit 5. No Spring, no DI framework — every object is wired by hand in `Main`.
State lives in a `ConcurrentHashMap`; there is no database.

## Quick start (offline, no credentials)

`MOCK_DEVIN=true` swaps in a stateful fake Devin so the whole system runs end-to-end with no network calls. In this mode
`TARGET_REPO` defaults to `mock-org/mock-repo`, so nothing else is required.

```bash
MOCK_DEVIN=true ./gradlew run
```

Then open the dashboard at <http://localhost:8080> and fire a realistic GitHub
`issues` webhook at the receiver:

```bash
curl -i -X POST http://localhost:8080/webhook/github \
  -H "Content-Type: application/json" \
  -d '{
    "action": "labeled",
    "issue": {
      "number": 42,
      "title": "Null pointer exception on empty cart checkout",
      "labels": [ { "name": "bug" }, { "name": "devin-fix" } ]
    },
    "repository": { "full_name": "acme/widgets" }
  }'
```

The issue appears on the dashboard as **Queued → Running**, then **Completed** with a (fake) PR link within a few
15-second poll cycles. You can also watch the raw feed:

```bash
curl -s http://localhost:8080/status | jq
```

## Real mode

Provide real credentials and drop `MOCK_DEVIN` (or set it to `false`):

```bash
export DEVIN_API_KEY=apk_...          # Devin API key (Bearer token)
export GITHUB_TOKEN=ghp_...           # GitHub token with repo scope
export TARGET_REPO=your-org/your-repo # owner/name
# optional: require signed webhooks
export GITHUB_WEBHOOK_SECRET=...
./gradlew run
```

Point a GitHub repository webhook (Issues events) at `POST /webhook/github`. If
`GITHUB_WEBHOOK_SECRET` is set, configure the same secret on the GitHub webhook so the
`X-Hub-Signature-256` HMAC verifies; if it is unset, signature verification is skipped.

### Docker

```bash
docker compose up --build                 # real mode (set env vars first)
MOCK_DEVIN=true docker compose up --build  # offline mock mode
```

## Configuration

| Env var                 | Required            | Default              | Purpose                                        |
|-------------------------|---------------------|----------------------|------------------------------------------------|
| `MOCK_DEVIN`            | no                  | `false`              | Use the offline fake Devin client.             |
| `DEVIN_API_URL`         | no                  | `https://api.devin.ai` | Devin API base URL.                          |
| `DEVIN_API_KEY`         | real mode           | —                    | Devin API key (sent as `Bearer`).              |
| `GITHUB_TOKEN`          | real mode           | —                    | GitHub token for issue scan + PR comment.      |
| `TARGET_REPO`           | real mode           | `mock-org/mock-repo` in mock mode | Repo (`owner/name`) to watch.     |
| `GITHUB_WEBHOOK_SECRET` | no                  | — (verification off) | Enables `X-Hub-Signature-256` HMAC checking.   |
| `DEVIN_TIMEOUT_MINUTES` | no                  | `30`                 | Wall-clock cap; a stuck session then fails.    |
| `PORT`                  | no                  | `8080`               | HTTP listen port.                              |

## HTTP endpoints

| Method | Path              | Purpose                                                    |
|--------|-------------------|------------------------------------------------------------|
| `POST` | `/webhook/github` | Receive a GitHub `issues` event → orchestrate → `202`.     |
| `GET`  | `/status`         | JSON: all tracked sessions, counts by state, avg duration. |
| `GET`  | `/healthz`        | Liveness check → `ok`.                                      |
| `GET`  | `/`               | The dashboard (static, polls `/status` every 5s).          |

## Architecture

```
GitHub issue labelled "devin-fix"
        │
   ┌────┴───────────────┐
   │ webhook (push)      │  60s scan (pull, safety net)
   ▼                     ▼
  WebServer  ───────►  Orchestrator  ──► DevinClient (Http | Mock)
   /webhook/github        │  onIssueEvent / poll / scanIssues
                          ├──► SessionStore (ConcurrentHashMap, keyed by issue #)
                          └──► GitHubClient (comment the PR link back on the issue)
   /status ◄── StatusView ◄── SessionStore
```

Package layout under `dev.ayo.devinbridge`:

- **`domain/`** — the sealed `SessionState` (`Queued`/`Running`/`PrOpened`/`Completed`/`Failed`)
  and `TrackedSession`. All transitions go through one exhaustive `SessionState.advance`
  switch; illegal transitions throw `IllegalStateTransition`, and the compiler proves the switch covers every case.
- **`store/`** — `SessionStore`, an in-memory map with idempotent `register`.
- **`devin/`** — `DevinClient` with `HttpDevinClient` (v1 API: `POST /v1/sessions`,
  `GET /v1/sessions/{id}`) and `MockDevinClient`. Devin has no official Java SDK (only a Python client in
  `CognitionAI/qa-devin`), so this stays hand-rolled.
- **`github/`** — `Hub4jGitHubClient`, a thin adapter over
  [`org.kohsuke:github-api`](https://github.com/hub4j/github-api) (hub4j), the de-facto-standard Java GitHub client —
  used instead of a hand-rolled REST client.
  `WebhookParser` likewise delegates to hub4j's `GitHub.offline().parseEventPayload`
  for the webhook JSON databinding rather than hand-walking the JSON tree. The optional HMAC `WebhookVerifier` is still
  hand-rolled — hub4j has no signature verification.
- **`orchestrator/`** — `Orchestrator`, which owns the schedulers and is the only place that reacts to events. Each
  session is polled inside its own try/catch, so one failure never kills the loop.
- **`web/`** — Javalin wiring and the `StatusView` JSON shape.

### Design notes (the trade-offs)

- **Devin lifecycle.** Devin's real status enum is `working / blocked / finished /
  expired`. `working`/`blocked` keep polling; `expired` fails the session; and a wall-clock cap
  (`DEVIN_TIMEOUT_MINUTES`) fails a session that never resolves.
- **Restart behaviour.** State is in-memory and intentionally not persisted. On restart the map is empty, but the 60s
  scan re-discovers still-labelled issues — and sessions are created with Devin's `idempotent` flag, so a re-triggered
  issue is de-duplicated by Devin rather than spawning a second session.
- **Webhook auth.** HMAC verification is optional and gated on `GITHUB_WEBHOOK_SECRET`
  so the mock/curl demo stays trivial while production can require signed deliveries.

### Known limitation

Posting the PR link back on the issue requires a valid `GITHUB_TOKEN`. In mock mode the state machine still advances to
`Completed` and the dashboard shows the PR URL, but the comment POST is logged as a failure (bad credentials) rather
than silently swallowed — by design, the poll loop catches it per-session and moves on.

## Tests

```bash
./gradlew test
```

Three focused suites: state-transition legality (every legal transition + representative illegal ones), store
idempotency, and an orchestrator happy-path that drives an issue from `Queued` to `Completed` against `MockDevinClient`
and asserts the PR comment is posted exactly once.
