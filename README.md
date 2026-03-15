<p align="center">
  <img src="https://raw.githubusercontent.com/TupacGodoy/Secure-Plots/main-optimization/src/main/resources/assets/secure-plots/icon.png" width="160" alt="Secure Plots"/>
</p>

# 🛡 Secure Plots

**Minecraft 1.21.1 — Fabric**  
Territory protection mod with menus, roles, upgrades, ambient effects, and a fully configurable visual border system.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/zhilius)

---

## Requirements

- Minecraft 1.21.1
- Fabric Loader ≥ 0.15.0
- Fabric API
- Java 21+

---

## Installation

1. Drop the `.jar` into your `mods/` folder (server + client).
2. Start the server once to generate the config files in `config/`.
3. Edit `secure_plots.json` (server behaviour) and `secure_plots_client.json` (visual settings).

---

## How it works

Place a **Plot Block** to claim that area. The block's tier determines how large the protected zone is. Right-click the block or use the **Plot Blueprint** item to manage it.

### Plot tiers (defaults, all configurable)

| Tier | Name | Radius |
|------|------|--------|
| 0 | Bronze | 15 × 15 |
| 1 | Gold | 30 × 30 |
| 2 | Emerald | 50 × 50 |
| 3 | Diamond | 75 × 75 |
| 4 | Netherite | 100 × 100 |

---

## Commands

All commands work as `/sp` or `/secureplots`.

| Command | Description |
|---------|-------------|
| `/sp help` | Show all commands |
| `/sp list` | List your plots |
| `/sp info [plot]` | Show info for the plot you're in, or a named plot |
| `/sp view` | Show the border of your nearest plot |
| `/sp rename <name>` | Rename the plot you're standing in |
| `/sp tp [plot]` | Teleport to a plot |
| `/sp add <player> <plot\|all>` | Add a member to a plot |
| `/sp remove <player> <plot\|all>` | Remove a member |
| `/sp flag` | List available flags |
| `/sp flag <flag> <true\|false> [plot]` | Set a global flag |
| `/sp perm` | List available permissions |
| `/sp perm <player> <perm> <true\|false> [plot]` | Set a permission for a member |
| `/sp fly [true\|false] [plot]` | Enable/disable fly for a plot |
| `/sp group` | List permission groups |
| `/sp group create <name>` | Create a group |
| `/sp group delete <name>` | Delete a group |
| `/sp group addmember <group> <player>` | Add a member to a group |
| `/sp group removemember <group> <player>` | Remove from a group |
| `/sp group setperm <group> <perm> <true\|false>` | Set a group permission |
| `/sp plot particle <type\|clear>` | Set ambient particles on entry |
| `/sp plot weather <clear\|rain\|thunder\|none>` | Override weather inside the plot |
| `/sp plot time <day\|noon\|night\|midnight\|<ticks>\|reset>` | Override time inside the plot |
| `/sp plot music <sound_id\|clear>` | Play music on entry |

---

## Roles & Permissions

### Roles

| Role | Description |
|------|-------------|
| `OWNER` | Full control, can break the block |
| `ADMIN` | Can manage members, permissions, flags |
| `MEMBER` | Default trusted player |
| `VISITOR` | Anyone not explicitly added |

### Global flags (`/sp flag`)

| Flag | Default | Description |
|------|---------|-------------|
| `ALLOW_VISITOR_BUILD` | off | Anyone can build |
| `ALLOW_VISITOR_INTERACT` | off | Anyone can use doors, levers, etc. |
| `ALLOW_VISITOR_CONTAINERS` | off | Anyone can open chests |
| `ALLOW_PVP` | off | PvP enabled for everyone |
| `ALLOW_FLY` | off | Everyone can fly |
| `ALLOW_TP` | on | Anyone can `/sp tp` here |
| `GREETINGS` | on | Show enter/exit title messages |

### Individual permissions (`/sp perm`)

`BUILD`, `BREAK`, `PLACE`, `INTERACT`, `CONTAINERS`, `USE_BEDS`, `USE_CRAFTING`, `USE_ENCHANTING`, `USE_ANVIL`, `USE_FURNACE`, `USE_BREWING`, `ATTACK_MOBS`, `ATTACK_ANIMALS`, `PVP`, `RIDE_ENTITIES`, `INTERACT_MOBS`, `LEASH_MOBS`, `SHEAR_MOBS`, `MILK_MOBS`, `CROP_TRAMPLING`, `PICKUP_ITEMS`, `DROP_ITEMS`, `BREAK_CROPS`, `PLANT_SEEDS`, `USE_BONEMEAL`, `BREAK_DECOR`, `DETONATE_TNT`, `GRIEFING`, `TP`, `FLY`, `ENTER`, `CHAT`, `COMMAND_USE`, `MANAGE_MEMBERS`, `MANAGE_PERMS`, `MANAGE_FLAGS`, `MANAGE_GROUPS`

---

## Server config — `secure_plots.json`

