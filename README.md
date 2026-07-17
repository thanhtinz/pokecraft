# SurvivalCore

An all-in-one survival server core for **Paper 1.21 (MC 26.x)**, Java 21, and
**Geyser/Bedrock** players. Menus are kept to a minimum on purpose: most systems
are **roleplay-first** (NPCs and physical blocks you interact with in the world),
and only shop-style things (auction, wallet, admin) use chest menus that Geyser
translates for Bedrock.

## Features

| System | How you use it |
| --- | --- |
| **Economy** (Vault) | `/balance`, `/pay`, `/baltop`, `/eco` (admin). Starting balance configurable. |
| **Homes / Warps / Spawn** | `/sethome`, `/home`, `/delhome`, `/homes`, `/warp`, `/warps`, `/setwarp` (admin), `/spawn`, `/setspawn` (admin). |
| **Teleport** | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back`, `/rtp` (random wild). Warmup + cooldown, cancelled on move/damage. |
| **Land claims** | `/claim`, `/unclaim`, `/trust`, `/untrust`, `/claiminfo`, `/claims`. Chunk-based — claim the chunk you stand in. |
| **Auction house** | `/ah` (chest menu — it's a shop), `/sell <price>`, `/ah mine`. |
| **Crates** | *Physical.* Hold a key and right-click a bound crate block. Fireworks + reward drop, no menu. |
| **Vaults** | *Physical.* Right-click a bound vault block to open your personal storage. |
| **Kits** | *NPC.* Right-click a **Kit Master** NPC (or `/kit`, `/kit <name>`). Clickable chat, cooldowns, permissions. |
| **Rank ladder** | `/rank`, `/rankup` — pay to climb the ladder; rank drives chat/tab prefixes. |
| **Jobs / Skills RPG** | *NPC.* Right-click a **Job Board** NPC (or `/jobs`). Mine/farm/chop/hunt/fish for money + XP; levels raise pay. |
| **Daily / Vote / Bounty** | `/daily` (streak rewards), `/vote` + `svote <player>` (vote hook), `/bounty <player> <amount>`, `/bounty list`. |
| **Chat / Scoreboard / Tab** | Rank-aware chat format, live sidebar, tab header/footer — all configurable. |
| **PlaceholderAPI** | `%survivalcore_balance%`, `_rank`, `_bounty`, `_claims`, `_jobs` (when PlaceholderAPI is installed). |

## Admin setup for the roleplay systems

These use in-world objects instead of menus, so an admin places them once:

- **NPCs** — stand where you want it and run `/npc create <role> [name]`.
  Roles: `kit_master`, `job_board`, `banker`. Remove with `/npc remove` (stand
  next to it), list with `/npc list`. NPCs are frozen, invulnerable villagers
  re-spawned from the database on every start.
- **Crate blocks** — look at a block and run `/crate setblock <crate>`. Give
  players keys with `/crate givekey <player> <crate> <amount>`. Unbind with
  `/crate delblock`.
- **Vault blocks** — look at a block and run `/vault setblock`. Unbind with
  `/vault delblock`.

## Voting

Any vote system (NuVotifier, VotingPlugin, …) can be pointed at the console
command `svote <player>` to grant the configured vote reward (money + a crate
key + broadcast). No hard dependency required.

## Configuration

Everything lives in `config.yml`: economy, teleport/rtp, claims, auction,
`crates`, `vaults`, `kits`, `ranks`, `jobs`, `daily`, `vote`, `bounty`, `chat`,
`scoreboard`, and `tab`. Reward weights are relative (they don't need to sum to
100). Data is stored in a single SQLite file (`survivalcore.db`).

## Building

```
./gradlew build
```

The shaded jar lands in `build/libs/SurvivalCore.jar`. Drop it in `plugins/`.
Soft dependencies: Vault, PlaceholderAPI, floodgate, Geyser-Spigot.
