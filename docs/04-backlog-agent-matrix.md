# 04 — Backlog & Matrix Agent/Skill

Swarm diaktifkan sesuai `AGENTS.md` Phase 0: terdeteksi **Android/Kotlin native** → lead `@mobile-crossplatform-architect`, verifier `@kotlin-reviewer` + `@performance-optimizer`. Skill dimuat JIT, maksimum 3 per subtask.

## Skill yang dimuat (dan kapan)

| Skill (`.agents/skills/…`) | Dimuat saat |
|---|---|
| `android-clean-architecture/SKILL.md` | M0 — menetapkan batas modul & aturan dependensi |
| `mobile-android-compose-master/SKILL.md` | M1, M3 — Compose, StateFlow, Room |
| `kotlin-coroutines-flows/SKILL.md` | M1, M2 — `Flow<BuildEvent>`, cancellation, `ProcessBuilder` di `Dispatchers.IO` |
| `kotlin-testing/SKILL.md` | M2 — contract test untuk 3 `BuildEngine` |
| `mobile-offline-first-sync-2026/SKILL.md` | M1, M6 — auto-save, konflik, pemulihan crash |
| `antislop-ui/SKILL.md` + `antislop-layoutmobile/SKILL.md` | M3 — layar onboarding & editor |
| `antislop-human/SKILL.md` (`accessibility`) | M3, M7 — kontras, target sentuh 48dp, screen reader |
| `antislop-copywriting/SKILL.md` | M3, M4 — semua string Indonesia |
| `e2e-testing/SKILL.md` | M2, M7 — E2E build→install |
| `devops-sre-kubernetes-iac/SKILL.md` | M5 — worker Docker, observability |
| `cybersecurity-appsec-redteam-2026/SKILL.md` | M0, M2, M5 — path traversal, consent, isolasi worker |
| `verification-loop/SKILL.md` | setiap akhir milestone (Gate 1) |

## Backlog per milestone

### M0 — Fondasi
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Struktur multi-module + aturan dependensi | `@architect`, `@code-architect` | `android-clean-architecture` | `./gradlew projects` bersih, detekt lolos, tidak ada dependensi `core → feature` |
| CI (build + detekt + ktlint + APK size) | `@devops-sre-architect` | `github-actions-ci-cd` | Workflow hijau di PR pertama |
| Spike eksekusi toolchain dari `assets/` | `@kotlin-build-resolver` | `mobile-android-compose-master` | APK terpasang di 1 device fisik |
| `scripts/prove/build-hello.sh` | `@swe-healer` | — | `apksigner verify` exit 0 |
| Review keamanan awal (path, proses) | `@cybersecurity-redteam-pro` | `cybersecurity-appsec-redteam-2026` | 0 temuan kritis |

### M1 — Editor
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Integrasi Sora Editor + wrapper Compose | `@mobile-crossplatform-architect` | `mobile-android-compose-master` | Ketikan P95 < 16 ms di file 1.000 baris |
| File tree, tab, rename/delete, sort | `@frontend-developer` | `android-clean-architecture` | Semua aksi punya undo & error state |
| Auto-save + pemulihan crash | `@architect` | `mobile-offline-first-sync-2026` | Kill proses → ≤ 1 s ketikan hilang |
| Grammar Java/XML/JSON/Kotlin | `@code-simplifier` | — | Highlight benar pada 4 fixture |
| Macrobenchmark scroll & ketik | `@performance-optimizer` | `kotlin-testing` | 0 frame > 32 ms |

### M2 — Build engine (paling kritis)
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Implementasi 8 langkah Tier 0 | `@kotlin-build-resolver` | `kotlin-coroutines-flows` | `Tier0PipelineTest` menghasilkan APK valid |
| Caching per-file + inkremental | `@performance-optimizer` | — | Build ke-2 < 10 s |
| Cancellation bersih | `@code-reviewer` | `kotlin-coroutines-flows` | Test: batal → 0 proses sisa |
| Parser diagnostik ECJ/d8/aapt2 | `@swe-healer` | — | 15 kasus error → baris tepat |
| Install flow (FileProvider + izin) | `@mobile-crossplatform-architect` | `antislop-human` | APK terpasang dari dalam app di API 24 & 35 |
| Hardening: no shell, path guard, checksum toolchain | `@security-reviewer` | `cybersecurity-appsec-redteam-2026` | Semua temuan high tertutup |
| Contract test 3 engine | `@qa-automation-architect` | `kotlin-testing` | Satu test suite berlaku untuk Tier 0/1/2 |

