# Contributing – no coding skills required

Lumen is **vibe-coded**: people describe what they want, AI writes the code. The interesting
question of this experiment is what happens when anyone – not just developers – can shape an app.
So: your idea counts, even if you've never seen a line of code.

There are three ways to help. Pick the one that fits you.

## 1. Share an idea (5 minutes, no setup)

Open an [💡 Idea issue](../../issues/new?template=idea.yml) and describe what you want, like you'd
explain it to a friend:

> "When the screensaver comes on, I'd like to see a photo from my iPhone album instead of the
> gradient."

Pictures and sketches help a lot. Ideas are open for everyone: anyone with an AI assistant can
pick one up and build it (see 3. below), and you'll be asked to try the result.

**Tips for a good idea:** say *what you see and press* on the TV, *why* you want it, and what
should *not* change. One idea per issue.

## 2. Test on your TV (the most valuable thing)

AI can't hold a remote. Every pull request needs someone who installs it and tries it:

1. Open the pull request, click **Checks → CI → Artifacts → lumen-apk**, download and unzip.
2. Install: `./tv-install.sh <your-fire-tv-ip> app-release.apk` (see [README](README.md#install)).
3. Comment what worked and what didn't. Photos of the TV are perfect.

Also great: [🐞 bug reports](../../issues/new?template=bug.yml), telling us which Fire TV
models Lumen runs on, and **translations**: all texts are in
[`strings.xml`](app/src/main/res/values/strings.xml) – copy it to `values-xx/` (xx = your language
code) and translate, or let an AI do it and check the result.

## 3. Build it yourself with an AI assistant

You don't need to understand the code, but you need a few tools:
[Git](https://git-scm.com/downloads), [JDK 17](https://adoptium.net),
[Android command-line tools](https://developer.android.com/studio#command-line-tools-only) (or Android Studio),
and an AI coding assistant – e.g. [Claude Code](https://claude.com/claude-code), Cursor or Copilot.

1. **Fork** this repo on GitHub and clone your fork.
2. Open the folder in your AI assistant and say what you want – e.g.
   *"Read CLAUDE.md. Then add a button to the control center that turns the TV off."*
   The assistant reads [CLAUDE.md](CLAUDE.md), which explains the project and the rules.
3. Let it run the tests and build (`./gradlew testDebugUnitTest assembleRelease`), then install
   on your TV with `./tv-install.sh <ip>` and try it.
4. Not happy? Tell the assistant what's wrong, in your own words. Repeat.
5. Ask the assistant to commit and open a pull request. **Please include your prompts** in the
   PR – they are part of the experiment.

## Rules for every change (human or AI)

- CI must be green (tests, lint, build). New logic comes with tests where possible.
- No private data: no API keys, calendar links, IP addresses or screenshots with personal content.
- One idea per pull request, and say if it was tested on a real device (which one).
- Don't break the TV: nothing that disables Amazon's settings, system updates or makes the device
  hard to recover.
- Be kind. Many people here are doing this for the first time.

## For maintainers

- There is no AI running in GitHub Actions – every contributor uses their own assistant, so the
  repo costs nothing to run.
- Releases: push a tag `vX.Y.Z` → [release workflow](.github/workflows/release.yml) builds and
  publishes the APK. Add the `LUMEN_KEYSTORE_*` secrets so updates install over older versions.
