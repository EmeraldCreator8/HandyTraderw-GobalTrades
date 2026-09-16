# Handy Trader - Fabric Mod

## Project Overview

A Fabric mod that adds star/favorite toggles to the villager trading UI. Favorited trades get a gold highlight overlay so they can be found instantly in long trade lists. Per-villager storage keyed by UUID, persisted as JSON.

## Tech Stack

- **Minecraft**: 26.3 (unobfuscated — no mappings needed)
- **Fabric Loader**: 0.19.5
- **Fabric API**: 0.160.6+26.3
- **Fabric Loom**: 1.18.2 (Gradle 9.7.1)
- **Java**: 25
- **YACL**: 3.9.6+26.3-fabric (soft dependency, via Modrinth Maven `maven.modrinth:yacl`)
- **ModMenu**: 21.0.0-beta.1 (soft dependency)

## Build

```bash
cd /Users/dfox/Development/minecraft/HandyTrader && ./gradlew build
cd /Users/dfox/Development/minecraft/HandyTrader && ./gradlew runClient
cd /Users/dfox/Development/minecraft/HandyTrader && ./gradlew genSources
```

Always prefix commands with `cd /path &&` so they auto-approve via permission rules.

## Project Structure

Client-only mod — `fabric.mod.json` declares `environment: "client"`, no main entrypoint. Uses `splitEnvironmentSourceSets()`:
- `src/main/` — shared code (config class + LOGGER holder; loaded everywhere when present)
- `src/client/` — client-only code (UI overlay, config screen, mixin)

Package: `dev.handy.mods.handytrader` (mod-id: `handytrader`)

## Dependencies

YACL and ModMenu are **soft dependencies** — `compileOnly`/`localRuntime` in build.gradle. The mod works without them. Config screen code checks `FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")` at runtime.

Maven repos:
- `https://maven.terraformersmc.com/` — ModMenu
- `https://api.modrinth.com/maven` — YACL (artifact: `maven.modrinth:yacl`)

## Config

YACL config screen with option for:
- Enable/disable favorites

Config class: `HandyTraderConfig` — JSON file at `config/handytrader.json` (with one-shot migration from `config/handytraders.json`).
Per-villager favorites stored at `config/handytrader-favorites.json` (with one-shot migration from `config/handytraders-favorites.json`).
