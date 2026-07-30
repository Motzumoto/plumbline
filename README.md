<img src="icon.png" alt="Plumbline" width="128" align="right">

# Plumbline

**Sub-level bounds repair for [Sable](https://modrinth.com/mod/sable) / Create: Aeronautics.**

**Plumbline never deletes anything.** Not with a config option, not with `--confirm`, not ever.
If it cannot repair a sub-level it says so and stops. People spend months on these airships.

---

## The problem

Sable's `SubLevelEntityCollision.collide()` picks the blocks to test with

```java
BlockPos.betweenClosed(min, max)
```

and walks the result inside a four-pass loop, without ever checking how large that region is.
When a sub-level's bounding box doesn't match its real footprint, that region can span tens of
millions of blocks — so a single entity's movement iterates effectively forever.

Measured on a live world:

```
region  155 x 985 x 379  =  57,863,825 blocks
one server tick stuck for 207 seconds
caused by six entities
```

Flying mobs make it worse, because they call `Entity.move()` every tick. In the case above the
trigger was six blazes near a parked airship.

### How you can tell the bounds are wrong, not just big

That region spans **y = −827 → 157**. The world bottom is **−64**. Blocks *cannot exist* at
y = −827. This isn't a big build — it's a broken bounding box. Plumbline is built on that
invariant, which is why its main check needs no tuning and cannot false-positive on a
legitimately enormous contraption.

## What Plumbline does

Two independent layers, so a failure in one can't disable the other.

**1. The guard** (mixin). Caps the region `collide()` is allowed to walk. Over the cap it hands
back an empty iterable: the loop finds no blocks, no sub-level collision is applied for that
pass, and the entity moves normally. This prevents the freeze; it doesn't fix the cause.

**2. The healer** (plain Sable API, no mixin). Every 30 seconds it walks
`SubLevelContainer.getContainer(level).getAllSubLevels()` and checks each bounding box against
the world's build limits. Anything impossible gets `forceUpdateGlobalBounds()` — Sable recomputes
its own bounds from reality. That call is idempotent and harmless on a healthy sub-level, so a
false positive costs nothing but a log line.

If a sub-level still has impossible bounds after a repair attempt, Plumbline records it, tells an
operator once, and takes no further action.

## `/plumbline report`

The hard part of this bug is evidence. Most reports read *"airships lag my server"*, which is
unactionable, so nothing gets fixed. `/plumbline report` writes a paste-ready Markdown report to
the log: mod versions, world height limits, every corrupt bounding box before and after repair,
and every oversized collision region with hit counts.

**If Plumbline catches something, please paste that report into
[the Sable issue tracker](https://github.com/ryanhcode/sable/issues).** This mod is a workaround.
It would rather campaign for its own obsolescence than quietly become load-bearing.

`/plumbline status` gives a one-line summary. Both require permission level 2.

## Config

`config/plumbline-common.toml`

| option | default | meaning |
|---|---|---|
| `enabled` | `true` | master switch |
| `guardMaxVolume` | `262144` | largest region the guard permits (64³) |
| `logRegions` | `true` | log each distinct oversized region |
| `healer.enabled` | `true` | validate and repair bounds |
| `healer.intervalSeconds` | `30` | seconds between passes |
| `healer.worldHeightSlack` | `128` | tolerance outside build limits before bounds count as impossible |
| `healer.volumeCheckEnabled` | `false` | secondary heuristic; **can** false-positive on huge builds |
| `healer.volumeCheckMax` | `64000000` | threshold for that heuristic |
| `healer.notifyOps` | `true` | message ops when a repair fails |

## Requirements

Minecraft 1.21.1 · NeoForge 21.1+ · Sable 2.0+ · Java 21. Works in singleplayer (integrated
server) and on dedicated servers.

## Credits

All the real work is [Sable](https://github.com/ryanhcode/sable) by ryanhcode. Plumbline is an
unaffiliated third-party workaround and is not endorsed by the Sable team.

Related upstream issues: [#857](https://github.com/ryanhcode/sable/issues/857) (bounds not derived
from the contraption footprint), [#338](https://github.com/ryanhcode/sable/issues/338),
[#1098](https://github.com/ryanhcode/sable/issues/1098).

If you want to *manage* sub-levels — list, freeze, archive, delete — use
[Shtreimel](https://www.curseforge.com/minecraft/mc-mods/shtreimel-sable-server-utility) or
[Sable CleanUp](https://modrinth.com/mod/sable-cleanup). Plumbline deliberately does one job.

MIT licensed.
