# 🛡 SecurePlots

A Fabric mod for Minecraft 1.21.1 that lets players protect areas of the world using special blocks. Includes a member permission system, groups, global flags, teleport, flight, and a full visual menu.

---

## 📋 Requirements

| Requirement | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.4 or higher |
| Fabric API | 0.116.9+1.21.1 or higher |
| Java | 21 |

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in the `mods/` folder.
3. Place the SecurePlots `.jar` file in the `mods/` folder as well.
4. Start the server or client. The config file is automatically generated at `config/secure_plots.json`.

---

## 🚀 Getting Started

1. Obtain a **Plot Block** (found in the functional block group of the creative inventory).
2. Place it in the world. That block becomes the center of your protection.
3. Use the **Plot Blueprint** (special item) or right-click the block to open the management menu.

---

## 🧱 Plot Blocks

There are 5 protection tiers. Each block defines the radius of the protected area (default values — all configurable in `secure_plots.json`):

| Block | Tier | Radius | Luminance | Hardness | Blast Resistance |
|---|---|---|---|---|---|
| Bronze Plot Block | Bronze | 15×15 blocks | 4 | 50 | 1200 |
| Gold Plot Block | Gold | 30×30 blocks | 5 | 50 | 1200 |
| Emerald Plot Block | Emerald | 50×50 blocks | 6 | 50 | 1200 |
| Diamond Plot Block | Diamond | 75×75 blocks | 7 | 50 | 1200 |
| Netherite Plot Block | Netherite | 100×100 blocks | 8 | 50 | 1200 |

---

## ⬆️ Tier Upgrades

To upgrade, open the plot menu and go to the **Upgrade** tab. Default costs:

| From → To | Materials |
|---|---|
| Bronze → Gold | 15 gold blocks |
| Gold → Emerald | 10 emerald blocks |
| Emerald → Diamond | 64 diamonds |
| Diamond → Netherite | 1 netherite block |

All upgrade costs are fully configurable in `secure_plots.json`.

---

## 🗂️ Management Menu

Opened with the **Plot Blueprint** inside your plot, or by right-clicking the block. It has 4 tabs:

### 📋 Info
- Plot name, owner, tier, size, and coordinates.
- Your current role.
- Teleport button (if public TP is enabled, or if you are owner/admin).
- Rename button (owner only).
- Inactivity indicator (if enabled in config).

### 👥 Members
- List of all members with their role and assigned groups.
- Click a member to edit their individual permissions.
- Shift+click to remove a member.
- Button to add new members (requires `MANAGE_MEMBERS` permission).

### 🌐 Global Permissions
- Toggles for all global permissions (flags) affecting every player in the plot.
- Groups section: create, edit, and delete permission groups.

### ⬆️ Upgrade
- Shows the current and next tier.
- Lists required materials with an indicator showing whether you have them.
- Upgrade button (automatically consumes the materials).

---

## 👤 Roles

| Role | Description |
|---|---|
| **OWNER** | Plot owner. All permissions. |
| **ADMIN** | Can manage members, permissions, build, interact, and use TP. |
| **MEMBER** | Can build, interact, open containers, and use TP. |
| **VISITOR** | Can only interact and enter (default for any player without access). |

Default permissions for each role are configurable in `secure_plots.json` under `roleDefaults`.

---

## 🔑 Individual Permissions

These permissions can be assigned per member or per group:

| Permission | Description |
|---|---|
| `BUILD` | Place and break blocks |
| `INTERACT` | Levers, doors, buttons |
| `CONTAINERS` | Open chests and inventories |
| `PVP` | Attack other players |
| `MANAGE_MEMBERS` | Add and remove members |
| `MANAGE_PERMS` | Change member permissions |
| `MANAGE_FLAGS` | Change global flags |
| `MANAGE_GROUPS` | Create and edit groups |
| `TP` | Use `/sp tp` to reach the plot |
| `FLY` | Fly inside the plot |
| `ENTER` | Enter the plot area |

---

