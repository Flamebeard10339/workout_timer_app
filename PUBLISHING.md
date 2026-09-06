# Getting Circuit Clock into F-Droid

F-Droid does not host uploaded APKs. It builds every app itself, from a git tag in this
repository, using a recipe that lives in F-Droid's own **fdroiddata** repository on GitLab.
So publishing is two things: tag a release here, then get a recipe merged there.

We are the author, so we submit the recipe ourselves as a merge request. (The alternative —
an RFP issue asking someone else to package it — is for apps you did not write, and sits in
a long queue.)

---

## Done

- [x] **The build is verified.** The `Build APK` workflow compiles the project from a clean
      checkout on every push to `main`, and is green. That is the same thing F-Droid's CI
      does, so a green run here is a strong signal.
- [x] **`v1.0` is tagged and pushed**, pointing at commit `3ee4c9f`, with a matching
      [GitHub release](https://github.com/Flamebeard10339/workout_timer_app/releases/tag/v1.0)
      so the recipe's `Changelog:` link resolves.
- [x] **The recipe is written and pre-flight checked** —
      [`fdroid/io.github.flamebeard10339.circuitclock.yml`](fdroid/io.github.flamebeard10339.circuitclock.yml).
      Copy-paste ready, no comments to strip. Its categories were validated against the real
      `config/categories.yml` in fdroiddata (113 categories; `Timer` is defined there as
      "Interval timing, timekeeping, countdown").
- [x] **Listing text** is in `fastlane/metadata/android/en-US/`, all within F-Droid's length
      caps, with a changelog named for the versionCode.

## Remaining — needs a GitLab account

### 1. Fork fdroiddata and add the recipe

```bash
# Fork https://gitlab.com/fdroid/fdroiddata in the GitLab UI first, then:
git clone https://gitlab.com/<your-gitlab-user>/fdroiddata
cd fdroiddata
git checkout -b circuitclock
cp <this repo>/fdroid/io.github.flamebeard10339.circuitclock.yml \
   metadata/io.github.flamebeard10339.circuitclock.yml
```

### 2. Lint and test-build the recipe

This needs `fdroidserver`, which is Linux-only. On Windows use WSL, or Docker:

```bash
docker run --rm -it -v "$PWD":/repo -w /repo registry.gitlab.com/fdroid/fdroidserver:latest bash
```

Then, inside:

```bash
fdroid rewritemeta io.github.flamebeard10339.circuitclock   # normalises formatting
fdroid lint io.github.flamebeard10339.circuitclock          # must print nothing
fdroid build -v -l io.github.flamebeard10339.circuitclock   # the real build
```

`fdroid build` is the one that matters. If it succeeds, F-Droid's CI almost certainly will.

### 3. Open the merge request

```bash
git add metadata/io.github.flamebeard10339.circuitclock.yml
git commit -m "New app: Circuit Clock"
git push -u origin circuitclock
```

Open the MR against `fdroid/fdroiddata` `master`, titled **New app: Circuit Clock**. GitLab
CI runs lint and build again. A maintainer reviews it; expect days to a few weeks, and
expect questions — answer them in the MR thread.

Once merged, the app appears after the next build cycle, usually a day or two.

---

## What reviewers will look at, and where this app stands

| Requirement | Status |
| --- | --- |
| Free software licence, `LICENSE` present | MIT |
| No proprietary dependencies | Only `androidx.webkit` |
| No tracking, ads, or analytics | None |
| Builds from source with no prebuilt binaries in-tree | Verified: no `.jar`/`.aar`/`.so`/`.apk` committed. The Gradle wrapper jar is deliberately absent — F-Droid supplies its own |
| Reproducible-friendly | `dependenciesInfo` excluded from the APK |
| Anti-features to declare | None |

Worth putting in the MR description: **the manifest declares no permissions at all**, so
without `android.permission.INTERNET` the app is incapable of network access. "Works
offline" is enforced by the OS rather than asserted by the developer. Reviewers notice this.

## Updating later

`AutoUpdateMode: Version` plus a tag-matching `UpdateCheckMode` means F-Droid picks up new
releases on its own. To ship 1.1:

1. Bump `versionCode` to 2 and `versionName` to `"1.1"` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/2.txt` — named for the **versionCode**,
   not the version name.
3. Commit, tag `v1.1`, push the tag.

F-Droid notices the tag and builds it. No second merge request.

The recipe's `UpdateCheckMode` carries a regex restricting it to tags shaped like `v1.2.3`.
That matters because CI publishes a rolling `dev-build` tag for sideloading; without the
filter, F-Droid would try to read it as a version number.

## Optional: screenshots

The listing looks bare without them. Drop PNGs into
`fastlane/metadata/android/en-US/images/phoneScreenshots/` (named `1.png`, `2.png`, …) and
they are picked up automatically. A 512×512 `images/icon.png` is used if present; otherwise
F-Droid renders the launcher icon.
