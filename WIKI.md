# InvSee Mod Configuration Wiki (1.3.4+)

The `config.json` file (located in `config/invsee/config.json`) allows you to fully customize the 4 buttons located at the top right of the InvSee menu (Slots 5, 6, 7, and 8). 

You can change their order, modify their icons, add custom commands to them, remove them entirely, or create completely new custom buttons with your own items, text, and commands.

In version 1.3.1+, all default buttons have been converted to fully data-driven `custom` types, meaning you have 100% control over their appearance and behavior while still maintaining language file support.

## Default Configuration
When you first run the server, the mod generates the following configuration for the default buttons:
```json
{
  language: en_us,
  top_row_buttons: [
    {
      type: custom,
      item: minecraft:golden_apple,
      name:  ,
      lore: [
        §c{lang:health}: {health}/{maxhealth},
        §6{lang:food}: {food}/20,
        {effects},
        {lastseen}
      ],
      command: #status
    },
    {
      type: custom,
      item: minecraft:paper,
      name: §b{lang:location}: {x} {y} {z},
      lore: [
        §7{lang:dimension}: {dimension}
      ],
      command: #tp
    },
    {
      type: custom,
      item: minecraft:ender_chest,
      name: §6{lang:open_ender_chest},
      lore: [],
      command: #enderchest
    },
    {
      type: custom,
      item: minecraft:experience_bottle,
      name: §e{lang:xp_level}: {xplevel},
      lore: [],
      command: #xp
    }
  ]
}
```
*Note: The `language` option determines the translation file used for `{lang:key}` placeholders (like `en_us` or `tr_tr`).*

---

## Top Row Buttons

Used for adding buttons to 4 empty slots at the top right corner.

`type`
`item`
`title`
`lore`
`command`


## Button Types

### 1. Custom Buttons (`type: custom`)
The `custom` type is the most powerful button type. It allows you to specify the exact item, name, lore, and command to execute. The default configuration uses this type for all buttons.



### 2. Empty Button (`type: empty`)
If you want to leave a slot completely empty and unclickable, use the `empty` type:
```json
{
  type: empty
}
```
Buttons that are set to empty but 'command' variable will be working even tho the slot is empty

---

## Placeholders (Variables)
When creating a `custom` button or assigning a `command` to any button, you can use the following placeholders. These will be automatically replaced with the correct real-time values for both online and offline players:

### Dynamic Values
* `{player}` - The name of the player whose inventory is being viewed.
* `{x}`, `{y}`, `{z}` - The exact X, Y, and Z coordinates of the player.
* `{dimension}` - The dimension the player is in (e.g., `overworld`, `the_nether`, `the_end`).
* `{health}` - The current health of the player (e.g., `20.0`).
* `{maxhealth}` - The maximum health of the player (online players only; shows last saved value for offline).
* `{food}` - The current food/hunger level of the player (0-20).
* `{xplevel}` - The current XP level of the player.
* `{effects}` - A **special** placeholder that expands into multiple lore lines listing the player's active potion effects. Must be placed as its own lore line.
* `{lastseen}` - A **special** placeholder that expands into a Last Seen: X ago lore line for offline players. For online players, this line is omitted. Must be placed as its own lore line.

> **Note:** `{effects}` and `{lastseen}` are special block placeholders — they must be on their own line in the `lore` array and will expand into one or more lines automatically.

### Translation Values (Language Support)
To prevent your config file from breaking multi-language support (e.g. English vs Turkish), you can use the `{lang:<key>}` placeholder. This will pull the translation directly from your active language file!
* `{lang:health}` -> Health (English) / Can (Turkish)
* `{lang:food}` -> Food (English) / Açlık (Turkish)
* `{lang:location}` -> Location (English) / Konum (Turkish)
* `{lang:dimension}` -> Dimension (English) / Boyut (Turkish)
* `{lang:xp_level}` -> XP Level (English) / XP Seviyesi (Turkish)
* `{lang:open_ender_chest}` -> Open Ender Chest (English) / Ender Sandığını Aç (Turkish)

---

## Internal Java Commands (Actions)
Instead of running console commands, you can attach the mod's complex internal Java logic to any button using the following `#` prefixed commands:

* `#status` - A dummy command that does nothing (useful for buttons that are strictly informational).
* `#tp` - Safely prompts the viewer in chat to teleport to the player's exact location.
* `#enderchest` - Opens the custom GUI for the player's Ender Chest (works safely for offline NBT).
* `#xp` - Drains the target's XP and transfers it directly to the viewer's XP bar.

---

## Customizing Language Files

If you want to modify the built-in translation texts (like Health, Food, Location, etc.), you don't need to change the Java code! You can simply create your own translation file.

1. Go to your server's `config/` directory.
2. Open or create the `invsee/lang/` folder.
3. Create a `.json` file that matches your `language` setting in `config.json` (e.g., `en_us.json`, `tr_tr.json`, `de_de.json`).

