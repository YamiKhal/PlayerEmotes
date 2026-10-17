## Greetings

| Emote | Id | Plays |
|-------|----|-------|
| Wave | `wave` | once |
| Slim Wave | `slim_wave` | once |
| Salute | `salute` | loops |
| Bow | `bow` | holds |
| Bow (Polite) | `bow_polite` | holds |
| Bow (Deep) | `bow_deep` | holds |
| Bow Down | `bow_down` | holds |
| Kneel | `kneel` | holds |

## Feelings

| Emote | Id | Plays |
|-------|----|-------|
| Cheer | `cheer` | loops, with sound |
| Clap | `clap` | loops |
| Clap (High) | `clap_high` | loops |
| Laugh | `laugh` | loops, with sound |
| Cry | `cry` | loops |
| Facepalm | `facepalm` | once |
| Shrug | `shrug` | once |
| Yawn | `yawn` | once |
| Hands Up | `hands_up` | loops |
| Arms Crossed | `arms_crossed` | loops |
| Arms Crossed Impatient | `arms_crossed_impatient` | loops |
| Flex Muscle | `flex_muscle` | once |
| T-Pose | `t_pose` | holds |

## Sitting and Laying

| Emote | Id | Plays |
|-------|----|-------|
| Sit Japanese | `sit_japanese` | holds |
| Sit Lean Back | `sit_lean_back` | holds |
| Sit Rest | `sit_rest` | holds |
| Sit Side | `sit_side` | holds |
| Lay | `lay` | holds |
| Lay (Relaxed) | `lay_relaxed` | holds |
| Lay (Side) | `lay_side` | holds |
| Lean | `lean` | holds |
| Lean Gentle | `lean_gentle` | holds |
| Hide (Barrel) | `hide_barrel` | holds |

## Dances and Moves

| Emote | Id | Plays |
|-------|----|-------|
| Robot Dance | `robot_dance` | loops |
| Side Step Shuffle | `side_step_shuffle` | loops |
| Arm Wave Dance | `arm_wave_dance` | loops |
| Floss | `floss` | loops |
| Thriller | `thriller` | loops |
| Air Drum | `air_drum` | loops |
| Ground Spin | `ground_spin` | loops |
| Backflip | `backflip` | once |
| Titanic | `titanic` | holds |

## You Might Recognize These :>

| Emote | Id | Plays |
|-------|----|-------|
| Kamehameha | `kamehameha` | holds |
| Naruto Run | `naruto_run` | loops |
| Zelda Item Get | `zelda_item_get` | holds, spins your held item |
| Matrix Dodge | `matrix_dodge` | once |
| Crane Kick | `crane_kick` | holds |
| Jotaro Point | `jotaro_point` | holds |
| Ginyu Pose | `ginyu_pose` | holds |
| Lightning Bolt | `lightning_bolt` | holds |

## Partner Emotes

These need two players, see [Partner Emotes](Partner-Emotes).

| Emote | Id |
|-------|----|
| Hug | `hug` |
| High Five | `high_five` |
| High Five (Sport) | `high_five_sport` |
| Fusion Dance | `fusion_dance` |

## Disabling some on a server

Copy the ids into `disabledEmotes` or `restrictedEmotes`, see [Server Setup](Server-Setup):

```json
"disabledEmotes": ["playeremotes:t_pose", "floss"]
```
