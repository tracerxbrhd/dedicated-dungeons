"""Generate deterministic vanilla structure NBT for the built-in test dungeon."""

from __future__ import annotations

import gzip
import io
import struct
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/data/dedicated_dungeons/structure/test"
DATA_VERSION = 3955  # Minecraft 1.21.1


def utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + utf(name) + payload


def int_payload(value: int) -> bytes:
    return struct.pack(">i", value)

def long_payload(value: int) -> bytes:
    return struct.pack(">q", value)

def byte_payload(value: bool) -> bytes:
    return bytes((1 if value else 0,))


def string_payload(value: str) -> bytes:
    return utf(value)


def list_payload(tag_type: int, values: list[bytes]) -> bytes:
    return bytes((tag_type,)) + int_payload(len(values)) + b"".join(values)


def compound_payload(entries: list[bytes]) -> bytes:
    return b"".join(entries) + b"\x00"


@dataclass(frozen=True)
class BlockState:
    name: str
    facing: str | None = None

    def nbt(self) -> bytes:
        entries = [tag(8, "Name", string_payload(self.name))]
        if self.facing is not None:
            properties = compound_payload([tag(8, "facing", string_payload(self.facing))])
            entries.append(tag(10, "Properties", properties))
        return compound_payload(entries)


@dataclass(frozen=True)
class Room:
    name: str
    size: tuple[int, int, int]
    floor: str
    wall: str
    accent: str
    markers: tuple[tuple[tuple[int, int, int], str, str | None], ...]
    loot_data: dict[tuple[int, int, int], dict[str, str | int | bool]] | None = None
    spawn_data: dict[tuple[int, int, int], dict[str, str | int | bool]] | None = None


ROOMS = (
    Room(
        "entrance",
        (13, 7, 13),
        "minecraft:deepslate_tiles",
        "minecraft:polished_blackstone_bricks",
        "minecraft:crying_obsidian",
        (
            ((0, 1, 6), "room_connector_marker", "west"),
            ((12, 1, 6), "room_connector_marker", "east"),
            ((6, 1, 0), "room_connector_marker", "north"),
            ((6, 1, 6), "player_spawn_marker", "east"),
            ((6, 1, 8), "player_spawn_marker", "east"),
        ),
    ),
    Room(
        "hall",
        (11, 6, 9),
        "minecraft:deepslate_tiles",
        "minecraft:polished_blackstone_bricks",
        "minecraft:cracked_polished_blackstone_bricks",
        (
            ((0, 1, 4), "room_connector_marker", "west"),
            ((10, 1, 4), "room_connector_marker", "east"),
            ((5, 1, 0), "room_connector_marker", "north"),
            ((5, 1, 4), "spawner_marker", "north"),
            ((5, 1, 6), "loot_marker", "north"),
        ),
        {
            (5, 1, 6): {
                "loot_role": "COMMON",
                "loot_profile": "dedicated_dungeons:forgotten_depths",
                "container_type": "CHEST",
            }
        },
        {
            (5, 1, 4): {
                "spawn_role": "COMMON",
                "spawn_profile": "dedicated_dungeons:default",
                "spawn_mode": "DIRECT",
            }
        },
    ),
    Room(
        "boss_chamber",
        (15, 8, 15),
        "minecraft:polished_deepslate",
        "minecraft:reinforced_deepslate",
        "minecraft:crying_obsidian",
        (
            ((0, 1, 7), "room_connector_marker", "west"),
            ((7, 1, 7), "boss_spawn_marker", "west"),
            ((7, 1, 11), "loot_marker", "north"),
            ((12, 1, 7), "exit_portal_marker", "east"),
        ),
        {
            (7, 1, 11): {
                "loot_role": "BOSS",
                "container_type": "TRAPPED_CHEST",
            }
        },
    ),
    Room(
        "survival_arena",
        (17, 7, 17),
        "minecraft:polished_deepslate",
        "minecraft:polished_blackstone_bricks",
        "minecraft:crying_obsidian",
        (
            ((8, 1, 3), "player_spawn_marker", "south"),
            ((8, 1, 8), "spawner_marker", "north"),
            ((8, 1, 14), "exit_portal_marker", "north"),
        ),
        None,
        {
            (8, 1, 8): {
                "spawn_role": "COMMON",
                "spawn_profile": "dedicated_dungeons:default",
                "spawn_mode": "DIRECT",
            }
        },
    ),
)


def room_blocks(room: Room) -> dict[tuple[int, int, int], BlockState]:
    width, height, depth = room.size
    result: dict[tuple[int, int, int], BlockState] = {}
    for x in range(width):
        for z in range(depth):
            result[(x, 0, z)] = BlockState(room.floor)
            result[(x, height - 1, z)] = BlockState(room.wall)
    for y in range(1, height - 1):
        for x in range(width):
            result[(x, y, 0)] = BlockState(room.wall)
            result[(x, y, depth - 1)] = BlockState(room.wall)
        for z in range(1, depth - 1):
            result[(0, y, z)] = BlockState(room.wall)
            result[(width - 1, y, z)] = BlockState(room.wall)
    for x, z in ((0, 0), (0, depth - 1), (width - 1, 0), (width - 1, depth - 1)):
        for y in range(height):
            result[(x, y, z)] = BlockState(room.accent)
    for position, marker, facing in room.markers:
        result[position] = BlockState(f"dedicated_dungeons:{marker}", facing)
    return result


def structure_nbt(room: Room) -> bytes:
    blocks = room_blocks(room)
    palette = sorted(set(blocks.values()), key=lambda state: (state.name, state.facing or ""))
    indices = {state: index for index, state in enumerate(palette)}
    block_entries = []
    for position, state in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        entries = [
            tag(9, "pos", list_payload(3, [int_payload(value) for value in position])),
            tag(3, "state", int_payload(indices[state])),
        ]
        marker_data = (room.loot_data or {}).get(position)
        block_entity_id = "dedicated_dungeons:loot_marker"
        if marker_data is None:
            marker_data = (room.spawn_data or {}).get(position)
            block_entity_id = "dedicated_dungeons:spawner_marker"
        if marker_data is not None:
            block_entity = [
                tag(8, "id", string_payload(block_entity_id)),
                tag(3, "x", int_payload(position[0])),
                tag(3, "y", int_payload(position[1])),
                tag(3, "z", int_payload(position[2])),
            ]
            for key, value in marker_data.items():
                if isinstance(value, bool):
                    block_entity.append(tag(1, key, byte_payload(value)))
                elif isinstance(value, int):
                    block_entity.append(tag(4, key, long_payload(value)))
                else:
                    block_entity.append(tag(8, key, string_payload(value)))
            entries.append(tag(10, "nbt", compound_payload(block_entity)))
        block_entries.append(
            compound_payload(entries)
        )
    root = compound_payload(
        [
            tag(3, "DataVersion", int_payload(DATA_VERSION)),
            tag(9, "size", list_payload(3, [int_payload(value) for value in room.size])),
            tag(9, "palette", list_payload(10, [state.nbt() for state in palette])),
            tag(9, "blocks", list_payload(10, block_entries)),
            tag(9, "entities", list_payload(10, [])),
        ]
    )
    return tag(10, "", root)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for room in ROOMS:
        buffer = io.BytesIO()
        with gzip.GzipFile(filename="", mode="wb", fileobj=buffer, mtime=0) as compressed:
            compressed.write(structure_nbt(room))
        (OUTPUT / f"{room.name}.nbt").write_bytes(buffer.getvalue())
    print(f"Generated {len(ROOMS)} structure files in {OUTPUT}")


if __name__ == "__main__":
    main()
