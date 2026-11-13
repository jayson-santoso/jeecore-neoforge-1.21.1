# JeeCore
JeeCore is a multiplayer-focused Minecraft 1.21.1 mod built with NeoForge and Java 21. Your adventure begins by choosing an RPG-style starting gene, equipped with a set of unique abilities, strengths, and of course a few weaknesses to keep you humble. But worry not, your friends might be equipped with abilities to cover your shortcomings (provided they aren’t too busy dealing with their own). With these complementary classes and diverse playstyles, a little teamwork can go a long way.

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

See [feature notes](docs/features/) and [artwork instructions](artwork/README.md)
for design context and asset rebuilding. `TEMPLATE_LICENSE.txt` retains the
NeoForge template notice. Follow the [comment style](docs/comment-style.md) when
documenting code.