## 🌐 Global Permissions (Flags)

Affect **all** players inside the plot, including visitors:

| Flag | Description |
|---|---|
| `ALLOW_VISITOR_BUILD` | Anyone can build |
| `ALLOW_VISITOR_INTERACT` | Anyone can interact |
| `ALLOW_VISITOR_CONTAINERS` | Anyone can open chests |
| `ALLOW_PVP` | PvP enabled for everyone |
| `ALLOW_FLY` | Everyone can fly in the plot |
| `ALLOW_TP` | Everyone can use `/sp tp` to this plot |
| `GREETINGS` | Show HUD message when entering/leaving |

The flags enabled by default on new plots are configurable in `secure_plots.json` under `defaultFlags`. Defaults: `ALLOW_TP` and `GREETINGS`.

---

## 👥 Permission Groups

Groups let you assign a set of permissions to multiple members at once. Created from the **Global Permissions** tab in the menu or with `/sp group create <name>`. Each group has its own permissions and member list.

---

## ✈️ Flight System

If the `ALLOW_FLY` flag is active on a plot, all players inside can fly. To give flight only to specific members, enable the `FLY` permission individually from the member permissions menu. Flight is automatically enabled on entry and revoked on exit (without affecting creative or already-flying players).

---

## 🗺️ Plot Blueprint

Special item for managing your plots:

- **Normal click** inside a plot: opens the management menu.
- **Normal click** outside a plot: opens a list of all your plots with TP options.
- **Shift+click**: shows the visual border of the nearest protection.

---

## 💬 Commands

All commands work with `/sp` or `/secureplots`.

### General

| Command | Description |
|---|---|
| `/sp list` | Lists all your protections with coordinates and tier. |
| `/sp info [plot]` | Shows detailed info about the plot you're standing in or the specified one. |
| `/sp view` | Shows the visual border of your nearest protection. |
| `/sp rename <name>` | Renames the plot you're standing in. |
| `/sp tp [plot]` | Teleports you to one of your plots or a public one. |

### Members

| Command | Description |
|---|---|
| `/sp add <player> <plot\|all>` | Adds a player as a member. |
| `/sp remove <player> <plot\|all>` | Removes a member. |

### Permissions & Flags

| Command | Description |
|---|---|
| `/sp flag` | Lists all available flags. |
| `/sp flag <flag>` | Shows the status of a flag in the current plot. |
| `/sp flag <flag> <true\|false> [plot]` | Enables or disables a flag. |
| `/sp perm` | Lists all available permissions. |
| `/sp perm <player> <perm>` | Shows whether a member has a permission. |
| `/sp perm <player> <perm> <true\|false> [plot]` | Changes an individual permission. |
| `/sp fly [true\|false] [plot]` | Enables/disables global flight + FLY permission for all members. |

### Groups

| Command | Description |
|---|---|
| `/sp group` | Lists groups in the current plot. |
| `/sp group create <name>` | Creates a permission group. |
| `/sp group delete <name>` | Deletes a group. |
| `/sp group addmember <group> <player>` | Adds a member to the group. |
| `/sp group removemember <group> <player>` | Removes a member from the group. |
| `/sp group setperm <group> <perm> <true\|false>` | Enables or disables a permission in the group. |

### The `<plot>` argument
You can pass the plot's **name**, its **number** from `/sp list`, or `all` to apply to all plots.

---

## ⚙️ Configuration

The file `config/secure_plots.json` is automatically generated with all default values. Below is the full config with every available option:

