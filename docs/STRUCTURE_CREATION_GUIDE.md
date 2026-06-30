# Создание встроенных структур Dedicated Dungeons

Этот гайд описывает фактический формат мода для NeoForge 1.21.1. Встроенный контент лежит в `src/main/resources/data/dedicated_dungeons`; Java-регистрация комнате не нужна.

## 1. Постройка и рекомендуемые размеры

Стройте комнату в тестовом плоском мире и заранее выберите локальную точку `[0, 0, 0]`. Рекомендуемые габариты вместе со стенами:

| Категория | Рекомендуемый размер | Назначение |
|---|---:|---|
| `small` | 7–11 × 5–7 × 7–11 | коридор, тупик, тайник |
| `medium` | 11–17 × 6–9 × 11–17 | обычная боевая/сюжетная комната |
| `large` | 17–25 × 7–12 × 17–25 | крупная развилка или событие |
| `boss` | 21–33 × 8–14 × 21–33 | босс и выход |
| `arena` | 21–33 × 8–14 × 21–33 | волны Survival Arena |

Оставляйте не меньше двух воздушных блоков над `player_spawn`, `mob_spawn` и `boss_spawn`. Точка connector должна лежать на внешней стене, а `facing` должен смотреть наружу.

## 2. Маркеры и соединения

В текущем формате маркеры задаются координатами в JSON. Это надёжнее, чем оставлять служебные блоки внутри NBT. Поддерживаются:

- `player_spawn` — вход игрока;
- `boss_spawn` — босс;
- `mob_spawn` — центр волн/обычных мобов;
- `loot_spawn` — точка награды;
- `exit` — выход после победы;
- `return_portal` — визуальная/логическая точка возврата.

`entrance`, `connector_entrance` и `connector_exit` задаются не marker-блоками: роль комнаты определяется тегом `entrance`, а проходы — элементами `connectors`. У каждого connector есть `name`, `pos`, наружный `facing` и совместимый `type`.

Можно временно поставить заметные блоки в мире на местах маркеров, выписать их локальные координаты, затем удалить перед сохранением. Мод не преобразует `STRUCTURE_BLOCK`/`JIGSAW` DATA-маркеры из NBT автоматически — JSON остаётся источником истины.

## 3. Сохранение NBT

1. Поставьте structure block, режим `SAVE`.
2. Задайте позицию и размер так, чтобы локальный ноль совпал с нулём JSON.
3. Отключите сохранение лишних сущностей либо сознательно оставьте только нужные.
4. Нажмите `SAVE`.
5. Возьмите `<world>/generated/<namespace>/structures/<path>.nbt`.
6. Положите файл в `src/main/resources/data/<namespace>/structure/<path>.nbt`.

Пример: `data/dedicated_dungeons/structure/crypt/hall.nbt` имеет id `dedicated_dungeons:crypt/hall`. Используется именно каталог `structure` в единственном числе.

## 4. Определение комнаты

Создайте `data/dedicated_dungeons/dungeon_rooms/crypt_hall.json`:

```json
{
  "theme": "dedicated_dungeons:underworld",
  "structure": "dedicated_dungeons:crypt/hall",
  "size": "medium",
  "bounds": { "min": [0, 0, 0], "max": [14, 7, 14] },
  "connectors": [
    { "name": "west", "pos": [0, 1, 7], "facing": "west", "type": "default" },
    { "name": "east", "pos": [14, 1, 7], "facing": "east", "type": "default" }
  ],
  "markers": {
    "mob_spawn": [[7, 1, 7]],
    "loot_spawn": [[7, 1, 11]]
  },
  "tags": ["normal"],
  "weight": 4,
  "required_mods": []
}
```

Без поля `structure` мод строит procedural-комнату по `palette`; это удобный vanilla fallback. `required_mods` содержит mod id, например `ironsspellbooks`, и никогда не должен содержать display name.

## 5. Добавление в пул и тему

Добавьте ссылку в `data/dedicated_dungeons/dungeon_room_pools/basic.json`:

```json
{ "room": "dedicated_dungeons:crypt_hall", "weight": 4 }
```

Пул уже связан с archetype, а archetype — с theme. Для нового набора нужны отдельные `dungeon_themes`, `dungeon_archetypes`, `dungeon_room_pools`, connectors и boss definition. Теги обязательных ролей: `entrance`, `normal`, `boss`; дополнительные: `dead_end`, `loot`, `arena`, `survival`.

Survival Arena читает room id из `config/dedicated_dungeons.toml` (`survivalArena.arenaRoom`). Arena-комната должна иметь `size: "arena"`, тег `arena`, `player_spawn`, `mob_spawn` и `exit`. Если она недоступна, включается безопасная generated arena.

## 6. Проверка

```text
/reload
/dungeon validate_data
/dungeon portal C dedicated_dungeons:basic
/dungeon start <player> C dedicated_dungeons:basic
/dungeon start_survival C
/dungeon list
```

Проверьте все четыре поворота комнаты: проходы должны встречаться без стены, bounding box не должен пересекать соседнюю деталь, а сундук/награда не должны попадать в вырезаемый doorway 3×3.

Типичные ошибки:

- пропущен JSON marker либо у spawn нет свободной высоты;
- connector смотрит внутрь;
- `bounds` меньше реальной NBT или использует другой локальный ноль;
- NBT слишком велик для `maxDungeonDiameter`;
- сундук находится рядом с doorway и вырезается;
- неверный mod id в `required_mods`;
- NBT содержит блок отсутствующего optional-мода, но fallback не задан;
- файл положен в `structures` вместо `structure`.
