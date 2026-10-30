# AGENTS.md — mandatory instructions for coding agents

This repository is worked on by multiple AI agents and humans. Follow these rules exactly.

## 1. Read before you write

1. Read **`INIT.md`** — it is the authoritative project memory (architecture, ADRs, status,
   database state, API compatibility, change history).
2. Read **`README.md`**, **`docs/ARCHITECTURE.md`**, **`docs/DEVELOPMENT_STATUS.md`** and
   **`docs/DECISIONS.md`**.
3. Inspect `build.gradle`, `src/main/java/**`, `src/test/java/**`, `src/main/resources/**`,
   `src/main/resources/db/migration/**` and `.github/workflows/**` before changing anything.
4. Confirm your planned change does not conflict with an existing ADR. If it must, add a new ADR
   to `INIT.md` § D and `docs/DECISIONS.md` explaining why.

Do not ask the user for information that is already documented in `INIT.md`.

## 2. Never build locally

- Do **not** run `./gradlew build`, `assemble`, `compileJava`, `test`, `check`, `run*`,
  `gradle build`, or any equivalent (including through an IDE or another agent).
- Do **not** install a JDK/Gradle to work around this rule.
- Static inspection, reading class files and reading documentation are allowed and expected.
- GitHub Actions (`.github/workflows/ci.yml`) compiles, tests and packages.
- **Never** state or imply that the project compiles or that tests pass without a link to an
  actual successful Actions run. Do not add `continue-on-error` to hide a failure.

## 3. Implementation rules

- No fake implementations, placeholder classes, always-passing tests, empty catch blocks or
  silent fallbacks to vanilla behaviour.
- Verify every Bukkit/Paper API call against the target API version before using it; record new
  verified calls and new limitations in `docs/API_COMPATIBILITY.md`.
- YAML is authoritative for gameplay values. SQL stores operational state only — never duplicate
  a YAML value into SQL.
- Never run JDBC on the server thread. Capture immutable data first, then use
  `AsyncDatabaseExecutor`, then return to the main thread before touching Bukkit objects.
- Keep the transaction boundary in mind: registration, trade updates, migration and audit writes
  are transactional, and unique constraints are the final integrity layer.
- Never edit a released migration. Add `V005__something.sql` (SQLite **and** MySQL folders).
- Keep public Javadoc on every public type and method (Checkstyle enforces structure, reviewers
  enforce usefulness).

## 4. Tests

- Add or update tests with every behavioural change.
- Categories: pure unit tests (default), database tests (temporary SQLite file), MockBukkit
  integration tests, and statistical tests tagged `@Tag("statistical")` (1,000,000+ iterations).
- Statistical tests must use a seeded `Random` and a chi-square (or equivalent) bound that does
  not fail on ordinary variation.
- Do not weaken an assertion to make a failing test pass; fix the cause or document the gap.

## 5. Documentation debt is a bug

Before you finish a session, update:

- `INIT.md` — status table, current state, test table, change history, new ADRs.
- `docs/DEVELOPMENT_STATUS.md` — what is implemented, what is verified, what is outstanding.
- `docs/CHANGELOG.md` — user-visible changes.
- `README.md` / `docs/CONFIGURATION.md` — if a command, permission or setting changed.

Record explicitly that compilation was **not** run locally.
