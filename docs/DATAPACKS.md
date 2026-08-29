# Datapacks

Dedicated Dungeons reads its gameplay definitions from server datapacks. The bundled `dedicated_dungeons:forgotten_depths` dungeon is intentionally small test content; production packs should provide their own dungeon definition and structure-authored rooms.

## Layout

Definitions use the following paths under `data/<namespace>/dedicated_dungeons/`:

```text
dungeons/
level_profiles/
themes/
archetypes/
room_pools/
rooms/
connector_profiles/
mob_profiles/
mob_pools/
encounter_pools/
bosses/
boss_pools/
loot_profiles/
```

Minecraft structure NBT belongs under `data/<namespace>/structure/`. Loot tables use the Minecraft 1.21.1 singular path `data/<namespace>/loot_table/`.

## Minimal dungeon definition

```json
{
  "format_version": 1,
  "display_name": "dungeon.example.demo.name",
  "description": "dungeon.example.demo.description",
  "themes": ["example"],
  "tags": ["demo"],
  "archetype": "example:basic",
  "level_profile": "dedicated_dungeons:standard",
  "recommended_level": 10,
  "minimum_level": 0,
  "maximum_level": 100,
  "base_weight": 10,
  "pools": {
    "encounter_pool": "example:encounters",
    "boss_pool": "example:bosses",
    "reward_pool": "example:rewards"
  },
  "rules": {
    "minimum_rooms": 4,
    "maximum_rooms": 6,
    "minimum_depth": 2,
    "maximum_depth": 6,
    "branches": 0,
    "generation_attempts": 24,
    "clear_condition": "boss_and_encounters",
    "cleanup_delay_seconds": 30
  },
  "allowed_dimensions": ["minecraft:overworld"],
  "requirements": {
    "required_mods": [],
    "optional_mods": []
  }
}
```

Room definitions reference structure NBT and declare entrance, connector, encounter, loot, boss and exit markers. The corresponding helper blocks are available in the Dedicated Dungeons creative tab when debug tools are enabled. Save authored rooms with Minecraft structure blocks after configuring marker BlockEntity data.

## Validation

After installing or changing a datapack, run:

```text
/reload
/uapi dungeons validate
/uapi dungeons generate_for_level 10
/uapi dungeons generate <namespace:dungeon>
```

Reloads are atomic: invalid content is reported without replacing the last valid registry snapshot. Definitions may use `requirements.required_mods` for optional integrations; unavailable guarded entries are disabled instead of becoming hard dependencies.

The files under `src/main/resources/data/dedicated_dungeons/dedicated_dungeons/` are the authoritative bundled examples for the current format.
