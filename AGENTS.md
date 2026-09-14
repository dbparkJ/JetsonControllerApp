# JetsonControllerApp

Android Kotlin/Jetpack Compose lives in `app/`; Python Jetson services in `backend/`; the upload server in `upload_receiver/`.

## Task scope and context

- Start with the affected code and tests. Read documentation only when it resolves a question for the current task; reuse unchanged material already read in this session.
- Use `README.md` for environment setup, `docs/BACKEND.md` for Jetson deployment/API operation, `docs/WIFI_DIRECT.md` for P2P, `docs/UPLOAD_SERVER.md` for receiver behavior, and `docs/EXTERNAL_PIPELINE_CONTRACT.md` for external pipeline integration. These are entry points, not a reading checklist.
- For Compose styling, use existing components and `docs/design/STYLE_GUIDE.md`; color values live in `docs/design/colors.json` and `app/src/main/java/com/example/jetsoncontroller/ui/theme/Color.kt`.
- Reports (`*_REPORT.md`) and dated investigations record past work. Consult them for relevant history; their old approvals, task lists, and missing-device notes are not standing instructions or current authorization. Keep integration-contract requirements scoped to that integration.
- Select skills for the requested workflow, not incidental words such as UI, design, server, or agent. An ordinary Compose/Python edit does not itself require an external design, Cloudflare, or OpenAI documentation workflow. For an applicable skill, load only the route and supporting references needed for the task; honor explicit skill requests and required tool prerequisites.

## Completion and authorization

- A requested local fix includes implementation, relevant verification, and correction of failures introduced by that fix. Continue through these steps without asking for approval again; resolve routine, reversible choices from existing code and user intent.
- Preserve unrelated working-tree changes. Ask only when a missing decision materially blocks the work, or an action exceeds the user's authorization. Device deployment, service restarts, sensor operation, and data deletion need a known target and applicable authorization; reuse authorization already given for that scope.
- Report the result, relevant checks, and actual blockers briefly. Distinguish local verification from device testing and deployment; do not create a separate audit report or execution log unless requested.

## Verification proportional to the change

- Documentation/instruction-only edits: check the diff and referenced paths; no Android build or hardware tests.
- Android changes: select relevant JVM tests with `./gradlew :app:testDebugUnitTest --tests '*RelevantTest' --max-workers=1 --console=plain`. Add `:app:assembleDebug` and/or `:app:lintDebug` when compilation, resources, or Android checks are affected. For color-token/contrast changes, use `python3 scripts/check_slate_harmony.py`.
- Python changes: use the existing environment and affected `unittest` modules. From `backend/`, for example, `.venv/bin/python -m unittest tests.test_api`; use the corresponding environment under `upload_receiver/` for receiver tests. See README only if setup is needed.
- Choose broader regression or connected-device tests for cross-cutting changes or behavior that targeted tests cannot establish. Required CI checks remain in `.github/workflows/reliability.yml`.
- After relevant checks pass, finish. Repeat or broaden checks only for new changes, failures, unresolved risks, or an explicit request. If a tool/device is unavailable, report the gap and continue independent work rather than retrying unchanged prerequisites.