### Feature toggles

| Field | Default | Description |
|-------|---------|-------------|
| `enableProtection` | `true` | Master switch for block/interact protection |
| `enableFlyInPlots` | `true` | Allow fly grants via FLY permission |
| `enableEnterHud` | `true` | Show plot name in action bar on entry |
| `enableGreetingMessages` | `true` | Show enter/exit title messages (GREETINGS flag) |
| `enablePlotParticles` | `true` | Ambient particles on entry |
| `enablePlotMusic` | `true` | Music playback on entry |
| `enablePlotWeather` | `true` | Per-plot weather override |
| `enablePlotTime` | `true` | Per-plot time override |
| `enablePlotTeleport` | `true` | Allow `/sp tp` |
| `enableHologram` | `true` | Floating info display above plot blocks |
| `enablePvpControl` | `true` | Block PvP in plots without ALLOW_PVP flag |
| `enableUpgrades` | `true` | Allow tier upgrades |
| `enablePermissionGroups` | `true` | Allow creating permission groups |
| `plotBlocksUnbreakable` | `true` | Non-owners cannot break plot blocks at all |
| `allowNestedPlots` | `false` | Allow placing plots inside other plots |

### General

| Field | Default | Description |
|-------|---------|-------------|
| `maxPlotsPerPlayer` | `3` | Max plots per player (`0` = unlimited) |
| `plotBuffer` | `15` | Minimum block gap between plots |
| `adminOpLevel` | `2` | OP level required for admin actions |
| `adminTag` | `"plot_admin"` | Command tag granting admin override (`/tag <player> add plot_admin`) |
| `cobblescoinsItemId` | `"cobbleverse:cobblecoin"` | Item ID used as Cobblecoins currency in upgrade costs |
| `blockedStructurePrefixes` | `["cobbleverse:", "legendarymonuments:"]` | Structure ID prefixes that block plot placement |

### Inactivity expiry

```json
"inactivityExpiry": {
  "enabled": false,
  "baseDays": 45,
  "daysPerTier": 5
}
```

When enabled, a plot expires if the owner hasn't been seen for `baseDays + (tier × daysPerTier)` days.

### Ambient settings

| Field | Default | Description |
|-------|---------|-------------|
| `particleCount` | `3` | Burst particle count on plot entry (1–5) |
| `ambientParticleCount` | `2` | Continuous particles per interval (1–5) |
| `ambientInterval` | `20` | Ticks between ambient particle spawns |
| `checkInterval` | `10` | Ticks between enter/exit detection checks |
| `musicVolume` | `4.0` | Music volume (0.1–4.0) |

### Tier configuration

Each tier can be customized in the `tiers` array:

```json
"tiers": [
  { "tier": 0, "displayName": "Bronze",    "radius": 15,  "luminance": 4, "hardness": 50, "blastResistance": 1200 },
  { "tier": 1, "displayName": "Gold",      "radius": 30,  "luminance": 5, "hardness": 50, "blastResistance": 1200 },
  { "tier": 2, "displayName": "Emerald",   "radius": 50,  "luminance": 6, "hardness": 50, "blastResistance": 1200 },
  { "tier": 3, "displayName": "Diamond",   "radius": 75,  "luminance": 7, "hardness": 50, "blastResistance": 1200 },
  { "tier": 4, "displayName": "Netherite", "radius": 100, "luminance": 8, "hardness": 50, "blastResistance": 1200 }
]
```

### Upgrade costs

```json
"upgradeCosts": [
  { "fromTier": 0, "toTier": 1, "cobblecoins": 0, "items": [{ "itemId": "minecraft:gold_block", "amount": 15 }] },
  { "fromTier": 1, "toTier": 2, "cobblecoins": 0, "items": [{ "itemId": "minecraft:emerald_block", "amount": 10 }] },
  { "fromTier": 2, "toTier": 3, "cobblecoins": 0, "items": [{ "itemId": "minecraft:diamond", "amount": 64 }] },
  { "fromTier": 3, "toTier": 4, "cobblecoins": 0, "items": [{ "itemId": "minecraft:netherite_block", "amount": 1 }] }
]
```

Any mod item can be used as a cost. Set `cobblecoins > 0` and configure `cobblescoinsItemId` to require a custom currency.

### Role defaults

Default permissions granted automatically when adding a member by role:

```json
"roleDefaults": {
  "admin":   ["BUILD", "INTERACT", "CONTAINERS", "PVP", "MANAGE_MEMBERS", "MANAGE_PERMS", "TP", "ENTER"],
  "member":  ["BUILD", "INTERACT", "CONTAINERS", "TP", "ENTER"],
  "visitor": ["INTERACT", "ENTER"]
}
```

### Default flags for new plots

```json
"defaultFlags": ["ALLOW_TP", "GREETINGS"]
```

---

## Client visual config — `secure_plots_client.json`

This file is stored on the **server** and automatically synced to all clients on join. Edit it on the server — players receive the changes automatically.

