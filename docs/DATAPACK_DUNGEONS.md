# Наборы тем и комнат для Dedicated Dungeons

Новый данж можно добавить обычным datapack или ресурсами другого мода. Java-код и регистрация через API не требуются. Все идентификаторы используют namespace автора, поэтому несколько наборов могут работать одновременно.

## Быстрый старт

Структура минимального datapack:

```text
my-dungeons/
├─ pack.mcmeta
└─ data/my_pack/
   ├─ dungeon_themes/crypt.json
   ├─ dungeon_archetypes/crypt.json
   ├─ dungeon_room_pools/crypt.json
   ├─ dungeon_rooms/
   │  ├─ entrance.json
   │  ├─ room.json
   │  ├─ boss.json
   │  └─ cache.json
   ├─ dungeon_connectors/straight.json
   ├─ dungeon_bosses/necromancer.json
   └─ structure/crypt/room.nbt       # необязательно
```

Для Minecraft 1.21.1 можно использовать:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Dedicated Dungeons theme"
  }
}
```

Если поле `structure` отсутствует, мод сам создаёт простую комнату из указанной палитры. Это позволяет проверить весь набор до постройки NBT-структур.

## Как связаны файлы

```text
theme
  └─ archetype
       ├─ room_pool ── rooms
       ├─ connectors
       └─ bosses ── reward_pool
