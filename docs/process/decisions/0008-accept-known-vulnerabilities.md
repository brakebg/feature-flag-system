# 0008 — Accept known vulnerabilities in Trivy gate 10

Status: **Accepted**
Decided by: owner (@brakebg), 2026-10-10
Changes: spec 11.3, gate 10

## 1. Decision

Six vulnerabilities are known. The owner accepts the risk. They are listed in the spec's
"Accepted vulnerabilities" table (11.3) and in `.trivyignore`, so gate 10 and CI do not
report them. Review by 2027-04-10.

| CVE | Severity | Where |
| --- | --- | --- |
| CVE-2026-47884 | CRITICAL | not recorded |
| CVE-2026-47890 | CRITICAL | `spring-webmvc` 6.2.19, backend. Fixed in 7.0.9 only |
| CVE-2026-78667 | HIGH | Go stdlib 1.26.7 in `/usr/bin/pebble`, backend image |
| CVE-2026-78669 | HIGH | Go stdlib 1.26.7 in `/usr/bin/pebble`, backend image |
| CVE-2026-97031 | HIGH | Go stdlib 1.26.7 in `/usr/bin/pebble`, backend image |
| CVE-2026-4775 | HIGH | `tiff` 4.7.1-r0 in the UI image (alpine 3.23.3) |

## 2. Why

Known issues, risk accepted by the owner.
(Owner: add the reason for each group here. For example: is `pebble` used at runtime?
Is `spring-webmvc` 7 a planned upgrade?)

## 3. Consequences

- Gate 10 has two exceptions: no fixed version (agent, `docs/DECISIONS.md`) and owner
  accepted (spec 11.3 table). Any other `.trivyignore` entry fails the gate.
- The agent adds the six entries to `.trivyignore` in the format from spec 11.3 and never
  removes them.
- A `.trivyignore` entry matches the CVE id in every scan target, not only the package
  listed above. The same CVE in another package stays hidden too.
- Five of the six have a fixed version: the three Go stdlib ones in `pebble`, `tiff`, and
  `spring-webmvc` (new major version only). When the base image or dependency is updated,
  the owner can remove those entries early.
- To do: add a check to `scripts/owner-review.sh` that every `.trivyignore` entry is
  either in the spec table or in `docs/DECISIONS.md`. Not done yet. Until then the owner
  reads `.trivyignore` during the review.
- When the review date passes, Trivy reports the CVEs again. The owner renews or removes
  the entries.
