The questions I get the most. Not in here? Ask on the [Discord](https://discord.gg/94Qk4MswX5).

## Playing

<details>
<summary><b>Pressing B does nothing</b></summary>

Another mod might use B too. Check *Options > Controls > Key Binds*, conflicting keys show in red. Change *Open Emote
Wheel* to something else.
</details>

<details>
<summary><b>My friends can't see my emotes</b></summary>

The server needs the mod, and your friends need it too. On a server without it, only you see your emotes.
In LAN or singleplayer opened to LAN, the host is the server. See [Installing](Installing#multiplayer).

Also check that nobody turned off *Other Players' Emotes* in their [Settings](Settings).
</details>

<details>
<summary><b>The emote stops right away</b></summary>

Walking, jumping or attacking stops an emote. Let go of the movement keys before playing one. Some controller mods
or auto-walk can count as walking too.
</details>

<details>
<summary><b>"Slow down a little"</b></summary>

The server has a cooldown between emotes. It's short by default, the server owner can change it.
</details>

<details>
<summary><b>"You can't use that emote here"</b></summary>

The server disabled it, or it's restricted to some players. Ask the server owner.
</details>

<details>
<summary><b>Partner emote request doesn't show up</b></summary>

- Stand at most 2 blocks away and **look at each other**.
- The other player might have *Partner Emote Requests* off. They can still walk up and join you themselves.
- The server might have partner emotes off.

See [Partner Emotes](Partner-Emotes).
</details>

<details>
<summary><b>/emotesync says they're not playing an emote I can join</b></summary>

They have to be playing an emote right now, be close enough (render distance), and not be in a partner emote. Those
are only for two.
</details>

<details>
<summary><b>I don't want the camera to switch to third person</b></summary>

Turn off *Third Person While Emoting* in the [Settings](Settings).
</details>

<details>
<summary><b>The wheel is laggy</b></summary>

The 3D previews cost a bit on slow PCs. Turn off *Emote Previews* in the [Settings](Settings), the wheel shows names
instead.
</details>

<details>
<summary><b>Do I need PlayerAnimator or another library?</b></summary>

No. Since 2.0 the mod doesn't need any library. Only Fabric needs Fabric API.
</details>

<details>
<summary><b>Will you add version X or loader Y?</b></summary>

The versions on the [Home](Home) page are the ones I support. I'm keeping the list like this so every version gets
the same care.
</details>

## Servers

<details>
<summary><b>Do players need the mod to join my server?</b></summary>

No. Players without it can join, they just don't see emotes.
</details>

<details>
<summary><b>How do I make an emote VIP only?</b></summary>

Put it in `restrictedEmotes` and give the `playeremotes.restricted` permission to the group. Example on
[Server Setup](Server-Setup#permissions).
</details>

<details>
<summary><b>I changed the config and nothing happened</b></summary>

Run `/playeremotes reload`. If it still doesn't work, check the server log, a typo in the file makes the mod use the
defaults until it's fixed.
</details>

<details>
<summary><b>My server emote pack doesn't show up for players</b></summary>

- It has to be in a data pack, under `data/<namespace>/playeremotes/`, not `assets/`.
- `sendServerPacks` has to be `true`.
- Run `/reload` after changing files.
- Check the limits: 2 MB per file, 16 MB in total.
</details>

## Making Emotes

<details>
<summary><b>My emote doesn't show up</b></summary>

Go through these:

1. Is the resource pack turned on?
2. Is the file in `assets/<namespace>/playeremotes/`? The folder is called `playeremotes`, not `emotes` or
   `animations`.
3. Did you export the **animation** (*Animation > Export Animations*), not the model?
4. Did you press the *Reload Emote Files* key (set it in Controls first) or `F3+T`?
5. Is the animation used by another emote in `pack.json` (`animations`, `partner`, `variants`)? Those don't show up
   as their own emote.
6. Check the log (`logs/latest.log`) for "PlayerEmotes", broken files are named there.
</details>

<details>
<summary><b>It looks different in game than in Blockbench</b></summary>

Most likely a pivot got moved. The game only reads your animation and uses its own player model, so it needs the
[Template](Template)'s pivots. Start again from a fresh template and copy your animation over.
</details>

<details>
<summary><b>The head doesn't do what I keyframed</b></summary>

Set `"look": false` for the emote in `pack.json`. With `look` on, the head follows the camera on top of the
animation.
</details>

<details>
<summary><b>My emote shows up in the Default pack</b></summary>

Files right in `playeremotes/` join the Default pack. Put them in a folder of their own, like
`playeremotes/my_emotes/`. See [Emote Packs](Emote-Packs).
</details>

<details>
<summary><b>My particle doesn't show</b></summary>

Only particles without options work (`minecraft:heart`, `minecraft:crit`...). `dust` and other ones that need a color
or a block don't. See [Sounds and Particles](Sounds-and-Particles).
</details>

<details>
<summary><b>The partner emote players stand in each other</b></summary>

The `distance` doesn't match your animations. Both actions have to be made as if the partner stands `distance` blocks
in front. See [Making Partner Emotes](Making-Partner-Emotes#lining-them-up).
</details>

<details>
<summary><b>Can I put my emote pack on CurseForge / Modrinth?</b></summary>

Yes, go ahead, no need to ask. Same for modpacks, addons and videos. A link back is appreciated, and I'd love to see
it on the Discord :)
</details>

<details>
<summary><b>Can my mod add emotes?</b></summary>

Yes, put them in your mod's `assets/<your mod id>/playeremotes/`, same as a resource pack. No code needed. If you want
to pick animations from the world, you can register a selector, see [pack.json](Pack-Json#selector-and-variants).
</details>
