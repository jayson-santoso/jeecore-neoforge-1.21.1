# JeeCore

A Minecraft 1.21.1 mod for Jee's Pack, built with NeoForge and Java 21.
Gameplay is organized around four genes: Dryad, Infernal, Nimbus, and Terragolem.

## Getting started

- Install JDK 21 and use the included Gradle wrapper.
- Ensure `libs/caelus-neoforge-7.0.1+1.21.1.jar` is available; the build uses this local dependency.
- Build: `./gradlew build` (Windows: `.\gradlew.bat build`). The mod jar is written to `build/libs/`.
- Launch the client on Windows: `./scripts/run-client.ps1`. This launcher handles Scoop cache aliases.
- Other runs: `./gradlew runClient2` and `./gradlew runServer`.

## Repository map

```text
src/main/
  java/net/sonflowers/jeecore/
    JeeCore.java           Mod entry point, registration, and commands
    core/                  Shared gene data, ability handling, networking,
                           registries, client UI, and event handling
    gene/
      dryad/               Healing, spores, and root snare
      infernal/            Rage, smoke, and charges
      nimbus/              Wind, glide, gust, and Windrunner
      terragolem/          Fuel, repair, overheating, impact, and related abilities
    mixin/                 Minecraft hooks; client-only hooks in mixin/client/
  resources/
    assets/jeecore/         Textures, models, language, and particle definitions
    data/                  Recipes, tags, advancements, and NeoForge data maps
    jeecore.mixins.json     Mixin registration
  templates/               Mod metadata expanded by Gradle
docs/
  features/<gene>/         Feature plans and playtest checklists
  audits/                  Investigation notes
scripts/                   Client and GameTest launchers
  assets/                  Asset generation tools
artwork/                   Source artwork, previews, and animation references
libs/                      Local dependency jars
runs/                      Local game worlds, settings, and logs (ignored)
```

Inside each gene, `server/` contains server gameplay, `client/` contains rendering
and input, and `network/` contains its payloads. Other packages such as `effect/`,
`entity/`, `menu/`, and `data/` hold that gene's content. Put reusable systems in
`core/` and gene-specific behavior beside its gene. Keep client-only Minecraft APIs
in client code; some existing shared state and payload handlers need care during refactoring.

`build.gradle` defines dependencies, run configurations, and verification tasks.
`gradle.properties` holds mod identity and version settings. `.github/workflows/`
contains CI. Generated output and caches live in `build/` and `.gradle/`.
Keep Minecraft's resource directory conventions when adding assets or data.

## Local tests

Tests are temporarily ignored by Git and kept outside the published mod jar:

- `src/regression/java/`: standalone regression checks. Run with `./gradlew check`.
- `src/gameTest/java/` and `src/gameTest/resources/`: Minecraft integration tests.
  Run with `./scripts/run-gametests.ps1` after dependencies are cached (the launcher uses offline mode).
- `check` runs available regression checks and compiles GameTests; it does not execute GameTests.

A fresh checkout has no test sources; regression tasks skip when their sources are
absent. Running GameTests requires the local test files. Runtime directories are
`runs/client/`, `runs/client2/`, `runs/server/`, `runs/data/`, and `runs/gametest/`.

See [feature notes](docs/features/) and [artwork instructions](artwork/README.md)
for design context and asset rebuilding. `TEMPLATE_LICENSE.txt` retains the
NeoForge template notice. Follow the [comment style](docs/comment-style.md) when
documenting code.
