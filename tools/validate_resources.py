"""Offline validation for the production datapack and marker assets."""

from __future__ import annotations

import gzip
import json
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/dedicated_dungeons"
DATA = RESOURCES / "data/dedicated_dungeons"
REGISTRY = DATA / "dedicated_dungeons"

MARKERS = (
    "player_spawn_marker",
    "room_connector_marker",
    "spawner_marker",
    "loot_marker",
    "boss_spawn_marker",
    "exit_portal_marker",
)

REGISTRY_ROOTS = (
    "dungeons",
    "themes",
    "archetypes",
    "room_pools",
    "rooms",
    "connector_profiles",
    "mob_pools",
    "encounter_pools",
    "boss_pools",
    "bosses",
    "level_profiles",
    "loot_profiles",
    "mob_profiles",
)


def fail(message: str) -> None:
    raise ValueError(message)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"Invalid JSON {path.relative_to(ROOT)}: {error}")


def validate_json() -> None:
    for path in sorted(RESOURCES.rglob("*.json")):
        load_json(path)
    for root in REGISTRY_ROOTS:
        directory = REGISTRY / root
        if not directory.is_dir() or not next(directory.glob("*.json"), None):
            fail(f"Required datapack registry is empty: {directory.relative_to(ROOT)}")
    test_dungeon = REGISTRY / "dungeons/forgotten_depths.json"
    if not test_dungeon.is_file():
        fail("Missing bundled test dungeon definition: dedicated_dungeons:forgotten_depths")


def validate_png(path: Path) -> None:
    content = path.read_bytes()
    if content[:8] != b"\x89PNG\r\n\x1a\n" or len(content) < 24:
        fail(f"Invalid PNG: {path.relative_to(ROOT)}")
    width, height = struct.unpack(">II", content[16:24])
    if (width, height) != (16, 16):
        fail(f"Marker texture must be 16x16: {path.relative_to(ROOT)}")


def validate_marker_assets() -> None:
    for name in MARKERS:
        required = (
            ASSETS / f"textures/block/{name}.png",
            ASSETS / f"blockstates/{name}.json",
            ASSETS / f"models/block/{name}.json",
            ASSETS / f"models/item/{name}.json",
        )
        for path in required:
            if not path.is_file():
                fail(f"Missing marker asset: {path.relative_to(ROOT)}")
        validate_png(required[0])


def validate_structures() -> None:
    expected = {
        "entrance": ("player_spawn_marker", "room_connector_marker"),
        "hall": ("room_connector_marker", "spawner_marker", "loot_marker"),
        "boss_chamber": ("room_connector_marker", "boss_spawn_marker", "loot_marker", "exit_portal_marker"),
        "survival_arena": ("player_spawn_marker", "spawner_marker", "exit_portal_marker"),
    }
    for name, markers in expected.items():
        path = DATA / f"structure/test/{name}.nbt"
        if not path.is_file():
            fail(f"Missing test structure: {path.relative_to(ROOT)}")
        try:
            decoded = gzip.decompress(path.read_bytes())
        except (OSError, EOFError) as error:
            fail(f"Invalid gzip NBT {path.relative_to(ROOT)}: {error}")
        if not decoded.startswith(b"\x0a\x00\x00") or b"DataVersion" not in decoded:
            fail(f"Invalid structure NBT root: {path.relative_to(ROOT)}")
        for marker in markers:
            block_id = f"dedicated_dungeons:{marker}".encode()
            if block_id not in decoded:
                fail(f"Structure {name} does not contain {marker}")
        if "loot_marker" in markers and b"loot_role" not in decoded:
            fail(f"Structure {name} loot marker does not persist BlockEntity loot_role data")
        if "spawner_marker" in markers and b"spawn_role" not in decoded:
            fail(f"Structure {name} spawner marker does not persist BlockEntity spawn_role data")


def validate_room_references() -> None:
    rooms = {path.stem: load_json(path) for path in (REGISTRY / "rooms").glob("*.json")}
    for room_id, room in rooms.items():
        structure_id = room.get("structure")
        if not isinstance(structure_id, str) or ":" not in structure_id:
            fail(f"Room {room_id} has no valid structure id")
        namespace, value = structure_id.split(":", 1)
        structure = RESOURCES / f"data/{namespace}/structure/{value}.nbt"
        if not structure.is_file():
            fail(f"Room {room_id} references missing structure {structure_id}")
        tags = set(room.get("tags", []))
        markers = room.get("markers", {})
        if "entrance" in tags and not markers.get("player_spawn"):
            fail(f"Entrance room {room_id} needs player_spawn")
        if "boss" in tags:
            if len(markers.get("boss_spawn", [])) != 1:
                fail(f"Boss room {room_id} needs exactly one boss_spawn")
            if not markers.get("exit_portal"):
                fail(f"Boss room {room_id} needs exit_portal")


def main() -> int:
    try:
        validate_json()
        validate_marker_assets()
        validate_structures()
        validate_room_references()
    except (OSError, ValueError) as error:
        print(f"RESOURCE VALIDATION FAILED: {error}", file=sys.stderr)
        return 1
    print("Resource validation passed: JSON, bundled test dungeon, loot/mob profiles, 6 marker asset sets, 4 internal structure fixtures, and room references.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
