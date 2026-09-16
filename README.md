# Handy Trader

A Fabric mod that lets you bookmark favorite villager trades. Targets Minecraft 26.3.

Star the trades you use most and they sort to the top of the list automatically — perfect for big trading halls where you only use 2 out of 10 master trades per villager.

[![Modrinth](https://img.shields.io/badge/Modrinth-handy--trader-green)](https://modrinth.com/project/handy-trader)

## Features

- **Bookmark favorites** — Click the top-left corner of any trade row to mark it with a gold bookmark indicator.
- **Sort to top** — Favorited trades automatically move to the top of the trade list.
- **Per-villager storage** — Each villager remembers its own favorites, persisted across sessions.
- **Hover feedback** — Ghost bookmark appears when hovering the corner; brightens on favorited rows.
- **Sound feedback** — Subtle amethyst chime when toggling favorites.
- **Configurable** — Toggle favorites on/off via config screen or JSON file.

## Requirements

- Minecraft Java Edition 26.3
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3+
- [Fabric API](https://modrinth.com/mod/fabric-api) matching your Minecraft version

### Optional (for config screen)

- [ModMenu](https://modrinth.com/mod/modmenu) — adds a Configure button in the mod list
- [YACL](https://modrinth.com/mod/yacl) — powers the in-game config screen

Without these, everything works with sensible defaults. You can also edit `config/handytrader.json` manually.

## Installation

### Single Player

1. Install Fabric Loader for Minecraft 26.3
2. Download Fabric API and place it in your `mods/` folder
3. Download Handy Trader and place it in your `mods/` folder
4. Launch the game and open a villager trade screen!

### Server

This is a **client-side only** mod. Install it on the client — no server installation needed.

## Building from Source

```bash
git clone https://github.com/dfox288/HandyTrader.git
cd HandyTrader

./gradlew build
# The compiled JAR will be in build/libs/
```

## Development

```bash
# Generate Minecraft sources for reference
./gradlew genSources

# Run Minecraft with the mod loaded
./gradlew runClient
```

## Part of the Handy series

Small Fabric mods that smooth over vanilla friction points:

- [Handy Shulker](https://modrinth.com/mod/handy-shulker) — bundle-like interactions for shulker boxes
- [Handy Bookshelf](https://modrinth.com/mod/handy-bookshelf) — enchantment glint and name tags for chiseled bookshelves
- [Handy Indicator](https://modrinth.com/mod/handy-indicator) — visual indicators on container blocks

## License

MIT License — see [LICENSE](LICENSE) for details.
