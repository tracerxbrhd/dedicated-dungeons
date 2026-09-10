![Dedicated Dungeons banner](src/main/resources/assets/dedicated_dungeons/branding/banner.png)

# Dedicated Dungeons

Dedicated Dungeons is a NeoForge framework for isolated, procedurally assembled Minecraft dungeon runs. It combines structure-authored rooms, managed U-API instances, party deployment, data-driven encounters and configurable loot.

- Minecraft 1.21.1
- NeoForge 21.1.234 or newer in the 21.1 line
- Java 21
- Requires [U-API](https://github.com/tracerxbrhd/u-api) 2.x

## Beta content

This beta does not include a handcrafted adventure or a production content pack. It ships only with **Forgotten Depths**, a small vanilla test dungeon assembled from the mod's internal structure fixtures. It exists to make the complete portal, generation, combat, reward and cleanup flow playable and to provide a reference for datapack authors.

The framework is the main product: modpacks and servers are expected to provide their own rooms, dungeon definitions, mob pools and loot profiles through datapacks.

## Features

- isolated dungeon instances with persisted recovery and cleanup;
- procedural room and connector assembly;
- solo and party-ready deployment;
- data-driven dungeons, rooms, mobs, bosses and loot;
- helper blocks for authoring structure NBT;
- guarded optional entries for supported modded entities;
- operator commands and debug tools for pack development.

Datapack entry points and validation commands are documented in [`docs/DATAPACKS.md`](docs/DATAPACKS.md). Release maintenance is documented in [`docs/RELEASING.md`](docs/RELEASING.md).

## License

Dedicated Dungeons source code is licensed under the [Mozilla Public License 2.0](LICENSE) (`MPL-2.0`). Modifications to MPL-covered source files remain subject to the MPL when distributed.

The Underworld Studio name, logos, branding, and other trademarks are not licensed by the MPL. Third-party software, Minecraft/NeoForge materials, and other third-party components remain subject to their respective licenses and terms.

Copyright © 2026 tracerxbrhd / Underworld Studio.
