If something breaks with a mod that's not here, please tell me on the
[Discord](https://discord.gg/94Qk4MswX5), with the mod's name and version.

## Works with

    Controlify - the wheel works with a controller, bind the mod's keys to buttons in Controlify (Fabric/NeoForge)
    Replay Mod - emotes show up in recordings, yours included
    Flashback - same, and jumping around the timeline lands mid-emote
    LuckPerms - and other permission mods, see Server Setup
    Sodium - works, bending included
    FastAnim - fixed in 2.0.1

## Replays

Replay Mod and Flashback recordings show everyone's emotes, the recording player's too, in step with the replay
(pausing, speed, jumping around).

- The mod has to be on the server while recording, and on the client watching.
- Emotes from server packs show if the files are in the watcher's cache.

## Mods that draw the player their own way

The mod plays emotes on the vanilla player model. Mods that change how the player is drawn can make limbs look odd
**while emoting**:

| Mod | What happens |
|-----|--------------|
| Entity Model Features (custom player models) | bent limbs stay straight |
| Figura (avatars) | bent limbs stay straight |
| 3D Skin Layers | the outer layer may stay straight on bent limbs |
| bendy-lib / PlayerAnimator mods | bent limbs stay straight |

**The fix for most of it:** set *Elbows and Knees* to *Off* in the [Settings](Settings). Most default emotes don't bend
anyway.

## Mods that change the player's pose

Some mods rewrite how the player stands or moves (animation mods, first person mods, combat mods). The mod resets the
pose before an emote plays so they don't mess it up, but if you see the head or body drifting off while emoting,
let me know which mod it is.

## Other emote mods

I haven't tested every emote mod next to this one. If two mods animate the player at the same time, they can fight
over it, so play emotes from one at a time.

## Something looks broken?

1. Set *Elbows and Knees* to *Off*.
2. Try without the mod you suspect.
3. Tell me on the Discord which mod and version, and a screenshot if you can :)
