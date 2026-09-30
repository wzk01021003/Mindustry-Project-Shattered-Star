# Project Shattered Star

A large multi-planet content mod for **Mindustry v8 (v160.3)**.

Shattered Star expands the game with a new custom solar system, dozens of
modified units, new AI behaviours, a multi-unit assembler block, and an
experimental screen-distortion rendering system.

---

## Content

### Planets

| Name | Description |
|---|---|
| `ss-sun` | Custom star. Invisible in the planet map. |
| `serpulo-r` | A parallel copy of Serpulo, orbiting `ss-sun`. |

### Units

Shattered Star reworks all base-game units into `*-r` variants and adds
two extra families:

- **Serpulo** — Ground / Air / Naval, combat and support.
- **Erekir** — Tanks / Mechs / Ships, plus a spider line with **glowing
  legs**.
- **Allotropes** (`*-at`) — A new branch of units with custom visuals.
- **Missiles** — Embedded missile units used by specific weapons
  (`plasma-missile`, `quell-missile-r`, `disrupt-missile-r`,
  `anthicus-missile-r`).

### Blocks

- **Multi Assembler** (`multi-assembler`) — Assembles multiple unit types
  in parallel, with a queue, loop mode, command assignment, and
  payload support.

### AI

Three custom unit commands:

- **`ss-hunt`** — Delegates to vanilla FlyingAI / GroundAI.
- **`ss-protect`** — Follows an allied target and defends it.
- **`ss-guard`** — Holds a point and patrols around it.

### Experimental Rendering

A toggleable screen-distortion system (settings → **Experimental
Rendering**). Comes with:

- Quality levels (Off / Low / Medium / High)
- Off-screen culling
- Auto-downgrade on low FPS
- Hard caps on concurrent effects, radius, and strength

Disabled by default. Recommended only for capable devices.

### Team

Adds a ninth team, **Aurora**, with a custom color palette and emoji.

---

## Installation

1. Download `ProjectShatteredStar.jar` from the [Releases](../../releases) page.
2. Put it in your Mindustry mods folder:
   - **Android**: `/Android/data/io.anuke.mindustry/files/mods/`
   - **Desktop**: `~/.local/share/Mindustry/mods/` (Linux) or
     `%APPDATA%/Mindustry/mods/` (Windows)
3. Restart the game.

---

## Building from source

Requires **Java 17** and **Gradle 8.10.2**.

```bash
./gradlew deploy