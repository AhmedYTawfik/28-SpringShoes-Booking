# Grader Run Guide — Spring 2026

This guide covers running the `abuelmagd/scalable-grading-system:spring2026-latest` image for Tasks (2–3) and Milestone 2 (M2). It includes the correct flags for each operating system, an explanation of every option, and instructions for the **run signature files** that you must push to GitHub after every grader run.

---

## What Changed from Earlier Guides

Previous instructions showed the repo mounted **read-only** (`:ro`). That flag is now **removed**. The grader writes an encrypted session file into your repo after each run to record that the run happened and what score it produced. That write fails with `:ro` mounted.

```
# OLD — breaks session write
-v "$(pwd)":/repo:ro

# CORRECT — drop the :ro
-v "$(pwd)":/repo
```

A `--tmpfs` flag is also required. The grader decrypts its payload into a RAM filesystem at startup; without it the grader cannot run at all.

```
--tmpfs /grader-ram:exec,size=256m
```

Nothing else about your source code changes; the grader only ever writes inside `.grader-cache/sessions/`.

---

## Prerequisites

- Docker Desktop installed and running
- Terminal open at the **root of your repository** (the folder containing `.git/`)
- **Windows:** use PowerShell, not cmd.exe
- **M2 only:** `team.json` present at repo root (see the [M2 section](#running-milestone-2-m2) below)

---

## Step 1 — Pull the Image (Once)

```bash
docker pull abuelmagd/scalable-grading-system:spring2026-latest
```

---

## Running — Tasks 2, and 3

Set `TASK=2`, or `TASK=3` as needed.

### Linux

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --tmpfs /grader-ram:exec,size=1024m \
  -v "$(pwd)":/repo \
  -e REPO_PATH=/repo \
  -e TASK=2 \
  abuelmagd/scalable-grading-system:spring2026-latest
```

### macOS

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --tmpfs /grader-ram:exec,size=1024m \
  -v "$(pwd)":/repo \
  -e REPO_PATH=/repo \
  -e TASK=2 \
  abuelmagd/scalable-grading-system:spring2026-latest
```

### Windows (PowerShell)

```powershell
docker run --rm `
  -v //var/run/docker.sock:/var/run/docker.sock `
  --tmpfs /grader-ram:exec,size=1024m `
  -v "${PWD}:/repo" `
  -e REPO_PATH=/repo `
  -e TASK=2 `
  abuelmagd/scalable-grading-system:spring2026-latest
```

---

## Running — Milestone 2 (M2)

M2 has three requirements that Tasks do not:

1. Use `PROJECT=M2` instead of `TASK`.
2. Set `THEME=<YourThemeName>` (see valid names below).
3. **Linux only:** add `--add-host=host.docker.internal:host-gateway`. macOS and Windows have this alias built-in and do not need it.

### Linux

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  --tmpfs /grader-ram:exec,size=1024m \
  -v "$(pwd)":/repo \
  -e REPO_PATH=/repo \
  -e PROJECT=M2 \
  -e THEME=Talabat \
  abuelmagd/scalable-grading-system:spring2026-latest
```

### macOS

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --tmpfs /grader-ram:exec,size=1024m \
  -v "$(pwd)":/repo \
  -e REPO_PATH=/repo \
  -e PROJECT=M2 \
  -e THEME=Talabat \
  abuelmagd/scalable-grading-system:spring2026-latest
```

### Windows (PowerShell)

```powershell
docker run --rm `
  -v //var/run/docker.sock:/var/run/docker.sock `
  --tmpfs /grader-ram:exec,size=1024m `
  -v "${PWD}:/repo" `
  -e REPO_PATH=/repo `
  -e PROJECT=M2 `
  -e THEME=Talabat `
  abuelmagd/scalable-grading-system:spring2026-latest
```

Replace `Talabat` with your actual theme name.

**Valid theme names:** `Amazon` · `Booking` · `EventTicketing` · `FinanceTracker` · `FreelanceMarketplace` · `Talabat` · `TripPlanning` · `Uber`

---

## M2 Pack Mode — Run the Tests Standalone (No Grader)

If you want to run the M2 tests directly against your own running stack — without the grader orchestrating anything — set `-e M2_PACK=1` and the image will write a standalone Maven test project as a ZIP into your repo. Unzip it on your machine, set the env vars in the bundled `README.md`, and `mvn test`.

### When to use it

- You want to debug a single test class without re-running the full grader pipeline.
- You want to iterate on your code with `mvn test -Dtest='TC42_*'` against a stack you brought up yourself.
- You want a snapshot of your project's discovered structure (`manifest.json`) for inspection.

### What's in the ZIP

- `src/test/java/com/testgen/<theme>/` — `PublicTests.java`, `DP_PatternTests.java`, `TestBase.java`, `TestAuthHelper.java`
- `src/test/resources/manifest.json` — your repo's discovered entities/controllers/enums (regenerable)
- `src/test/resources/theme.json` — your theme's spec constants
- `pom.xml` — Spring Boot 4.0.3 BOM, JDK 25, all DB-driver test deps
- `scanner/scanner.jar` + `scanner/regen-manifest.sh` — re-scan your repo after refactoring
- `README.md` — copy-pasteable env block + `mvn test` recipes

**Private tests are NEVER included.** The pack only emits Tier-0 public test files regardless of any flag you set.

### Run

```bash
# Linux / macOS
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --tmpfs /grader-ram:exec,size=1024m \
  -v "$(pwd)":/repo \
  -e REPO_PATH=/repo \
  -e PROJECT=M2 \
  -e THEME=Talabat \
  -e M2_PACK=1 \
  abuelmagd/scalable-grading-system:spring2026-latest
```

Output lands at `m2-tests-<your-repo-folder-name>.zip` inside your repo. Override the destination with `-e M2_PACK_OUTPUT=/repo/somewhere-else.zip`.

### Notes

- Pack mode skips grading entirely — no docker compose up, no test runs, no scoring. It's a one-shot bundle build.
- The bundled `manifest.json` reflects your repo at the moment you ran pack mode. Re-bundle (or re-run `bash scanner/regen-manifest.sh`) after renaming entities.
- Pack mode works without `--add-host=host.docker.internal:host-gateway` because nothing needs to reach your services from inside the container — the grader doesn't bring up your stack.

---

## DEBUG Mode

Add `-e DEBUG=1` to any run command to get full failure messages, exact assertion errors, stack traces, and an explanation of what the test expected vs. what it received.

```bash
# Linux/macOS — add this line to the run command
  -e DEBUG=1 \
```

```powershell
# Windows PowerShell — add this line to the run command
  -e DEBUG=1 `
```

Without `DEBUG=1` the output shows a condensed one-liner per failure, which is cleaner for a quick pass/fail check.

---

## Flag Reference

### Core flags (student use)

| Flag                                           | Required       | Effect                                                                                |
| ---------------------------------------------- | -------------- | ------------------------------------------------------------------------------------- |
| `-v /var/run/docker.sock:/var/run/docker.sock` | All            | Lets the grader talk to Docker (sibling containers)                                   |
| `--tmpfs /grader-ram:exec,size=1024m`          | All            | RAM filesystem where the grader writes its files                                      |
| `-v "$(pwd)":/repo`                            | All            | Mounts your repo **without** `:ro` — needed for session write                         |
| `-e REPO_PATH=/repo`                           | All            | Tells the grader where the repo is inside the container                               |
| `-e TASK=N`                                    | Tasks 2–3      | Selects the task grader (2 or 3)                                                      |
| `-e PROJECT=M2`                                | M2             | Selects the Milestone 2 grader                                                        |
| `-e THEME=<name>`                              | M2             | Identifies your project theme                                                         |
| `--add-host=host.docker.internal:host-gateway` | M2, Linux only | Routes `host.docker.internal` to the Linux host so the grader can reach your services |
| `-e DEBUG=1`                                   | Optional       | Full failure diagnostics with stack traces and assertion details                      |
| `-e M2_PACK=1`                                 | Optional (M2)  | Skip grading; emit a standalone Maven test pack ZIP into your repo (see Pack Mode section) |
| `-e M2_PACK_OUTPUT=<path>`                     | Optional (M2)  | Override pack output path (default: `/repo/m2-tests-<TeamFolderName>.zip`)             |

### Resource limit flags (instructor / advanced use only)

These flags cap how much CPU and memory each student service container is allowed to consume. They are **not required for self-testing** — omitting them leaves the containers uncapped, which is the right default when you are grading your own machine. They exist for batch grading scenarios where the instructor grades many repos in parallel and needs to prevent any single student's code from starving the host.

| Flag                          | Example value | Effect                                                         |
| ----------------------------- | ------------- | -------------------------------------------------------------- |
| `-e GRADER_MEMORY=<value>`    | `2g`, `4g`    | Sets `--memory` + `--memory-swap` on each student container    |
| `-e GRADER_MEMORY_SWAP=<val>` | `4g`          | Overrides swap cap independently (defaults to `GRADER_MEMORY`) |
| `-e GRADER_CPUS=<value>`      | `2`, `1.5`    | Sets `--cpus` on each student container                        |
| `-e GRADER_PIDS_LIMIT=<val>`  | `512`         | Sets `--pids-limit` on each student container                  |

> **Note for M2:** these flags control M1 student service containers. For M2-specific resource and timeout overrides, see the **M2 Tunable Overrides** section below.

---

## M2 Tunable Overrides

The M2 grader injects opinionated defaults for Cassandra hardening, healthcheck timing, request timeouts, and the Maven pre-build. Those defaults work for the vast majority of stacks, but if your stack hits a corner case (slow disk, low-RAM machine, custom Cassandra schema, etc.) every default below can be overridden via `-e` flags. Skip flags also exist for stacks where the grader's overrides themselves cause the problem.

### Memory & heap (Cassandra)

| Flag                          | Default  | What it sets                                  |
| ----------------------------- | -------- | --------------------------------------------- |
| `-e M2_CASSANDRA_MEMORY_LIMIT=<size>` | `1500m` | Container `deploy.resources.limits.memory` |
| `-e M2_CASSANDRA_HEAP=<size>`         | `1024M` | JVM `MAX_HEAP_SIZE`                       |
| `-e M2_CASSANDRA_NEW_HEAP=<size>`     | `200M`  | JVM `HEAP_NEWSIZE`                        |

> **Don't shrink Cassandra below the defaults** unless you're seeing OOM at startup AND you accept that GC pauses may exceed `M2_CASSANDRA_REQUEST_TIMEOUT`. Going below 768M heap regularly causes `DriverTimeoutException after PT30S` even on small workloads.

### Timeouts

| Flag                                  | Default | What it sets                                                               |
| ------------------------------------- | ------- | -------------------------------------------------------------------------- |
| `-e M2_DB_WAIT_SECONDS=<sec>`         | `480`   | How long phase-1 waits for all DB containers to become healthy             |
| `-e M2_MVN_TIMEOUT_SECONDS=<sec>`     | `600`   | Cap on `mvn clean package -DskipTests` pre-build                           |
| `-e M2_CASSANDRA_REQUEST_TIMEOUT=<dur>` | `30s` | Spring Data Cassandra request timeout (must stay >= JVM write_request_timeout) |

### Healthcheck timing — Cassandra

| Flag                                                | Default |
| --------------------------------------------------- | ------- |
| `-e M2_CASSANDRA_HEALTHCHECK_INTERVAL=<dur>`        | `10s`   |
| `-e M2_CASSANDRA_HEALTHCHECK_TIMEOUT=<dur>`         | `5s`    |
| `-e M2_CASSANDRA_HEALTHCHECK_RETRIES=<int>`         | `40`    |
| `-e M2_CASSANDRA_HEALTHCHECK_START_PERIOD=<dur>`    | `60s`   |

Default window: `60s + 40 * 10s = 460s`. Bump `RETRIES` first if Cassandra needs longer; bump `START_PERIOD` only if your machine is slow enough that the JVM hasn't started GC by 60s.

### Healthcheck timing — backend services

| Flag                                                | Default |
| --------------------------------------------------- | ------- |
| `-e M2_BACKEND_HEALTHCHECK_INTERVAL=<dur>`          | `10s`   |
| `-e M2_BACKEND_HEALTHCHECK_TIMEOUT=<dur>`           | `5s`    |
| `-e M2_BACKEND_HEALTHCHECK_RETRIES=<int>`           | `30`    |
| `-e M2_BACKEND_HEALTHCHECK_START_PERIOD=<dur>`      | `60s`   |

Default window: `60s + 30 * 10s = 360s`. Bump `RETRIES` if your Spring Boot apps have slow ES/Mongo connect cycles.

### Skip / escape-hatch flags

These are **last-resort** overrides for when the grader's own injection breaks your stack. Read what each one disables before flipping it.

#### `-e M2_NO_COMPOSE_OVERRIDE=1` — disable ALL grader overrides

Skips the entire compose override file. Your `docker-compose.yaml` runs verbatim. **What this disables:**

- `JWT_SECRET` / `JWT_EXPIRATION` injection — cross-service auth tests will fail unless every service shares a key your code wired up
- `SPRING_CASSANDRA_REQUEST_TIMEOUT=30s` injection — you must add `spring.cassandra.request-timeout=30s` to your Cassandra-using service's `application.properties`, or the 5s default will trip the `schema-action` step
- All Cassandra hardening (image pin, memory cap, JVM_OPTS, tmpfs, ulimits, healthcheck) — your declared image and config are used as-is
- Backend healthcheck override (interval / retries / start_period) — your declared values stand
- `env_file: !override` stub — a missing student `.env` file becomes fatal
- Per-team `container_name` namespacing — concurrent grader runs against the same daemon may collide on container names
- Host-port offset (`M2_HOST_PORT_OFFSET` becomes a no-op)

When to use: rare. Your stack must already declare every hardening knob the grader normally injects.

#### `-e M2_NO_CASSANDRA_OVERRIDE=1` — disable ONLY Cassandra hardening

Backend overrides, JWT, request-timeout injection, and other DB overrides still apply. **What this disables:**

- `image: cassandra:5.0` pin — your declared image tag is used (be sure it's multi-arch and CQL-v4-compatible)
- `tmpfs` mounts for `/var/lib/cassandra/{data,commitlog,saved_caches,hints}`
- `ulimits` (`nofile`/`nproc`/`memlock`)
- `deploy.resources.limits.memory` cap
- `environment: !override` block (`MAX_HEAP_SIZE`, `HEAP_NEWSIZE`, `CASSANDRA_NUM_TOKENS`, `SimpleSnitch`, `DC`/`RACK`, `AUTH=allow`)
- `JVM_OPTS` (gossip-skip, ring_delay_ms, write_request_timeout_in_ms, memtable + cache caps, `-Xss`, `MaxDirectMemorySize`, etc.)
- `volumes: !override []` — any disk-backed volume you declared is kept
- `depends_on: !override []` — your `depends_on` is kept
- Cassandra healthcheck override — your declared healthcheck stands (often `cqlsh`, which can time out before Cassandra is responsive)

When to use: you've tuned your own Cassandra configuration and the grader's injection conflicts with it.

#### `-e M2_NO_SEQUENCE_ALIGN=1` — disable PG sequence alignment

After your apps are healthy, the grader runs a `setval(seq, MAX(id))` on every PG SERIAL/IDENTITY column so that JPA's later `nextval()` doesn't return an id you already inserted. This fixes the very common `seedAdmin: POST /api/auth/register returned 500 — duplicate key value violates unique constraint "users_pkey", Detail: Key (id)=(1) already exists` failure that hits stacks with `data.sql` / `import.sql` / `CommandLineRunner` seeders.

When to use: rare. Only flip this if your stack manages sequences in a way the alignment somehow breaks (e.g. you intentionally pre-populate IDs higher than your sequence to reserve a range).

### Other overrides (already in prior versions)

| Flag                              | Default | Effect                                                        |
| --------------------------------- | ------- | ------------------------------------------------------------- |
| `-e M2_HOST_PORT_OFFSET=<int>`    | `0`     | Adds offset to every published host port (avoid collisions)   |
| `-e M2_KEEP_DATA=1`               | off     | Don't tear down DB volumes after the run (post-mortem)        |
| `-e M2_SECURITY_DEBUG=1`          | off     | Inject Spring Security DEBUG logging into backend services    |

---

## Run Signature Files

### What They Are

Every time the grader finishes — whether your code passes, fails, or errors — it writes an encrypted binary file to your repository at:

```
.grader-cache/sessions/<student_id>_<timestamp>_<random>.bin
```

For example:

```
.grader-cache/sessions/49-12345_2026-05-04T14-22-11Z_a3f7c1b2.bin
```

This file is your **run signature**. It records information about this run and proves your contribution towards the project

The instructor **cannot** verify your run without this file. Runs without a pushed signature may not count.

### How to Push Signature Files After Each Run

```bash
git add .grader-cache/
git commit -m "chore: add grader session signature"
git push
```

You can combine several runs into a single commit:

```bash
# Run grader once — .grader-cache/sessions/ gets a new file
# Run grader again — another file appears
git add .grader-cache/
git commit -m "chore: add grader session signatures"
git push
```

### Verify the Files Are Tracked

```bash
git status .grader-cache/
```

If you see `Untracked files: .grader-cache/`, the directory is not yet tracked. Run `git add .grader-cache/` then commit.

### Make Sure .grader-cache/ Is Not in .gitignore

Check your `.gitignore`. If it contains any of the following, **remove that line**:

```
.grader-cache
.grader-cache/
*.bin
```

The signature files are binary (`.bin`) and must be committed as-is. Do not add them to `.gitignore`.

### Why You Must Not Use :ro

The `:ro` (read-only) mount flag blocks the session write. The grader prints a warning and skips writing the file:

```
WARN: session audit-file write failed ... Common cause: -v "$(pwd)":/repo:ro —
drop the :ro so the grader can write the audit file under .grader-cache/sessions/
```

Your code still will be tested by the grader, but **no signature file is created**, so the instructor cannot verify the run. Always use `-v "$(pwd)":/repo` without `:ro`.

---

## M2: team.json

The M2 grader detects your theme in this order:

1. `-e THEME=<name>` environment variable (highest priority — always set this)
2. `theme` key in `team.json` at the repo root
3. Repository directory name pattern

**Always set `THEME=` explicitly.** If you rely on `team.json`, make sure it is committed and pushed. A missing or malformed `team.json` causes theme detection to fall through to directory name matching, which is unreliable.

Minimal `team.json`:

```json
{
  "theme": "Talabat",
  "team": [
    { "name": "Your Name", "id": "49-XXXXX" }
  ]
}
```

---

## Troubleshooting

**Cannot connect to the Docker daemon**
Docker Desktop is not running. Start it, wait 30 seconds, try again.

**bootstrap: /grader-ram is not a tmpfs-with-exec mount**
You are missing `--tmpfs /grader-ram:exec,size=256m`. Add it to your `docker run` command — it is required for the grader to start.

**WARN: session audit-file write failed**
You are using `:ro`. Remove it from the `-v` flag: use `-v "$(pwd)":/repo` instead of `-v "$(pwd)":/repo:ro`.

**M2: Stack health-check timed out**
Your `docker compose up` failed or a service is not healthy. Run `docker compose logs` to see why, fix it, then re-run the grader.

**M2: `DriverTimeoutException: Query timed out after PT5S` (Cassandra)**
A Cassandra-using service (typically `shipping-service`) couldn't finish its `CREATE TABLE` statements within the default 5-second timeout. Add this to that service's `application.properties`:

```properties
spring.cassandra.request-timeout=30s
```

Or set `spring.cassandra.schema-action=NONE` and create your tables yourself via a `schema.cql` file. Recent grader versions inject this timeout automatically — if you're on an older image, run `docker pull abuelmagd/scalable-grading-system:spring2026-latest` to update.

**M2: mvn pre-build failed / stale JAR**
Run `mvn clean package -DskipTests` in your repo root before running the grader.

**M2: host.docker.internal — Name or service not known (Linux)**
You are missing `--add-host=host.docker.internal:host-gateway`. macOS and Windows do not need it.

**M2: Could not detect theme**
Set `-e THEME=<YourThemeName>` explicitly. Check the spelling matches one of the eight valid theme names exactly (case-sensitive).

**"image platform does not match" (Apple Silicon)**
Pull the latest multi-arch image:

```bash
docker pull abuelmagd/scalable-grading-system:spring2026-latest
```

**mounts denied / path is not shared (macOS/Windows)**
Open Docker Desktop → Settings → Resources → File Sharing → add the folder containing your repository.