### Plot border — thickness

| Field | Default | Description |
|-------|---------|-------------|
| `edgeThickness` | `0.06` | Core edge line width |
| `glowThickness` | `0.13` | Outer glow halo width |
| `scanlineThickness` | `0.025` | Vertical scanline width |
| `scanlineSpacing` | `1.5` | Blocks between scanlines |
| `borderHeight` | `25.0` | Height of the border wall in blocks |

### Plot border — animation

| Field | Default | Description |
|-------|---------|-------------|
| `pulseCycleMs` | `2000` | Duration of one brightness pulse cycle (ms) |
| `pulseMin` | `0.6` | Minimum brightness (0.0–1.0) |
| `pulseRange` | `0.4` | Brightness variation range (`pulseMin + pulseRange ≤ 1.0`) |
| `boltFlickerMs` | `120` | How often corner lightning bolts regenerate (ms) |

### Plot border — tier colors

Each tier has 9 color channels (all values 0.0–1.0):

```json
"tierColors": [
  {
    "tier": 0,
    "coreR": 1.0, "coreG": 0.55, "coreB": 0.05,
    "glowR": 0.7, "glowG": 0.28, "glowB": 0.0,
    "whiteR": 1.0, "whiteG": 0.88, "whiteB": 0.55
  }
]
```

| Channel group | Effect |
|---------------|--------|
| `coreR/G/B` | Main edge color |
| `glowR/G/B` | Outer halo color |
| `whiteR/G/B` | Inner bright core |

### Hologram

The floating info panel displayed above plot blocks.

| Field | Default | Description |
|-------|---------|-------------|
| `hologramEnabled` | `true` | Show/hide the hologram (mirrors `enableHologram` in server config) |
| `hologramHeight` | `3.0` | Blocks above the plot block |
| `hologramMaxDistance` | `24.0` | Max render distance in blocks |
| `hologramScale` | `0.025` | Text scale |
| `hologramBackgroundOpacity` | `0.75` | Panel background opacity (0.0–1.0) |
| `hologramPaddingX` | `8` | Horizontal padding in font pixels |
| `hologramPaddingY` | `6` | Vertical padding in font pixels |
| `hologramLineSpacing` | `1` | Extra pixels between lines |
| `hologramFadeInMs` | `400` | Fade-in duration (ms) |
| `hologramFadeOutMs` | `600` | Fade-out duration before expiry (ms) |
| `hologramFloat` | `true` | Enable floating up/down animation |
| `hologramFloatAmplitude` | `0.1` | Float range in blocks |
| `hologramFloatCycleMs` | `3000` | Duration of one float cycle (ms) |

#### Hologram text labels

All label strings are customizable:

| Field | Default |
|-------|---------|
| `hologramDefaultName` | `"PROTECTED PLOT"` |
| `hologramLabelOwner` | `"Owner:   "` |
| `hologramLabelTier` | `"Tier:    "` |
| `hologramLabelSize` | `"Size:    "` |
| `hologramLabelMembers` | `"Members: "` |
| `hologramLabelNext` | `"Next: "` |
| `hologramLabelMaxLevel` | `"§6§l★ Max Level ★"` |

### Plot management screen

| Field | Default | Description |
|-------|---------|-------------|
| `screenPanelWidth` | `320` | Panel width in pixels |
| `screenPanelHeight` | `240` | Panel height in pixels |
| `screenRowSpacing` | `16` | Pixels between info rows |
| `screenMaxNameLength` | `32` | Maximum plot name length |

#### Screen colors (ARGB hex)

| Field | Default |
|-------|---------|
| `screenColorBorderOuter` | `0xFF373737` |
| `screenColorBackground` | `0xFFC6C6C6` |
| `screenColorTitleBar` | `0xFF555555` |
| `screenColorTitleBarTop` | `0xFF666666` |
| `screenColorShadowDark` | `0xFF8B8B8B` |
| `screenColorShadowLight` | `0xFFFFFFFF` |

---

## Localisation

The mod supports full localisation via Minecraft's standard resource pack system. Two languages are bundled:

- `en_us` — English (default)
- `es_es` — Spanish (Rioplatense)

All 256 translation keys cover commands, UI, tooltips, HUD messages, permission names and descriptions, and every player-facing string. To add another language, create `assets/secure-plots/lang/<locale>.json` in a resource pack using the existing files as a template.

---

## Permissions summary for server admins

- Players need **no special permissions** to place plot blocks or use `/sp` commands on their own plots.
- Admin override: give the player the `plot_admin` command tag (`/tag <player> add plot_admin`) or grant OP level ≥ `adminOpLevel` (default 2).
- The Creative tab in the plot menu (gives plot blocks) requires OP or creative mode.

---

## Support

If Secure Plots is useful for your server, consider supporting development:

☕ **[ko-fi.com/zhilius](https://ko-fi.com/zhilius)**

---

## License

GPL-3.0 — Copyright (C) 2025 TupacGodoy
