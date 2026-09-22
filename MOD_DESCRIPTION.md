# DamageStats

> Server-authoritative damage tracking and combat analytics for Minecraft.

DamageStats records successful damage dealt to `LivingEntity` targets and turns it into practical combat information: damage totals, DPS, hit counts, damage-type breakdowns, direct-source analysis, fight history, and a configurable in-game overlay.

The mod is designed for both personal combat analysis and server administration. Statistics are collected and validated on the server, while each player receives only the views they are allowed to browse.

## Features

- **Damage and DPS tracking** - View final damage, original damage, reduced damage, DPS, hit counts, average hit, highest hit, lowest hit, kills, and hits per second.
- **Multiple damage dimensions** - Break data down by damage type, direct source, responsible source, target, and entity instance.
- **Responsible-source resolution** - Projectile damage is attributed to its shooter, and owned entities such as tamed mobs are attributed to their owner while the direct source remains available for deeper analysis.
- **Fight sessions and lifetime totals** - Compare the current fight with lifetime totals and review a configurable number of finished fights.
- **Interactive statistics GUI** - Select sources and targets, search and page through entity choices, switch chart dimensions, change session/lifetime scope, filter chart rows, and inspect detailed metrics.
- **Instance drill-down** - Inspect recorded living-entity instances and direct-source paths involved in the attack.
- **Draggable overlay** - Keep selected combat metrics on screen and configure its position, scale, opacity, visible fields, and whether each metric uses the current fight or lifetime data.
- **Persistent storage** - World-specific statistics are stored under the save's `damagestats` directory, with an event journal, aggregate cache, indexes, and recovery-oriented maintenance paths.
- **JSON and CSV export** - Export the current view as a complete JSON snapshot and a tabular CSV file.
- **Configurable damage categories** - Group damage types through `config/damagestats-damage-types.json`, including custom damage types added by other mods.
- **Permission-aware browsing** - Ordinary players can query their own outgoing damage and authorized direct-source drill-downs; operators can browse global statistics and manage server storage.

## Installation

1. Install a Minecraft version and mod loader supported by the specific DamageStats release.
2. Use the Java version required by that Minecraft and mod-loader combination.
3. Put the DamageStats jar in the `mods` folder.
4. Install DamageStats on both the client and the server for multiplayer use. The client and server must use compatible mod versions.
5. Start the game or server once to generate the configuration files.

## In-Game Controls

| Control | Action |
|---|---|
| `H` | Open the DamageStats screen |
| `J` | Show or hide the overlay |
| `/damagestats toggle` | Enable or disable new damage tracking (permission level 2) |
| `/damagestats storage` | Open the server storage overview (permission level 2) |
| `/damagestats export` | Export server-wide statistics to the world storage directory (permission level 2) |
| `/damagestats reload` | Reload damage-type categories (permission level 2) |

The default keys can be changed in Minecraft's **Controls** menu under the **DamageStats** category.

## Using the GUI

Open the dashboard with `H`.

- Use **Source** and **Target** selectors to choose what you want to browse.
- Choose a chart dimension such as **Damage Type**, **Direct Source**, **Responsible Source**, or **Target**.
- Switch between **This Fight** and **Total** to compare the active combat session with lifetime data.
- Click chart rows to apply filters, clear filters, or inspect recorded entity instances.
- Use **Set Focus** when you want the selected source and target to drive live synchronization and the overlay.
- Use **Refresh** to request a fresh server-authoritative view after recent damage or data maintenance.
- Open **More Actions** for detailed metrics, fight history, overlay settings, and reset actions.

### Source, Direct Source, and Target

DamageStats keeps three useful relationships for each event:

- **Responsible source** - The entity credited with the damage, such as a player behind an arrow or the owner of a tamed mob.
- **Direct source** - The entity that directly reached the target, such as an arrow, fireball, mob, or melee attacker.
- **Target** - The `LivingEntity` that received the damage.

For example, an arrow hit can be shown as the player responsible for the damage while still retaining the arrow's direct-source information. This makes it possible to start with a player's outgoing damage and inspect the direct source used for a specific attack.

### Damage Scope

- **This Fight** shows the active fight session. A session ends after the configured timeout without new damage.
- **Total** shows lifetime accumulated data for the current world.
- Finished fight summaries can be retained according to the server configuration.

## Overlay

The overlay can display a selection of the following values:

- Source and target
- Final damage and original damage
- Damage reduction and reduction rate
- Average DPS and live DPS
- Original-damage DPS
- Hit count and average hit
- Highest final/original hit
- Top damage type
- Top direct source
- Combat-session status