**Example: `config/invsee/lang/en_us.json`**
```json
{
  health: HP,
  food: Hunger,
  effects: Active Potions,
  effects_none: Clean,
  last_seen: Offline Since,
  days_ago: %d days ago,
  hours_ago: %d hours ago,
  minutes_ago: %d minutes ago,
  just_now: just now,
  click_teleport: Click here to teleport!,
  ender_chest: %s'\''s Ender Chest,
  offline_inv: %s (Offline),
  no_player_data: Player does not exist!,
  no_other_players: No one else is online!,
  player_list: Player List,
  cannot_self: You can'\''t open your own inventory this way!,
  stolen_xp: §aYou took %d XP!,
  player_inventory: %s'\''s Backpack,
  player_not_found: Invalid player name!,
  location: Loc,
  dimension: Dim,
  save_aborted_player_online: Error: Player joined the game!,
  xp_level: XP Lvl,
  open_ender_chest: View Ender Chest,
  failed_load_data: Corrupted player data!,
  prev_page: Back,
  next_page: Next,
  online_players: Online,
  offline_players: Offline,
  page_info: Pg %d/%d,
  click_view_offline: Show Offline,
  click_view_online: Show Online,
  status_online: Online
}
```

Once the file is created, any changes you make will immediately reflect in the `{lang:key}` placeholders without requiring a server restart (just close and reopen the InvSee menu)!

---

## Client-Side Configuration

Each player (client) can configure their own language preference via the client config file located at:

`config/invsee/client.json`

```json
{
  preferredLanguage: auto
}
```

* `auto` (default) — The mod automatically detects and uses the Minecraft client'\''s currently selected language.
* Any language code (e.g. `en_us`, `tr_tr`) — Forces a specific language for button text displayed in the InvSee GUI.

> **Note:** The client language determines what language the **button names and lore** are displayed in (via `{lang:key}` placeholders). The server language (`config.json`) is used as a fallback when a key is missing from the client'\''s language file.

---

## Executing Server Commands
You can also assign standard console commands to any button! Commands are executed directly by the server console, meaning the viewer does not need specific permissions to run them. Also custom (nonvanilla) command are supported too.

For example, lets assume you have a mod which adds /heal command and create a button for healing:
```json
{
  type: custom,
  item: minecraft:golden_apple,
  name: §eHeal Player,
  lore: [
    §7Current Health: §c{health}/{maxhealth},
    ",
 §aClick to fully heal {player}!
 ],
 command: heal {player}
}
```

---

## How It Works (Technical Overview)

### Command: `/invsee`
Running `/invsee` without arguments opens the **Player List GUI** — a paginated 3-row chest menu showing all online and offline players. Clicking a player head opens their inventory.

Running `/invsee <player>` directly opens that player's inventory screen.

Running `/invsee reload` allows you to reload configurations without restarting the game:
* `/invsee reload server` — Reloads the server's `config.json` and language files. (Requires OP level 2)
* `/invsee reload client` — Reloads your local `client.json` and syncs your language preference to the server. (Available to all players who have the mod installed)
* `/invsee reload` — If you have OP permissions, this acts as a smart shortcut and reloads BOTH server and your client configurations simultaneously. If you are not OP, it only reloads your client configuration.

**Permissions:** Base commands and `server` reloads require operator-level 2 permission (`gamemaster`). `client` reloads require no OP permissions but do require the mod to be installed on the client.

### Online Player Inventory
When viewing an online player'\''s inventory, changes are applied **live** to the player'\''s actual inventory in real-time. The mod creates a live wrapper over the `ServerPlayer`'\''s inventory, so any item you move or remove is immediately reflected on the target.

### Offline Player Inventory
When viewing an offline player:
1. The mod reads the player'\''s `.dat` file from `world/playerdata/`.
2. It loads their full inventory (including armor and offhand) into a temporary container.
3. When the GUI is closed, the mod **re-reads** the latest `.dat` file and writes the changes back atomically (write to a temp file, then rename).
4. If the player **comes online** while you have their inventory open, saving is **aborted** and you receive a warning message.

### Armor Slot Mapping
The 45-slot (9x5) chest GUI maps slots as follows:

| GUI Slot | Content |
|----------|---------|
| 0 | Helmet |
| 1 | Chestplate |
| 2 | Leggings |
| 3 | Boots |
| 4 | Off-hand item |
| 5-8 | Top-row buttons (read-only) |
| 9-35 | Main inventory (slots 9-35) |
| 36-44 | Hotbar (slots 0-8) |

### Ender Chest
The ender chest button opens a separate 3-row chest GUI. For online players, it directly accesses the live ender chest inventory. For offline players, it reads/writes the `EnderItems` NBT tag in the player'\''s `.dat` file using the same atomic save mechanism as the main inventory.

### XP Transfer (`#xp`)
Clicking the XP button drains **all** of the target player'\''s experience and gives it to the viewer. For online players, the XP is transferred live. For offline players, the `XpTotal`, `XpLevel`, and `XpP` NBT fields are read and zeroed in the `.dat` file.

### Teleport (`#tp`)
Clicking the teleport button closes the GUI and sends a chat message to the viewer with a **clickable command** (`/execute in <dimension> run tp @s <x> <y> <z>`). This is a suggestion, not an automatic teleport — the viewer can modify the command before running it.

### Language System
The mod uses a two-tier language system:
1. **Client language** — sent from the client to the server on join via a custom network packet. Used for `{lang:key}` rendering in button names/lore.
2. **Server language** — set in `config.json`. Used as the default for server-side messages (command errors, chat messages).

Fallback order: **Client lang → Server lang → `en_us`**
