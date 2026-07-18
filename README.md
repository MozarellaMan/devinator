# Devinator

An event-driven automation that receives GitHub webhook events, orchestrates
[Devin AI](https://devin.ai) sessions to remediate labelled issues, and reports the resulting pull request back on the
issue + a live status dashboard.

When an issue is labelled **`devin-fix`**, Devinator starts a Devin session asking it to fix that issue and open a PR,
then polls the session to completion.

## Stack

Java 25 · Gradle · Javalin 7

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
export DEVIN_API_KEY=cog_...          # Devin service-user token (Bearer)
export DEVIN_ORG_ID=org-...           # Devin organization ID
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

| Env var                 | Required  | Default                           | Purpose                                                 |
|-------------------------|-----------|-----------------------------------|---------------------------------------------------------|
| `MOCK_DEVIN`            | no        | `false`                           | Use the offline fake Devin client.                      |
| `DEVIN_API_URL`         | no        | `https://api.devin.ai`            | Devin API base URL.                                     |
| `DEVIN_API_KEY`         | real mode | —                                 | Devin service-user token, `cog_...` (sent as `Bearer`). |
| `DEVIN_ORG_ID`          | real mode | `org-mock` in mock mode           | Devin organization ID, `org-...`.                       |
| `GITHUB_TOKEN`          | real mode | —                                 | GitHub token for posting the PR-link comment.           |
| `TARGET_REPO`           | real mode | `mock-org/mock-repo` in mock mode | Repo (`owner/name`) to watch.                           |
| `GITHUB_WEBHOOK_SECRET` | no        | — (verification off)              | Enables `X-Hub-Signature-256` HMAC checking.            |
| `DEVIN_TIMEOUT_MINUTES` | no        | `30`                              | When to timeout a stuck Devin session.                  |
| `PORT`                  | no        | `8080`                            | HTTP listen port.                                       |

## HTTP endpoints

| Method | Path              | Purpose                                                    |
|--------|-------------------|------------------------------------------------------------|
| `POST` | `/webhook/github` | Receive a GitHub `issues` event → orchestrate → `202`.     |
| `GET`  | `/status`         | JSON: all tracked sessions, counts by state, avg duration. |
| `GET`  | `/health`         | Liveness check → `ok`.                                     |
| `GET`  | `/`               | The dashboard (static, polls `/status` every 5s).          |

## Architecture

```
GitHub issue labelled "devin-fix"
        │
        ▼ webhook (push)
  WebServer  ───────►  Orchestrator  ──► DevinClient (Http | Mock)
   /webhook/github        │  onIssueEvent / poll
                          ├──► SessionStore 
                          └──► GitHubClient
   /status ◄── StatusView ◄── SessionStore
```

### Design notes / Assumptions

- **Devin lifecycle.** The v3 API splits status into two fields: `status` plus a nullable
  `status_detail` that's only documented for `status=running` and `status=suspended`.
  `DevinStatus.fromRaw` collapses this back down to the five states our state machine actually cares about which is the
  completion of the session (`running` + `finished`). These statuses; `suspended`/`error`/
  `exit` will all fail the session. (`DEVIN_TIMEOUT_MINUTES`) also fails a session that never resolves either way.
- **Completion signal once a PR is open.** We're terminating devin sessions once the raised PR has been merged or
  closed.
- **Persistence** State is in-memory and intentionally not persisted
- **Webhook auth** HMAC verification is optional and gated on `GITHUB_WEBHOOK_SECRET`

## Tests

```bash
./gradlew test
```
