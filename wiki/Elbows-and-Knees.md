Emotes can bend the arms and legs in the middle. My own emotes don't use it, I like the classic straight look more,
but it's there for emote packs that want it.

## How to use it

1. Use `player_emote_template_bendable.geo.json` from the [Template](Template).
2. Rotate the lower bones. That's all, there's nothing to turn on.

```
right_arm
└─ right_lower_arm     the elbow
left_arm
└─ left_lower_arm
right_leg
└─ right_lower_leg     the knee
left_leg
└─ left_lower_leg
```

`forearm` and `shin` work as names too (`right_forearm`, `left_shin`).

An emote that never keys these bones keeps straight limbs.

## Two looks

Pick one with `bend_style` in [pack.json](Pack-Json#bend_style):

```json
"stretch": { "bend_style": "split" }
```

| Style | What it looks like |
|-------|--------------------|
| `smooth` (default) | the limb stays one piece and bends at the joint |
| `split` | two rigid halves turning into each other, like in Blockbench |

Skin, outer layer and armor follow either way. Held items and hand props follow the hand, particle locators follow
the lower half.

Players can override it in their [Settings](Settings): *As Made*, *Smooth*, *Split* or *Off*.

## Things to know

- Only **rotation** counts on the lower bones, position is ignored, and so is scale (scale the upper bone instead).
- Bends past about 130 degrees fold over themselves.
- Keep the pivots, they sit where the game bends the limb, in the middle.
- Limbs another mod draws its own way (Entity Model Features models, Figura avatars, bendy-lib) stay straight instead
  of breaking. 3D Skin Layers may keep the outer layer straight. If anything looks weird, *Off* in the settings is the
  way out.
