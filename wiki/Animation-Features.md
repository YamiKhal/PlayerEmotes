## Loop modes

| Blockbench | In the file | In game |
|------------|-------------|---------|
| Play Once | `"loop": false` | plays, then the emote ends |
| Loop | `"loop": true` | keeps going until the player stops it |
| Hold On Last Frame | `"loop": "hold_on_last_frame"` | plays, then stays in the last pose until the player stops it |

Hold On Last Frame is the one for sitting, laying and poses. Molang (below) keeps running while it holds, so you can
still add breathing on top.

## Keyframes

- **Rotation, position and scale** on every bone.
- **Pre and post values** (the two values when you split a keyframe in Blockbench), for instant jumps.
- **Interpolation:** linear, smooth (catmull-rom) and step.

Rotation is in degrees, position in pixels (16 pixels = 1 block), like in Blockbench.

## Easing

Made with the GeckoLib Blockbench plugin? Its easing works. The easing goes on the keyframe you're moving **to**.

```json
"1.0": {
  "post": [0, 0, 45],
  "easing": "easeOutBack",
  "easingArgs": [1.5]
}
```

Every GeckoLib / AzureLib easing works: `linear`, `step`, `easeInSine`, `easeOutSine`, `easeInOutSine`, `easeInQuad`
... up to `easeInOutBounce`, `easeOutBack`, `easeOutElastic` and the rest. `catmullrom` and `step` work as easing too.

## Molang

You can write Molang in any keyframe value instead of a number, Blockbench shows it in the preview too.

```json
"head": {
  "rotation": {
    "0.0": { "post": ["math.sin(query.anim_time * 180) * 5", 0, 0] }
  }
}
```

That nods the head 5 degrees up and down, once every 2 seconds.

    Math - + - * /, comparisons, a ? b : c, and math.* (sin, cos, abs, clamp, lerp, lerprotate, random, die_roll, min_angle, hermite_blend...)
    Angles - degrees, like in Blockbench
    query.anim_time - seconds since the animation started, starts over each loop
    query.life_time - seconds since the emote started, keeps counting while holding the last frame
    Player queries - health, speed, is_sneaking and more, see below

**Breathing on a held pose:** write the Molang right into the last keyframe of the pose. Since `life_time` keeps
counting while the pose is held, the player keeps breathing:

```json
"torso": {
  "rotation": {
    "0.0": { "post": [0, 0, 0] },
    "1.5": { "post": ["20 + math.sin(query.life_time * 120) * 1.5", 0, 0] }
  }
}
```

### Player queries

Molang can read things about the player playing the emote. `q.` works the same as `query.`.

```
query.health  max_health  hurt_time
query.is_on_ground  is_in_water  is_in_water_or_rain  is_in_lava  is_on_fire
query.is_sneaking  is_sprinting  is_swimming  is_riding  is_using_item
query.ground_speed  vertical_speed  yaw_speed        blocks per second, degrees per second
query.limb_swing  limb_swing_amount
query.head_x_rotation  head_y_rotation  body_y_rotation  cardinal_facing_2d
query.distance_from_camera  equipment_count  has_head_gear  player_level
query.time_of_day  moon_phase
```

`head_y_rotation` is the head's turn compared to the body, so this on `head` looks where the player looks:

```json
"post": ["q.head_x_rotation", "q.head_y_rotation", 0]
```

Queries from other mods (that this mod doesn't know) are `0`. In the menu previews they read your own player.

## Scale

Scale works like in Blockbench: around the bone's pivot, and a bone scales what's nested in it. Scaling `torso`
grows the head and arms with it.

- One number scales all three axes, `[x, y, z]` each on its own.
- `0` hides a bone.
- `body` scales the whole player. The shadow, hitbox and name tag stay the same.
- Negative scale (mirroring) shows the part inside out.
- The lower arms and legs only rotate, scale the upper bone instead.
- Scale is capped at 16, so nobody can fill everyone's screen with a giant head :>

## Effects

Sound and particle keyframes (Blockbench's *Effects* row) work, see [Sounds and Particles](Sounds-and-Particles).

## What doesn't work

    Moving the template's pivots - the game uses its own model, keep them where they are
    Position on the lower arm and leg bones - only rotation counts there
    Timeline (instruction) keyframes - skipped, they only mean something to the mod that made them
    Custom geometry on the player - the game only reads the animation, not your model. Use props for extra stuff
