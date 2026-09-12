# Titanium Fold — plan

A Chromium-based Titanium fork for the **Samsung Galaxy Z Fold 8** inner display. Goal: in **portrait**, put a **thin vertical tab rail on the right**, in the same column as the selfie-camera punch-hole, so page content is not drawn under the hole and the old top tab strip no longer steals vertical space.

This document is the working plan: product intent, how the overlay repo works, what is already patched, how we will build, and what is still blocked.

**Git: stay on `main`.** This is a personal fork. Do all work on `main` — no feature branches, no `cursor/*` branches, no split PRs unless that policy changes. Commit on `main`, push `main` when asked. If a branch is created by tooling, merge it into `main` immediately and delete it.

---

## 1. Problem

On the Fold 8 inner screen in portrait, stock Chromium-style tablet UI looks like this:

- A **horizontal tab strip** across the top (plus a tablet toolbar: back / forward / omnibox).
- The **punch-hole camera sits mid-right**, in the content column, not in a corner and not in the status bar.
- Web pages, NTP tiles, and video run into the hole. The top strip does nothing about that, and it costs a full row of height.

Target layout in portrait:

```
|  page content (no hole)  |  rail ~52dp  |
                           |  tabs        |
                           |  [ empty ]   |  ← camera, ~32dp gap
                           |  more tabs   |
                           |  +           |
```

In **landscape**, keep Chromium’s normal **horizontal** tablet tab strip.

The cover screen stays phone UI. Vertical tabs are a large-form-factor feature (`>= 600dp`). The inner display qualifies; the cover does not.

---

## 2. Device notes (Fold 8)

- Inner display is ~4:3 and is meant to be used in portrait.
- Selfie camera is a punch-hole on the **middle of the right edge** in portrait (not a corner hole like Pixel Fold).
- Measured hole diameter with CPU-X: **0.52 cm (~5.2 mm)**. At inner-display density that is roughly **25–32 dp** — about one collapsed tab.
- **Do not hard-code 0.52 cm, 76 px, or a Fold 8 model check.** Rotation, cover vs inner, and density change the numbers. Use `WindowInsets.getDisplayCutout()` / `DisplayCutout.getBoundingRects()`.

Two different “skips”:

1. **Skip for the page (important).** If the right rail is at least as wide as Android’s `safeInsetRight`, web content never enters the punch-hole column. The hole sits in chrome.
2. **Skip inside the rail.** A full-height rail still draws *through* the circle. Favicon / close / new-tab can land on the camera. Insert a spacer in the tab list at the cutout’s Y so items flow around it. Dark rail background under the hole is fine.

Do **not**:

- Letterbox the whole window (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER`) — that wastes a band of screen instead of parking chrome in it.
- Make the rail only 0.52 cm wide — too thin to tap.
- Expand the rail to 240 dp in portrait — that steals too much of a 4:3 panel.

---

## 3. What this repository is

This is **not** a Chromium tree. It is a **build overlay** on Vanadium / Chromium:

| Path | Role |
| --- | --- |
| `args.gn` | GN args. Already sets `is_desktop_android = true` (tablet / desktop-Android UI, including a horizontal tab strip on large screens). Package is still `io.github.jqssun.helium`. |
| `build.sh` | Checks out Chromium at the Vanadium tag, applies Vanadium patches, runs `patch.sh`, compiles `chrome_public_apk`. |
| `patch.sh` | `sed` / copy overrides on the Chromium tree (extensions, flags, Fold vertical tabs). |
| `vanadium/` | GrapheneOS Vanadium submodule (patches + version). |
| `patches/fold/` | Fold-specific Java copied into Chromium at patch time. |
| `.github/workflows/build.yml` | CI. Upstream uses a **self-hosted** Linux runner; GitHub-hosted runners are too small. |

Tab UI lives in Chromium, applied at checkout. We do not vendor 30M lines of C++ here. History lives on `main` only (see the Git note at the top).

Tracked Chromium at time of writing: **152.0.7977.x** (Vanadium tags exist through **153.0.8010.x**). Android vertical tabs landed in Chromium in 2026 (`android-vertical-tabs`, expiry milestone 160). The flag, `VerticalTabsSideUiCoordinator`, and collapsed rail **exist in 152**.

`is_desktop_android = true` is why Titanium already looked like the Kiwi / tablet screenshot (top strip + tablet toolbar) on the inner display.

---

## 4. Upstream Chromium vertical tabs (do not reinvent)

Chromium’s **Android** vertical tabs (not the desktop Views tab strip) are a Side UI:

| Piece | Upstream behavior (152) |
| --- | --- |
| Flag | `android-vertical-tabs` / `kAndroidVerticalTabs`, **off** by default |
| Eligibility | Flag on **and** `DeviceFormFactor.isNonMultiDisplayContextOnTablet` (inner Fold qualifies) |
| Default dock | **Left** (`AnchorSide.LEFT`) |
| Collapsed / expanded | **76 dp** / **240 dp** (or 33% of window) |
| Horizontal strip | Hidden while vertical tabs are on |
| Toggle | App menu / tab-strip context menu |
| Preference | `VERTICAL_TABS_ENABLED`, `VERTICAL_TABS_COLLAPSED` in shared prefs |

Key files (in the Chromium tree after checkout):

- `chrome/browser/ui/vertical_tabs/.../VerticalTabUtils.java` — eligibility, widths, prefs
- `.../vertical_tabs/VerticalTabsSideUiCoordinator.java` — Side UI container, `AnchorSide`
- `.../vertical_tabs/VerticalTabListCoordinator.java` — list, decorations
- `chrome/browser/ui/side_ui/.../SideUiCoordinator.java` — `AnchorSide.LEFT` / `RIGHT`
- `chrome/android/.../tabbed_mode/TabbedRootUiCoordinator.java` — wires VT, suppresses the top strip
- Display cutout path: `DisplayCutoutController`, `InsetObserver`, CSS `safe-area-inset-*`

Upstream VT is a **sticky user toggle**, not orientation-driven, and it does not park chrome in a punch-hole. That is what this fork adds.

A Milestone 0 test already confirmed the experimental flag works on the Fold 8 inner screen (left-docked, too thick). The inner screen **is** treated as tablet-sized. That de-risked eligibility.

---

## 5. Product decisions (v1)

1. **Ride Chromium VT.** No custom tab manager.
2. **Portrait:** vertical tabs on, collapsed, right-docked, ~52 dp.
3. **Landscape:** horizontal tablet strip (VT reports disabled).
4. **Cover screen:** unchanged phone UI.
5. **Cutout:** runtime `DisplayCutout` gap in the list; no device fingerprints.
6. **arm64-only APK** for Fold test builds (`BUILD_32BIT=1` restores 32-bit).
7. **Do not change application id** in v1 (`io.github.jqssun.helium`). A debug-signed APK cannot update over Play / GitHub Titanium; uninstall once before sideloading.

---

## 6. Overlay patches already written (not built yet)

These apply when `build.sh` → `patch.sh` runs on a Chromium checkout. They are **not** in the Chromium tree until then. Nothing is committed unless asked.

### 6.1 Enable vertical tabs without `chrome://flags`

In `patch.sh`:

- `feature_overrides.EnableFeature(chrome::android::kAndroidVerticalTabs)` next to the other desktop-Android overrides.
- `BASE_FEATURE(kAndroidVerticalTabs, ENABLED)`.
- CachedFlag default and `enable_by_default` param set **true**.
- `android-vertical-tabs` added to the unexpire-flags loop.

### 6.2 Right, thin, collapsed

| Change | Where |
| --- | --- |
| `AnchorSide.LEFT` → `RIGHT` | `VerticalTabsSideUiCoordinator` |
| Collapsed width **76 → 52 dp** | `VerticalTabUtils.SIDE_UI_CONTAINER_COLLAPSED_WIDTH_DP` |
| Default collapsed **true** | `VERTICAL_TABS_COLLAPSED` pref default |
| Rail horizontal margin 12 → 4 dp | `dimens.xml` |
| Header collapsed end margin 8 → 2 dp | `dimens.xml` |
| Scrollbar padding / negative margin 9 → 2 dp | `dimens.xml` |

52 dp is about as thin as the 32 dp header buttons allow with a few dp of padding. That is enough to cover `safeInsetRight` for a ~5 mm hole and still be tappable.

### 6.3 Portrait on / landscape off

`VerticalTabUtils.isVerticalTabsEnabled()`:

- Not eligible → false (cover screen, phones).
- Landscape → false (horizontal strip).
- Otherwise (portrait tablet window) → **true**.

Chromium does **not** recreate on orientation alone. `ChromeActivity.performOnConfigurationChanged` is patched to `doRecreateActivity()` on orientation change so the layout actually switches. Slight flicker on rotate; acceptable for v1. Can later swap for a live `ComponentCallbacks` toggle in `TabbedRootUiCoordinator`.

### 6.4 Cutout gap

`patches/fold/FoldCutoutGapDecoration.java` is copied into:

`chrome/android/features/tab_ui/java/src/org/chromium/chrome/browser/tasks/tab_management/vertical_tabs/`

and registered next to `VerticalTabGroupSpineDecoration` in `VerticalTabListCoordinator`. The file is also added to `tab_management_java_sources.gni` (`internal_tab_management_java_sources` is an explicit list, not a glob).

Behavior:

- Listen for window insets + layout on the tab `RecyclerView`.
- Read `DisplayCutout.getBoundingRects()`.
- If a rect intersects the list, remember its Y in view coordinates (plus 4 dp).
- `getItemOffsets`: the first item whose range would overlap the hole gets extra `top` offset so the hole sits in empty rail, not on a favicon.

Works for mid-right (Fold 8) and for corner holes. If the cutout does not intersect the list (landscape, or hole in the header), there is no gap.

### 6.5 Build / sign helpers

- `build.sh` compiles **arm64** only unless `BUILD_32BIT=1`. First compile is the expensive one; skipping 32-bit roughly halves it.
- CI publish/attest lists arm64 artifacts only.
- `common.sh` `set_keys`: if `LOCAL_TEST_JKS` / `STORE_TEST_JKS` are unset, generate a **debug keystore** (`alias=fold`, password `android`) so a Hetzner SSH build can still sign. GitHub Actions should keep using real secrets when they exist.
- `keys/` is gitignored.

---

## 7. Build: why it is heavy, and how we will run it

Compiling Titanium is compiling **Chromium for Android** from source: Blink, V8, Skia, media, ICU, etc. Roughly tens of thousands of compile steps, 150–250 GB disk, and a link step that wants **64+ GB RAM**. Google does this on compile farms. A single beefy Linux box is the “bring your own hardware” version.

Chromium Android does **not** build on the Mac used for this overlay. Need **Ubuntu x86_64**, lots of cores, RAM, and disk.

### 7.1 Machine spec

Ask Hetzner (or equivalent) for **one** dedicated box:

| | Preferred | Acceptable |
| --- | --- | --- |
| Type | **CCX63** | CCX53 |
| Cores | 48 dedicated | 32 dedicated |
| RAM | 192 GB | 128 GB |
| Disk | 960 GB NVMe | 600 GB |
| Image | Ubuntu 24.04 x86 | same |
| Location | hel1 / fsn1 / nbg1 | EU |

Hourly (gross, as of planning): CCX63 about **€1.61/hour**, monthly cap about **€1,007** if left running the entire month. Realistic session: **€5–10 first build**, **€2–4** incremental, **€2–3/month** if a snapshot is kept.

**16 cores / 32 GB RAM (shared CPX)** can limp along with a large swap file. Not preferred: slow and likely to OOM at link.

### 7.2 Hetzner status (blocked)

- CLI `hcloud` works; API token is in `~/.config/hcloud/token`.
- SSH key `m2-air` was uploaded to the Hetzner project.
- Creating **CCX63 / 53 / 43** failed: **`dedicated core limit exceeded`** (new-account quota). Shared CPX was not created; we stopped to request a limit increase instead of falling back to an under-spec box.
- Limit request: raise **dedicated vCPU to 48** for a single CCX63, one server, compile Chromium, snapshot and delete after use. Hetzner Cloud has **no spending cap**; safety is quota + delete-after-session + optional self-destruct timer.

When the limit is approved:

1. Create `titanium-fold-build` CCX63, Ubuntu 24.04, hel1, SSH key `m2-air`.
2. Install a **10-hour self-destruct** (API delete) so a forgotten server cannot run all month.
3. Provision: swap/zram if needed, `git clone` this repo with submodules, install Chromium build deps via `build.sh` / `install-build-deps.sh`.
4. Run `./build.sh` on the box (arm64). First build **2–4 hours** on CCX63 after the Chromium fetch.
5. Copy `chromium/src/out/release/*-arm64-v8a.apk` off the machine.
6. **Snapshot, then delete** the server (stopped cloud servers still bill). Restore from snapshot for the next iteration so Chromium does not fetch from zero.

Optional later: register the box as a GitHub Actions **self-hosted** runner matching `build.yml` (`runs-on: self-hosted`). First APK does not need that; SSH is enough.

### 7.3 What not to use

- GitHub-hosted `ubuntu-latest`: disk/RAM too small.
- This Mac: not a Chromium Android host.
- Leaving a CCX63 running overnight “just in case”: ~€13–20. Snapshot + delete instead.

---

## 8. Milestones

### Milestone 0 — done

Enable `chrome://flags/#android-vertical-tabs` on current Titanium on the Fold 8. Confirmed: inner screen is tablet-sized; rail works; collapsed rail felt **too thick**; dock was **left** (wrong side for the camera).

### Milestone 1 — patched, not built (this is the first APK)

Flag on, right dock, 52 dp collapsed, portrait auto, cutout gap, arm64 APK. **Blocked on Hetzner dedicated-core limit.**

### Milestone 2 — device iteration after first sideload

Driven by what the Fold 8 actually shows:

- Nudge 52 dp if still thick or if buttons clip.
- Cutout spacer: first-item offset vs mid-list hole; header vs list; padding so the hole is in the gutter, not on the collapse control.
- Consume cutout insets for **WebContents** so the page is not padded *and* the rail (double inset). Chromium already has `DisplayCutoutController`; may need the rail to consume `displayCutout` on the right.
- Cover ↔ inner fold: no broken hybrid (VT must drop on the cover).
- Landscape: confirm horizontal strip; hole may move to a short edge — spacer should disappear if it no longer intersects the rail.
- Live orientation switch without full `recreate()` if flicker is annoying.
- Expanded rail (titles) only if 52 dp collapsed is too cramped; keep portrait default collapsed.

### Milestone 3 — maintainability

`patch.sh` `sed` against Chromium Java **will break** on 4-week Vanadium bumps (152 → 153 is already in tags). Prefer named `.patch` files (`git am`) for Fold UI, keep `sed` for one-line flag flips. Re-test strings in `VerticalTabUtils` / `VerticalTabsSideUiCoordinator` after each submodule update.

---

## 9. Test plan (first APK on Fold 8)

Install: uninstall Play/GitHub Titanium first if the APK is debug-signed. Sideload `*-arm64-v8a.apk`.

**Inner display, portrait**

- [ ] Vertical rail on the **right**, not left.
- [ ] Rail clearly thinner than stock 76 dp (~52 dp).
- [ ] No horizontal tab strip.
- [ ] Page content does not run through the punch-hole.
- [ ] No favicon, close, or + on the hole (gap in the list).
- [ ] Tabs still switch, close, and open.
- [ ] Collapse/expand still works; default is collapsed.

**Inner display, landscape**

- [ ] Horizontal tablet strip is back.
- [ ] Vertical rail is gone.

**Cover screen**

- [ ] Phone UI, no vertical rail.

**Fold / unfold**

- [ ] Inner portrait rail returns; cover stays phone UI.

**NTP / a long article / fullscreen video**

- [ ] Hole sits in the rail column.
- [ ] Fullscreen video: note whether the hole is still a problem (fullscreen may hide chrome).

---

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| 152 VT still unfinished vs HEAD | Ship 152; re-port seds if Vanadium jumps to 153 mid-work. |
| `sed` misses after a Chromium rename | First build log; then convert Fold hunks to real patches. |
| CachedFlag ignores field-trial enable | Several enable paths (BASE_FEATURE, CachedFlag, `enable_by_default`, field_trials). |
| Recreate-on-rotate loses tab UI state | Chromium already re-parents on some config changes; watch for lost scroll / keyboard. |
| Double padding around the hole | Milestone 2: consume display-cutout insets for web contents once the rail owns that edge. |
| Debug signature vs Play | Uninstall once; later use a real keystore in Actions secrets. |
| Hetzner bill | One server, snapshot+delete, 10 h self-destruct, do not raise limits beyond 48 dedicated vCPU. |

---

## 11. Next action

When Hetzner approves **48 dedicated vCPUs**:

1. Create the CCX63 (do not leave it running idle).
2. Provision + `./build.sh`.
3. Sideload the arm64 APK and run section 9.
4. Snapshot and delete the server.
5. File Milestone 2 tweaks from the device, not from screenshots alone.

No Chromium compile can start until that quota exists. Overlay code for Milestone 1 is already on `main`. Keep follow-up patches on `main` as well.
