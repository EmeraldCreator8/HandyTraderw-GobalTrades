# Changelog

## 2.3.0-beta.1

Preview build for the **Minecraft 26.3** snapshot cycle (tested against 26.3-snapshot-1).

- Rebuilt against Fabric API **0.153.1+26.3** and YACL **3.9.5+26.3-fabric**.
- No feature changes were needed for 26.3.
- ModMenu has no 26.3 build yet, so it won't list the mod on 26.3 until it updates — the config screen is still reachable through YACL.

## 2.2.1

- **Fixed a conflict with Villager Trading Plus (Easier Villager Trading).** When a villager had a favorited trade, clicking it fell back to vanilla single-trade instead of that mod's "trade all on click". HandyTrader was cancelling the whole trade-selection method to apply the favorites reorder against the server; that cancellation also swallowed the other mod's hook. The reorder is now applied by remapping only the outgoing trade-select packet, so other trade mods run normally. (#20)

## 2.2.0

Updated for the **Minecraft 26.2** full release.

- Rebuilt against Fabric API **0.152.1+26.2** and Fabric Loader **0.19.3**.
- YACL updated to **3.9.4+26.2-fabric** and ModMenu to **20.0.0-beta.2**; with YACL's stable 26.2 build out, the in-game config screen is back in dev and live play.
- Now declares compatibility with `26.2` and future `26.2.x` point releases.

## 2.1.0-beta.4

- Tracks Minecraft **26.2-pre-1** (first prerelease of the 26.2 cycle); skips snapshots 6, 7, and 8 since none broke this mod's surface.
- Rebuilt against Fabric API **0.149.2+26.2**.
- YACL bumped to **3.9.3+26.2-fabric** and ModMenu to **19.0.0-alpha.1** on the compile classpath. Neither has a build that explicitly lists `26.2-pre-1` in its game_versions yet, so they remain `compileOnly` — the in-game config screen returns to dev runtime once either ships a matching build.

## 2.1.0-beta.3

### Breaking
- **Mod ID renamed from `handytraders` to `handytrader`** to match the rest of the Handy series convention (singular). Existing config (`config/handytraders.json`) and per-villager favorites (`config/handytraders-favorites.json`) are migrated automatically on first launch — no settings or favorites lost.
- Internal package moved from `net.rezanmb.handytraders` to `dev.handy.mods.handytrader`. No user-facing impact unless another mod was depending on internal classes.

### Changes
- **Trade-identity hash upgraded from 32-bit hashCode to 64-bit truncated SHA-1.** Eliminates the silent collision risk where two distinct trades on the same villager could star/unstar each other. Existing favorites are rewritten to the new format the first time you reopen each villager — no manual reset needed, but villagers you never revisit stay on the old format.
- **Mod is now declared client-only** (`environment: "client"`). Dedicated servers no longer load the jar; the mod was already client-only in behavior, this just makes it explicit.
- **Favorites file write moved off the render thread** so toggling a bookmark on a slow disk no longer stalls the frame.
- **Config persistence rebuilt on YACL `ConfigClassHandler`** (matches the rest of the Handy suite). On-disk JSON shape is unchanged; users without YACL installed still run on defaults.

### Internal
- Cleanup wave aligned this mod with the rest of the suite — JAVA_25 mixin compatibility level, full @At descriptors, named bookmark color palette, narrowed exception handlers, and a CI release workflow that no longer fails on prerelease tags.

## 2.1.0-beta.1

- Preview build for Minecraft **26.2 snapshots** (tested against 26.2-snapshot-3)
- Rebuilt against Fabric API 0.146.1+26.2
- No source changes needed — the trade-menu mixins and favorites overlay compile cleanly against 26.2

## 2.0.2

- Update to Minecraft 26.1.2 compatibility
- Update Fabric Loader to 0.19.2, Fabric API to 0.146.1

## 2.0.1

- Update to Minecraft 26.1.1 compatibility
- Update Fabric Loader to 0.18.6, Fabric API to 0.145.3, YACL to 3.9.2

## 2.0.0

- Port to Minecraft 26.1 (Java 25, unobfuscated)
- Restore YACL config screen integration

## 2.0.0-beta.1

- Port to Minecraft 26.1-rc-1 (Java 25, unobfuscated)
- Restore YACL config screen integration
- Add CI/CD pipeline with automated Modrinth publishing

## v1.0.1

### Bug Fixes
- **Fix trade selection desync when favorites are sorted** — clicking a sorted trade row now correctly tells the server which trade was selected, fixing the issue where items would flash in/out of payment slots or trades would silently fail
- **Fix selection preservation on bookmark toggle** — toggling a bookmark mid-session no longer desyncs the selected trade; the previously selected trade stays selected at its new sorted position
- **Handle server-side offer refresh** — when a villager restocks or levels up, the mod now detects the new offers and re-applies favorite sorting instead of showing stale data

### Technical
- Replaced non-functional `@ModifyArg` on `ServerboundSelectTradePacket` constructor with `@Inject`-based interception of `postButtonClick` for reliable trade index remapping
- Added `lastKnownOffers` reference tracking to detect server-side offer replacements

## v1.0.0

Initial release for Minecraft 1.21.11 (Fabric).

### Features
- **Favorite trades** — click the top-left corner of any trade row to bookmark it with a gold corner indicator
- **Sort favorites to top** — favorited trades automatically move to the top of the trade list
- **Per-villager persistence** — favorites are stored per villager UUID and saved to disk across sessions
- **Bookmark hover feedback** — ghost bookmark appears when hovering the corner, brightens on favorited rows
- **Amethyst chime** — subtle sound feedback when toggling favorites
- **Config screen** — "Enable Favorites" toggle via YACL + ModMenu (both optional)
- Config persists as `config/handytraders.json` (editable manually without YACL)
- Favorites persist as `config/handytraders-favorites.json`