```json
{
  "maxPlotsPerPlayer": 3,
  "plotBuffer": 15,
  "adminTag": "plot_admin",
  "cobblescoinsItemId": "cobbleverse:cobblecoin",
  "blockedStructurePrefixes": [
    "cobbleverse:",
    "legendarymonuments:"
  ],
  "inactivityExpiry": {
    "enabled": false,
    "baseDays": 45,
    "daysPerTier": 5
  },
  "tiers": [
    { "tier": 0, "displayName": "Bronze",    "radius": 15,  "luminance": 4, "hardness": 50.0, "blastResistance": 1200.0 },
    { "tier": 1, "displayName": "Gold",      "radius": 30,  "luminance": 5, "hardness": 50.0, "blastResistance": 1200.0 },
    { "tier": 2, "displayName": "Emerald",   "radius": 50,  "luminance": 6, "hardness": 50.0, "blastResistance": 1200.0 },
    { "tier": 3, "displayName": "Diamond",   "radius": 75,  "luminance": 7, "hardness": 50.0, "blastResistance": 1200.0 },
    { "tier": 4, "displayName": "Netherite", "radius": 100, "luminance": 8, "hardness": 50.0, "blastResistance": 1200.0 }
  ],
  "upgradeCosts": [
    { "fromTier": 0, "toTier": 1, "cobblecoins": 0, "items": [{ "itemId": "minecraft:gold_block", "amount": 15 }] },
    { "fromTier": 1, "toTier": 2, "cobblecoins": 0, "items": [{ "itemId": "minecraft:emerald_block", "amount": 10 }] },
    { "fromTier": 2, "toTier": 3, "cobblecoins": 0, "items": [{ "itemId": "minecraft:diamond", "amount": 64 }] },
    { "fromTier": 3, "toTier": 4, "cobblecoins": 0, "items": [{ "itemId": "minecraft:netherite_block", "amount": 1 }] }
  ],
  "roleDefaults": {
    "admin":   ["BUILD", "INTERACT", "CONTAINERS", "PVP", "MANAGE_MEMBERS", "MANAGE_PERMS", "TP", "ENTER"],
    "member":  ["BUILD", "INTERACT", "CONTAINERS", "TP", "ENTER"],
    "visitor": ["INTERACT", "ENTER"]
  },
  "defaultFlags": ["ALLOW_TP", "GREETINGS"]
}
```

### Option Reference

| Option | Description |
|---|---|
| `maxPlotsPerPlayer` | Maximum plots per player. `0` = unlimited. |
| `plotBuffer` | Minimum block gap between plots to prevent overlap. |
| `adminTag` | Command tag that grants admin access to all plots. Assign with `/tag <player> add <value>`. |
| `cobblescoinsItemId` | Cobblecoins item ID for integration with the cobbleverse mod. |
| `blockedStructurePrefixes` | List of structure ID prefixes over which plots cannot be placed. |
| `inactivityExpiry.enabled` | If `true`, protections expire when the owner is inactive. |
| `inactivityExpiry.baseDays` | Base days of inactivity before a protection expires. |
| `inactivityExpiry.daysPerTier` | Extra grace days per upgrade tier. |
| `tiers[].displayName` | Tier name shown in menus and commands. |
| `tiers[].radius` | Plot radius in blocks (total area is radius×radius). |
| `tiers[].luminance` | Luminance of the plot block (0–15). |
| `tiers[].hardness` | Block hardness (mining time). |
| `tiers[].blastResistance` | Block blast resistance. |
| `upgradeCosts` | Upgrade costs per tier. Supports multiple items from any mod and cobblecoins. |
| `roleDefaults.admin` | Permissions automatically assigned when adding an ADMIN. |
| `roleDefaults.member` | Permissions automatically assigned when adding a MEMBER. |
| `roleDefaults.visitor` | Base permissions for any player with no assigned role. |
| `defaultFlags` | Global flags automatically enabled on every new plot. |

---

## 🔧 Admin Permissions

Server operators with the tag set in `adminTag` (default `plot_admin`, assignable with `/tag <player> add plot_admin`) have full access to all plots as if they were owners. They can manage members, permissions, flags, and groups of any plot.

The tag can be freely changed in `secure_plots.json` without recompiling the mod.

---

## 💾 Data

Plot data is saved as a `PersistentState` in the world (under `world/data/`). No external database required. Fully backwards compatible: plots saved with an older version of the mod load correctly, and any new config fields are automatically populated with default values.
