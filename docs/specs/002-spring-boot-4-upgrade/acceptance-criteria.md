# Acceptance criteria — spec 002

Owner-authored, locked. IDs for gate 14 (traceability). Verbatim from `SPEC.md` section 6.

Due milestone: 9

- [ ] **AC-UPG-1** · The running backend reports Spring Boot 4.1.x, Spring Framework 7.0.9 or later, Spring Security 7.1.x and Jackson 3.x, read at runtime from the libraries (not from build files).
- [ ] **AC-UPG-2** · No backend class imports Jackson 2 `com.fasterxml.jackson.core` or `com.fasterxml.jackson.databind` (ArchUnit, gate 3).