Each supported metric can use either the current fight or lifetime scope. The overlay editor lets you drag the overlay and adjust its scale and background opacity. The overlay is disabled by default and can be toggled with `J`.

## Server Configuration

The common configuration is stored at `config/damagestats-common.toml`.

| Option | Default | Description |
|---|---:|---|
| `trackingEnabled` | `true` | Whether new damage events are recorded. Existing data is preserved when tracking is disabled. |
| `sessionTimeoutTicks` | `100` | Ticks without new damage before the current fight ends. `20` ticks equals one second. |
| `dpsWindowTicks` | `100` | Window used for live DPS. Shorter windows react faster; longer windows are smoother. |
| `instanceDirectoryRetentionHours` | `72` | How long inactive instance candidates remain in the selection directory. |
| `instanceDirectoryLimit` | `5000` | Maximum number of instance candidates retained in the directory. This does not delete the complete event history. |
| `keepFinishedSessions` | `10` | Number of finished fight summaries to retain. Set to `0` to keep no history. |
| `autoSave` | `true` | Whether aggregate statistics are periodically saved according to the autosave interval. |
| `autoSaveIntervalTicks` | `0` | Autosave interval in ticks. `0` disables periodic aggregate saves; normal shutdown still saves data. |
| `publicStats` | `true` | Allows ordinary players to browse statistics beyond their own private outgoing damage, subject to server validation. |

Client-only overlay settings are stored in `config/damagestats-client.toml`.

### Damage Type Categories

DamageStats generates `config/damagestats-damage-types.json` with example categories such as `Physical`, `Magic`, `Fire`, `Explosion`, and `Environment`. Edit this file to add or reorganize damage type IDs:

```json
{
  "Physical": [
    "minecraft:player_attack",
    "minecraft:mob_attack",
    "minecraft:arrow"
  ],
  "Magic": [
    "minecraft:magic",
    "minecraft:indirect_magic"
  ],
  "Environment": [
    "minecraft:fall",
    "minecraft:lava",
    "minecraft:drown"
  ]
}
```

Use `/damagestats reload` after editing the file. Damage types that are not assigned to a category remain available by their registry ID.

## Permissions

DamageStats uses Minecraft permission levels:

- **Permission level 0** - Players can view their own outgoing damage, select targets, and use authorized direct-source instance drill-down.
- **Permission level 2** - Operators can browse arbitrary recorded sources and targets, view global statistics, use server storage tools, export world statistics, toggle tracking, and reload damage categories.

The server validates GUI requests; buttons and packets do not grant additional permissions by themselves.

## Storage and Export

World statistics are stored in:

```text
<world>/damagestats/
```

The directory contains the aggregate statistics cache and the persistent event journal used for authoritative queries and recovery. The storage overview can show event counts, segment sizes, index/cache usage, exports, temporary files, backups, and total usage.

There are two export paths:

- **GUI export** - Exports the currently browsed, permission-checked view to the client game directory under `damagestats/exports/`.
- **`/damagestats export`** - Exports server statistics to the world's `damagestats/exports/` directory and requires permission level 2.

Both exports create matching `.json` and `.csv` files. JSON contains filters, current-fight data, lifetime data, finished-fight summaries, grouped details, and reduction information. CSV contains tabular grouped details suitable for spreadsheets or further analysis.

Administrators can use the storage overview to clean temporary files and migration backups, remove server exports, or clear all records. Destructive cleanup actions require confirmation.

## Important Notes and Limitations

- DamageStats records successful damage events with a final amount greater than zero. Fully immune or otherwise zero-damage events are not counted.
- The amount of reduction detail depends on the damage event API provided by the target Minecraft version and mod loader. Some versions or damage sources may not expose every reduction component separately.
- Damage tracking is server-authoritative. A client cannot query statistics from a server that does not have DamageStats installed.
- Statistics are stored per world/save.
- The instance directory is a selection index and retention mechanism; pruning it does not erase the complete recorded event history.
- Very large histories can make server-side queries more expensive. Test long-running or high-frequency servers with their expected workload.

## Compatibility

DamageStats is distributed in version-specific builds. Choose the file that matches your Minecraft version and mod loader. The project includes separate version branches, so the available features and low-level event details may vary slightly between releases.

- **Side:** Client and server
- **Mod ID:** `damagestats`

## Links

- **Source code and issue tracker:** [GitHub](https://github.com/Zershyan/DamageStats)
- **License:** GNU AGPL 3.0

## Credits

Created by **zershyan**.

DamageStats is intended to complement combat testing, server administration, and technical gameplay analysis across its supported Minecraft versions.
