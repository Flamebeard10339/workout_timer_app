# Getting Circuit Clock into F-Droid

F-Droid does not host uploaded APKs. It builds every app itself, from a git tag in your
repository, using a recipe that lives in F-Droid's own **fdroiddata** repository on GitLab.
So publishing is two things: tag a release here, then get a recipe merged there.

You are the author, so submit the recipe yourself as a merge request. (The alternative —
opening an RFP issue asking someone else to package it — is for apps you did not write, and
it sits in a long queue.)

---

## 1. Tag a release

F-Droid builds from a tag, not from a branch.

```bash
git tag -a v1.0 -m "Circuit Clock 1.0"
git push origin v1.0
```

Optionally make it a GitHub release too, so the `Changelog:` link in the recipe resolves:

```bash
gh release create v1.0 --title "Circuit Clock 1.0" --notes "First release."
```

The tag name, `versionCode` and `versionName` must line up with the recipe. Right now that
is `v1.0`, `versionCode = 1`, `versionName = "1.0"` in `app/build.gradle.kts`.

## 2. Confirm it builds from a clean clone

Do this before submitting — F-Droid's CI will do exactly this, and a failure there costs a
review round trip.

```bash
git clone --branch v1.0 https://github.com/Flamebeard10339/workout_timer_app /tmp/cc-check
cd /tmp/cc-check
gradle wrapper          # the wrapper jar is not committed; F-Droid supplies its own too
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK. An unsigned release APK appears in
`app/build/outputs/apk/release/`.

## 3. Fork fdroiddata and add the recipe

```bash
# Fork https://gitlab.com/fdroid/fdroiddata in the GitLab UI first, then:
git clone https://gitlab.com/<your-gitlab-user>/fdroiddata
cd fdroiddata
git checkout -b circuitclock
cp <this repo>/fdroid/io.github.flamebeard10339.circuitclock.yml \
   metadata/io.github.flamebeard10339.circuitclock.yml
# delete the leading ## comment lines from the copy
```

## 4. Lint and test-build the recipe

This needs `fdroidserver`, which is Linux-only. On Windows use WSL, or Docker:

```bash
docker run --rm -it -v "$PWD":/repo -w /repo \
  registry.gitlab.com/fdroid/fdroidserver:latest bash
```

Then, inside:

```bash
fdroid rewritemeta io.github.flamebeard10339.circuitclock   # normalises formatting
fdroid lint io.github.flamebeard10339.circuitclock          # must print nothing
fdroid build -v -l io.github.flamebeard10339.circuitclock   # the real build
```

`fdroid build` is the one that matters. If it succeeds, F-Droid's CI almost certainly will.

## 5. Open the merge request

```bash
git add metadata/io.github.flamebeard10339.circuitclock.yml
git commit -m "New app: Circuit Clock"
git push -u origin circuitclock
```

Open the MR against `fdroid/fdroiddata` `master`, titled **New app: Circuit Clock**. GitLab
CI runs the lint and build again. A maintainer reviews it; expect days to a few weeks, and
expect questions. Answer them in the MR thread.

Once merged, the app appears in the repository after the next build cycle — usually within
a day or two.

---

## What reviewers will look at, and where this app stands

| Requirement | Status |
| --- | --- |
| Free software licence, `LICENSE` present | MIT |
| No proprietary dependencies | Only `androidx.webkit` |
| No tracking, ads, or analytics | None |
| Builds from source with no prebuilt binaries in-tree | No jars committed; the Gradle wrapper jar is deliberately absent |
| Reproducible-friendly | `dependenciesInfo` is excluded from the APK |
| Anti-features to declare | None |
| Listing text and changelog | `fastlane/metadata/android/en-US/` |

The permission list is empty, which reviewers like and which is worth pointing out in the
MR description: without `android.permission.INTERNET` the app is incapable of network
access, so "works offline" is enforced by the OS rather than asserted by the developer.

## Updating later

With `UpdateCheckMode: Tags` and `AutoUpdateMode: Version`, F-Droid picks up new releases on
its own. To ship 1.1:

1. Bump `versionCode` to 2 and `versionName` to `"1.1"` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/2.txt` — the file is named for the
   `versionCode`, not the version name.
3. Commit, tag `v1.1`, push the tag.

F-Droid notices the tag and builds it. No second merge request.

## Optional: screenshots

The listing looks bare without them. Drop PNGs into
`fastlane/metadata/android/en-US/images/phoneScreenshots/` (named `1.png`, `2.png`, …) and
they are picked up automatically. A phone icon at
`fastlane/metadata/android/en-US/images/icon.png` (512×512) is also used if present;
otherwise F-Droid renders the launcher icon.