```

Комнаты собираются в главный путь `entrance → normal... → boss`. Свободные точки подключения могут получить ветки `dead_end`. До изменения мира планировщик пробует повороты, проверяет пересечения, лимит деталей, максимальный диаметр и границы изолированного слота.

## Общие правила координат

- `bounds.min` и `bounds.max` — включительные локальные границы детали.
- `connectors[].pos` и все `markers` задаются в тех же локальных координатах.
- `facing` — направление наружу: `north`, `south`, `west` или `east`.
- Соединяются только точки с одинаковым `type`; базовое значение — `default`.
- Точка подключения должна находиться на внешней стене комнаты.
- Точка появления игрока и босса должна иметь два свободных блока по высоте.
- Планировщик поворачивает детали на 0°, 90°, 180° или 270°.

Для NBT-структуры локальный ноль структуры должен совпадать с локальным нулём JSON. Файл `data/my_pack/structure/crypt/room.nbt` имеет id `my_pack:crypt/room`. Структуру удобно сохранить structure block'ом, затем перенести полученный NBT в datapack.

## Тема

`data/my_pack/dungeon_themes/crypt.json`:

```json
{
  "translation_key": "dungeon_theme.my_pack.crypt",
  "archetypes": [
    { "archetype": "my_pack:crypt", "weight": 10 }
  ],
  "required_mods": ["example_magic"],
  "fallback": "dedicated_dungeons:underworld"
}
```

`required_mods` и `fallback` необязательны. Случайные мировые порталы автоматически учитывают доступные темы. Порталы от ключа и обычной команды по умолчанию используют встроенную тему; конкретный архетип можно проверить командой.

## Архетип

```json
{
  "theme": "my_pack:crypt",
  "room_pool": "my_pack:crypt",
  "connectors": [
    { "connector": "my_pack:straight", "weight": 4 }
  ],
  "bosses": [
    { "boss": "my_pack:necromancer", "weight": 1 }
  ],
  "main_path": { "min": 3, "max": 6 },
  "dead_end_chance": 0.3,
  "difficulties": ["C", "B", "A", "S"],
  "required_mods": ["example_magic"],
  "fallback": "dedicated_dungeons:basic"
}
```

`main_path` задаёт количество обычных комнат между входом и боссом. `dead_end_chance` находится в диапазоне 0–1. Ранги: `E`, `D`, `C`, `B`, `A`, `S`, `ANOMALY`.

## Пул комнат

```json
{
  "theme": "my_pack:crypt",
  "rooms": [
    { "room": "my_pack:entrance", "weight": 1 },
    { "room": "my_pack:room", "weight": 8 },
    { "room": "my_pack:boss", "weight": 1 },
    { "room": "my_pack:cache", "weight": 2 }
  ],
  "fallback": "dedicated_dungeons:basic"
}
```

Вес в пуле умножается на собственный `weight` комнаты.

## Комната

```json
{
  "theme": "my_pack:crypt",
  "structure": "my_pack:crypt/room",
  "size": "medium",
  "bounds": { "min": [0, 0, 0], "max": [10, 6, 10] },
  "connectors": [
    { "name": "west", "pos": [0, 1, 5], "facing": "west", "type": "default" },
    { "name": "east", "pos": [10, 1, 5], "facing": "east", "type": "default" }
  ],
  "markers": {
    "mob_spawn": [[5, 1, 5]],
    "loot_spawn": [[5, 1, 8]]
  },
  "tags": ["normal"],
  "weight": 3,
  "palette": {
    "floor": "minecraft:deepslate_tiles",
    "wall": "minecraft:polished_blackstone_bricks",
    "accent": "minecraft:crying_obsidian"
  },
  "required_mods": ["example_magic"]
}
```

Допустимые размеры: `small`, `medium`, `large`, `boss`, `arena`. Обязательные роли набора задаются тегами:

- `entrance` — хотя бы одна входная комната; желательно добавить `player_spawn`;
- `normal` — хотя бы одна комната главного пути;
- `boss` — хотя бы одна комната с размером, разрешённым выбранному боссу; желательно добавить `boss_spawn` и `exit`;
- `dead_end` — необязательная тупиковая комната;
- остальные теги (`loot`, `puzzle` и т. п.) свободны для соглашений аддонов.

Маркеры: `player_spawn`, `boss_spawn`, `mob_spawn`, `loot_spawn`, `exit`, `return_portal`.

## Соединительная деталь

```json
{
  "structure": "my_pack:crypt/straight",
  "bounds": { "min": [0, 0, 0], "max": [6, 4, 4] },
  "connectors": [
    { "name": "west", "pos": [0, 1, 2], "facing": "west", "type": "default" },
    { "name": "east", "pos": [6, 1, 2], "facing": "east", "type": "default" }
  ],
  "weight": 2,
  "palette": {
    "floor": "minecraft:deepslate_tiles",
    "wall": "minecraft:polished_blackstone_bricks",
    "accent": "minecraft:crying_obsidian"
  }
}
```

У соединительной детали должно быть ровно две точки. Поворотный коридор задаётся двумя точками на соседних сторонах.

## Босс

```json
{
  "entity": "example_magic:necromancer",
  "room_sizes": ["large", "boss"],
  "base_health": 160.0,
  "base_damage": 12.0,
  "reward_pool": "my_pack:crypt",
  "weight": 1,
  "required_mods": ["example_magic"]
}
```

Здоровье и урон дополнительно умножаются на настройки ранга из `underworldapi-server.toml`. Reward pool использует существующий формат `data/<namespace>/underworld/reward_pools/*.json`.

## Optional-интеграции и fallback

- Определение с отсутствующим модом из `required_mods` не выбирается.
- Недоступные комнаты, соединители и боссы удаляются из соответствующих пулов выбора.
- `theme`, `archetype` и `room_pool` могут ссылаться на `fallback`.
- Циклы fallback и отсутствующие ссылки считаются ошибками валидации.
- Если после фильтрации корректный план собрать невозможно, запускается встроенная legacy-арена. Портал и сохранение мира не ломаются.

## Проверка в игре

После `/reload` выполните:

```text
/dungeon validate_data
/dungeon portal C my_pack:crypt
/dungeon start <player> C my_pack:crypt
/dungeon list
/dungeon tp <instance-uuid>
/dungeon start_survival C
/dungeon spawn_portal survival C
```

`validate_data` показывает количество загруженных файлов и первые ошибки/предупреждения. Полный список также попадает в `latest.log`. Исправьте все `ERROR`; `WARNING` об отсутствующем optional-моде допустим.

Лимиты генерации и защита настраиваются в секции `instances` файла `config/dedicated_dungeons.toml`.
