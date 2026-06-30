# Datapack, расширяющий комнаты Dedicated Dungeons

Datapack может добавить целую тему, отдельные комнаты или заменить встроенную Survival Arena без Java-кода и обязательных зависимостей.

## 1. Структура pack

```text
my_dungeons/
├─ pack.mcmeta
└─ data/my_pack/
   ├─ structure/crypt/hall.nbt
   ├─ dungeon_themes/crypt.json
   ├─ dungeon_archetypes/crypt.json
   ├─ dungeon_room_pools/crypt.json
   ├─ dungeon_rooms/{entrance,hall,boss,arena}.json
   ├─ dungeon_connectors/straight.json
   ├─ dungeon_bosses/necromancer.json
   └─ underworld/reward_pools/crypt.json
```

`pack.mcmeta` для Minecraft 1.21.1:

```json
{"pack":{"pack_format":48,"description":"My Dedicated Dungeons pack"}}
```

Используйте собственный lowercase namespace (`my_pack`), не `minecraft` и не `dedicated_dungeons`, если вы не заменяете встроенное определение намеренно.

## 2. Минимальная цепочка definitions

Theme:

```json
{
  "translation_key": "dungeon_theme.my_pack.crypt",
  "archetypes": [{"archetype":"my_pack:crypt","weight":10}],
  "fallback": "dedicated_dungeons:underworld"
}
```

Archetype:

```json
{
  "theme": "my_pack:crypt",
  "room_pool": "my_pack:crypt",
  "connectors": [{"connector":"my_pack:straight","weight":4}],
  "bosses": [{"boss":"my_pack:necromancer","weight":1}],
  "main_path": {"min":3,"max":6},
  "dead_end_chance": 0.3,
  "difficulties": ["E","D","C","B","A","S","ANOMALY"],
  "fallback": "dedicated_dungeons:basic"
}
```

Room pool:

```json
{
  "theme":"my_pack:crypt",
  "rooms":[
    {"room":"my_pack:entrance","weight":1},
    {"room":"my_pack:hall","weight":8},
    {"room":"my_pack:boss","weight":1}
  ],
  "fallback":"dedicated_dungeons:basic"
}
```

Connector:

```json
{
  "bounds":{"min":[0,0,0],"max":[6,4,4]},
  "connectors":[
    {"name":"west","pos":[0,1,2],"facing":"west","type":"default"},
    {"name":"east","pos":[6,1,2],"facing":"east","type":"default"}
  ],
  "palette":{"floor":"minecraft:deepslate_tiles","wall":"minecraft:stone_bricks","accent":"minecraft:crying_obsidian"}
}
```

Boss definition:

```json
{
  "entity":"minecraft:wither_skeleton",
  "room_sizes":["large","boss"],
  "base_health":160,
  "base_damage":12,
  "reward_pool":"my_pack:crypt",
  "weight":1
}
```

## 3. Комнаты, boss arena и survival arena

Обычная комната использует формат из `STRUCTURE_CREATION_GUIDE.md`. Boss-room имеет `size: "boss"`, тег `boss`, markers `boss_spawn`, `loot_spawn` и `exit`.

Survival room:

```json
{
  "theme":"my_pack:crypt",
  "structure":"my_pack:crypt/arena",
  "size":"arena",
  "bounds":{"min":[0,0,0],"max":[24,8,24]},
  "connectors":[{"name":"entrance","pos":[12,1,24],"facing":"south","type":"arena"}],
  "markers":{
    "player_spawn":[[12,1,20]],
    "mob_spawn":[[12,1,12]],
    "exit":[[12,1,22]]
  },
  "tags":["arena","survival"],
  "required_mods":[]
}
```

Чтобы runtime использовал её, установите `survivalArena.arenaRoom = "my_pack:arena"` в `config/dedicated_dungeons.toml`. Волны, задержка, elite cadence и случайный weight находятся в той же секции.

## 4. Optional blocks и fallback

Укажите `required_mods` на theme/archetype/pool/room/connector/boss, содержащем optional-контент. Мод фильтрует такие definitions до выбора. Однако block tags не заменяют блоки внутри NBT. Для вариативности используйте отдельные NBT-варианты, structure processors в отдельном аддоне или post-processing; базовый мод не подменяет NBT-блоки автоматически.

Всегда оставляйте vanilla fallback. Для комнаты достаточно удалить поле `structure` и задать vanilla `palette`; для chain-level fallback используются поля `fallback` в theme, archetype и room pool. Число попыток и максимальный размер ограничены, поэтому невалидный pack не создаёт бесконечную генерацию.

## 5. Загрузка и диагностика

1. Положите pack в `<world>/datapacks/my_dungeons`.
2. Выполните `/datapack list` и убедитесь, что pack включён.
3. Выполните `/reload`.
4. Выполните `/dungeon validate_data`.
5. Проверьте `logs/latest.log` по `my_pack:` и `Dedicated Dungeons`.
6. Запустите конкретный archetype: `/dungeon portal C my_pack:crypt`.
7. Для обхода портала: `/dungeon start <player> C my_pack:crypt`.
8. Для arena: `/dungeon start_survival C` или `/dungeon spawn_portal survival C`.
9. Смотрите state: `/dungeon list`; при аварии — `/dungeon cleanup`.

Ошибки `missing`, `unknown entity/block`, `fallback cycle`, `no entrance/normal/boss room` нужно исправить. Warning об отсутствующем моде из `required_mods` допустим. Тестируйте pack также без optional-модов: должен выбираться vanilla fallback, а `/dungeon validate_data` не должен показывать crash-level ошибок.
