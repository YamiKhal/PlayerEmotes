Already have animations made with the AzureLib or GeckoLib Blockbench plugin? They load as they are. Drop the
`.animation.json` into an emote pack, or include it from where it already is.

Support is **partial**: it works for humanoid animations, not every rig out there.

## The rig

The animation has to be made on a **humanoid rig** with pivots like vanilla's: head at the neck, arms at the
shoulders, legs at the hips. Creature rigs (four legs, wings, tentacles) can't be put on a player.

## Bone names

Besides the [Template](Template)'s names, these work:

| Their name | Becomes |
|------------|---------|
| `bipedHead`, `armorHead` | `head` |
| `bipedBody`, `armorBody` | `torso` |
| `bipedRightArm`, `armorRightArm`, `arm_right` | `right_arm` |
| `bipedLeftArm`, `armorLeftArm`, `arm_left` | `left_arm` |
| `bipedRightLeg`, `armorRightLeg`, `leg_right` | `right_leg` |
| `bipedLeftLeg`, `armorLeftLeg`, `leg_left` | `left_leg` |
| `root` | `body` (the whole player) |

Case, `_` and `-` don't matter, so `RightArm`, `rightArm` and `right_arm` are all the same bone.

`bipedBody` is the torso, the template's `body` is the whole player. If an animation has both a template name and
one of these for the same bone, the template name wins.

Any other bone (boots, tails, weapons) becomes a prop bone, see [Animated Props](Animated-Props).

## Animation names

`animation.model.attack` becomes `attack`, and characters an id can't have become `_` (`Attack 1` is `attack_1`).

## Includes

AzureLib's `includes` work. Use animations of any mod or resource pack without copying them, only the ones you list
become emotes:

```json
{
  "includes": [
    {
      "file_id": "somemod:animations/knight.animation.json",
      "animations": ["animation.knight.salute"]
    }
  ]
}
```

- `file_id` is a path under `assets/`, like in AzureLib. Without a namespace it's the including file's.
- No `animations` list takes all of them.
- The file's own animations win over included ones with the same name.
- Included files can include further, 8 deep at most.
- In server packs, only files inside the server's emote packs get sent. Other paths come from the player's own
  resources (another mod's assets work if the player has that mod).

## What works

    Easing - every GeckoLib / AzureLib easing, catmullrom and step as easing too
    Molang - including player queries, see Animation Features
    Effect keyframes - sounds and particles, see Sounds and Particles
    Broken animations - skipped with an error in the log, the rest of the file still loads

## What doesn't

    Timeline keyframes - skipped, they only mean something to the mod that made them
    Their .geo models as props - only items and blocks can be props for now
