# Parking Ticket Lot Simulator - Project Status

Last updated: 2026-05-29

## Source Of Truth

Use `Bqko/Parking-Ticket-Lot-Simulator` as the canonical project.

`LKhela/Parking-Lot-Ticket-Simulator` should be treated as a reference/staging repo only. Do not merge it wholesale into the canonical repo. Useful work from LKhela should be manually ported into Bqko through focused branches or pull requests.

The local Bqko working tree now contains the project cleanup and feature work
listed below. Nothing has been pushed to GitHub yet.

Current repo state:

| Repo | Branch | Latest checked commit | Status |
| --- | --- | --- | --- |
| `Bqko/Parking-Ticket-Lot-Simulator` | `main` | `849993a` - Fixed system bugs - 2026-05-22 | Canonical base |
| `LKhela/Parking-Lot-Ticket-Simulator` | `main` | `a655b51` - Add files via upload - 2026-05-29 | Reference/staging only |
| `LKhela/Parking-Lot-Ticket-Simulator` | `master` | `02ef4a3` - PHP prototype - 2026-05-14 | Legacy prototype, not current JavaFX app |

Important: the Bqko and LKhela repos do not share a normal Git history, so alignment cannot be done with a clean merge. Port individual changes intentionally.

## Verification Summary

Checks run locally on 2026-05-29:

| Check | Result | Notes |
| --- | --- | --- |
| Bqko active Java source compile | Pass | JavaFX + SQLite classpath compile succeeded. |
| LKhela active Java source compile | Pass | JavaFX + SQLite classpath compile succeeded. |
| Bqko Maven build | Pass | `mvn test` equivalent run with Maven 3.9.9 and JDK 25. |
| Bqko JUnit suite | Pass | 86 tests passed, 0 failed. |
| Bqko core smoke test | Pass | Entry, duplicate prevention, payment, exit, fee persistence, malformed plate rejection, and lost-ticket spot release worked. |
| LKhela active core smoke test | Pass with caveats | Entry, duplicate prevention, payment, exit, and fee persistence worked. Malformed plate was accepted. Lost-ticket service does not release the in-memory spot by itself. |
| LKhela admin-login contribution compile | Pass | The loose `admin login` files compile against Bqko classes. |
| LKhela admin-login auth smoke | Pass | `admin / admin123` seeded and authenticated. Wrong password rejected. |
| PPTX claims vs code | Aligned locally | Admin login and customer profile work are now implemented in the local Bqko tree. |

## Feature Audit

| Feature | Bqko canonical status | LKhela status | Decision |
| --- | --- | --- | --- |
| JavaFX desktop app shell | Working | Working | Keep Bqko version. |
| Vehicle entry | Working | Working | Keep Bqko. |
| License plate validation | Working in Bqko service | Missing in LKhela active service | Keep Bqko validator. Add UI feedback/tests. |
| Spot assignment | Working | Working | Keep Bqko. |
| Duplicate active vehicle prevention | Working | Working | Keep Bqko. |
| Payment and exit lifecycle | Working | Working | Keep Bqko. |
| Lost ticket flow | Working locally | Partially working | Bqko service now records payment, updates revenue/DB, and releases the spot. |
| Fee calculator | Working, tested | Working, untested | Keep Bqko tested version. |
| Peak/off-peak pricing tiers | Present | Present | Keep Bqko, add direct tests for tier multiplier behavior. |
| Admin pricing panel | Working | Working | Keep Bqko naming and persistence API. |
| Admin login | Working locally | Exists only in top-level `admin login` folder | Ported into Bqko active source. |
| Admin DB table | Working locally | Exists only in loose `admin login/DatabaseManager.java` | Ported schema and default admin seed into Bqko `DatabaseManager`. |
| Customer records table | Present in Bqko DB/repository | Present in LKhela DB design | Keep Bqko. |
| Customer name/phone fields | Working locally | Claimed in PPTX but not in active source | Implemented in Bqko `EntryScreen`. |
| Customer auto-fill by plate | Working locally | Claimed in PPTX but not active source | Implemented in Bqko. |
| Issued ticket shows customer | Working locally | Claimed in PPTX but not active source | Implemented in Bqko. |
| DB persistence and restore | Present | Present | Keep Bqko. Add migration notes and tests. |
| Dashboard metrics | Present | Present | Keep Bqko. Test revenue/history edge cases. |
| CSV export | Present | Present | Keep Bqko. Escape CSV values before relying on it. |
| Text ticket log on close | Present | Present in LKhela as explicit export | Keep Bqko, ignore generated log files in Git. |
| JUnit tests | Present, 86 passing | Missing | Keep and expand Bqko tests. |
| Build system | Working locally | Missing | Maven `pom.xml` added. |

## Useful Contributions

Completed locally:

1. Added Maven build and test configuration.
2. Added GitHub Actions CI for `mvn --batch-mode test`.
3. Ignored runtime DB/log/IDE artifacts and removed them from Git tracking.
4. Ported admin login, admin repository, admins table, and default admin seed.
5. Implemented customer name/phone fields, plate auto-fill, customer save, and issued-ticket customer display.
6. Fixed lost-ticket ownership in `TicketManager`.
7. Added tests for admin auth, customer repository updates, lost-ticket payment, and lost-ticket spot release.

Remaining recommendations:

1. Add DB migration or reset strategy.
   - Current schema changes are `CREATE TABLE IF NOT EXISTS`, but existing DBs may not get new columns/tables safely.
   - Document whether users should delete local DB during development, or add migration logic.

2. Tighten dashboard/export behavior.
   - Escape CSV fields.
   - Test dashboard totals after payment, exit, lost ticket, and restart.

3. Improve admin security.
   - Add logout.
   - Add optional password change.
   - Consider salted password hashing instead of bare SHA-256.
   - Add basic login audit logging.

4. Package the app.
   - Add run scripts or a packaged app target.
   - Include a sample DB/bootstrap flow.

## Team Workflow

Use this process from now on:

1. All production work targets `Bqko/Parking-Ticket-Lot-Simulator`.
2. Contributors branch from Bqko `main`.
3. Branch names should describe the feature, for example `feature/admin-login` or `fix/lost-ticket-release`.
4. No direct folder uploads to GitHub for Java source changes.
5. Every feature change should update this file if project status changes.
6. Every merged feature should have at least one compile/test/smoke verification note.

## Current Presentation Reality Check

`PLTS_Update.pptx` is partly ahead of the code.

The PPTX claims are now implemented in the local Bqko working tree, but they are
not on GitHub until these local changes are reviewed and pushed.