### M3 — Onboarding & template
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Arah visual & token | `@design-director`, `@design-architect` | `antislop-ui` | `DESIGN.md` terkunci, token OKLCH di `Theme.kt` |
| Wizard 5 langkah | `@frontend-developer` | `antislop-layoutmobile` | 320dp width tanpa overflow, 8 state lengkap |
| 6 template fungsional | `@fullstack-developer` | `mobile-android-compose-master` | Tiap template build sukses & 1 test fungsional |
| Copy Indonesia | `@copy-editor` | `antislop-copywriting` | 0 buzzword, 0 istilah EN yang tak dijelaskan |
| Audit anti-slop + a11y | `@antislop-auditor`, `@a11y-contrast-enforcer` | `antislop-human` | Kontras ≥ 4.5:1, target ≥ 48dp |
| Uji 5 non-programmer | `@planner` | — | ≥ 4/5 sampai APK terpasang |

### M4 — Belajar & diagnosa
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| 25 halaman konsep | `@copy-editor` | `antislop-copywriting` | Tiap halaman ≤ 200 kata + 1 contoh kode |
| Glosarium 60 istilah | `@documentation-engineer` | — | Format EN → ID + analogi |
| 40 pemetaan error | `@swe-healer` | `kotlin-testing` | 40 unit test, semua hijau |

### M5 — Cloud
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| API Ktor + SSE | `@backend-developer` | `kotlin-ktor-patterns` | SSE baris pertama < 1 s |
| Worker Docker (JDK 17 + SDK 35) | `@devops-sre-architect` | `docker-patterns` | Image < 2 GB, warm cache |
| Consent + kuota + retensi artefak | `@cybersecurity-redteam-pro` | `cybersecurity-appsec-redteam-2026` | Uji isolasi & TTL lolos |
| Klien resumable + backoff | `@code-reviewer` | `circuit-breaker-rate-limiter-resilience` | Putus jaringan → build lanjut |

### M6 — Kolaborasi
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Git via JGit + kredensial aman | `@architect` | `jwt-session-security-passkeys` | Token di EncryptedSharedPreferences, tidak di log |
| Snapshot/riwayat lokal | `@database-reviewer` | `mobile-offline-first-sync-2026` | Restore 1 klik, ukuran dibatasi |
| Import/export zip | `@backend-developer` | — | Zip-slip ditolak (test) |

### M7 — Rilis
| Tugas | Agent | Skill | DoD |
|---|---|---|---|
| Baseline profile + cold start | `@performance-optimizer` | — | < 1.5 s di device 2 GB |
| Toolchain per-ABI on-demand | `@devops-sre-architect` | `supply-chain-security-sbom` | Checksum + SBOM terbit |
| Play Store compliance | `@security-reviewer` | `cybersecurity-appsec-redteam-2026` | Deklarasi izin & data safety sesuai |
| Crash-free ≥ 99% | `@qa-automation-architect` | `e2e-testing` | Beta 2 minggu tanpa regresi P0 |

## Gate per milestone (wajib, tidak boleh dilewati)

```bash
./gradlew clean detekt lintDebug
./gradlew :app:assembleDebug
./gradlew :build:tier0:testDebugUnitTest :core:diagnose:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # emulator API 24 & 35, RAM 2 GB
apkanalyzer apk summary app/build/outputs/apk/debug/app-debug.apk   # < 80 MB
```

Gate 2 (sebelum menandai milestone selesai): `@code-reviewer` + `@antislop-auditor` + `@security-reviewer` menandai **setuju** tanpa temuan High/Critical terbuka. Gate 3: setiap bug non-trivial yang diperbaiki menambah satu baris invariant di `MEMORY.md`.
