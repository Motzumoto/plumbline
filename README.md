<img src="icon.png" alt="Plumbline" width="128" align="right">

# Plumbline

Sub-level bounds repair for [Sable](https://modrinth.com/mod/sable) / Create: Aeronautics.

Plumbline does not delete anything, ever. If it can't fix a sub-level it logs it and
leaves it alone.

## The problem

Sable picks the blocks to test for entity collision like this:

```java
BlockPos.betweenClosed(min, max)
```

It then walks the result in a four pass loop, and never checks how big the region is.
When a sub-level's bounding box doesn't match the blocks actually in it, that region can
cover tens of millions of positions, and a single entity moving one step takes
effectively forever.

Numbers from a real world:

```
region  155 x 985 x 379  =  57,863,825 blocks
one server tick stuck for 207 seconds
six entities involved
```

Flying mobs make this much worse, because they call `Entity.move()` every tick. In the
case above it was six blazes near a parked airship.

### Why that's a broken box and not just a big one

The region covers y = -827. The bottom of the world is -64. There is nothing there and
there never can be.

That is what Plumbline checks against. It's a fact about how the world works rather than
a number somebody picked, so it needs no tuning and won't trip on a large build.

## What it does

Two parts, working independently, so if one breaks the other still runs.

**The guard** is a mixin. It caps how large a region the collision scan is allowed to
walk. Past the cap it hands back an empty iterable, so the loop finds nothing, no
sub-level collision happens for that pass, and the entity moves normally. That stops the
freeze without fixing the cause.

**The healer** uses Sable's public API and no mixin at all. Every 30 seconds it walks
`SubLevelContainer.getContainer(level).getAllSubLevels()` and checks each bounding box
against the world height limits. Anything impossible gets `forceUpdateGlobalBounds()`,
which asks Sable to work its bounds out again from the blocks that are really there.
Doing that to a healthy sub-level changes nothing, so a false positive costs a log line.

If a sub-level is still wrong after a repair attempt, Plumbline writes it down and tells an
operator once. It keeps retrying that one but waits twice as long each time, levelling off
at half an hour, so a sub-level nobody can fix costs about one log line every thirty
minutes rather than one every pass. It never gives up on it and it never removes it.

## /plumbline report

Most reports of this bug amount to "airships lag my server", which nobody can act on.
`/plumbline report` writes markdown to the log: mod versions, world height limits, every
bad bounding box before and after repair, and each oversized region with a hit count.

Plumbline works around a bug in another mod and it would be better if it didn't have to
exist. The report is there so that if you decide to write the problem up, you have the
actual numbers to write it up with.

`/plumbline status` prints a one line summary. Both commands need permission level 2.

## Config

`config/plumbline-common.toml`

| option | default | meaning |
|---|---|---|
| `enabled` | `true` | master switch |
| `guardMaxVolume` | `262144` | largest region the guard permits, 64 cubed |
| `logRegions` | `true` | log each distinct oversized region |
| `healer.enabled` | `true` | validate and repair bounds |
| `healer.intervalSeconds` | `30` | seconds between passes |
| `healer.worldHeightSlack` | `128` | how far past the build limits is still tolerated |
| `healer.volumeCheckEnabled` | `false` | second check by volume, can misfire on huge builds |
| `healer.volumeCheckMax` | `64000000` | threshold for that check |
| `healer.notifyOps` | `true` | message ops when a repair fails |

Only the height check runs by default. The volume one is a guess and can be wrong about a
genuinely enormous contraption, so it's opt in.

## Requirements

Minecraft 1.21.1, NeoForge 21.1+, Sable 2.0+, Java 21. Works in singleplayer and on
dedicated servers.

## Building

`build.sh` compiles with plain javac against the jars in a normal Minecraft install. There
is no Gradle setup because an untested NeoGradle config would be worse than none. If you
want to add one, that's a very welcome pull request.

```
MC_LIBS=/path/to/launcher/libraries SABLE_JAR=/path/to/sable.jar ./build.sh
```

## Credits

[Sable](https://github.com/ryanhcode/sable) is by ryanhcode and does all the actual work.
Plumbline is unaffiliated and not endorsed by anyone involved with it.

Related upstream issues: [#857](https://github.com/ryanhcode/sable/issues/857), which is
the same underlying problem, plus
[#338](https://github.com/ryanhcode/sable/issues/338) and
[#1098](https://github.com/ryanhcode/sable/issues/1098).

If you want to actually manage sub-levels, listing and freezing and archiving and
deleting them, use
[Shtreimel](https://www.curseforge.com/minecraft/mc-mods/shtreimel-sable-server-utility)
or [Sable CleanUp](https://modrinth.com/mod/sable-cleanup). Plumbline only does the one
thing.

MIT.
