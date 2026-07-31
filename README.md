<img src="icon.png" alt="Plumbline" width="128" align="right">

# Plumbline

Stops entity collision against [Sable](https://modrinth.com/mod/sable) sub-levels from
freezing the server tick. For Create: Aeronautics.

## The problem

When an entity moves near a sub-level, Sable works out which blocks to test like this:

1. take the entity's bounding box swept from where it was to where it is going
2. expand it by 0.35
3. inverse transform it into the sub-level's local frame at `lastPose`
4. inverse transform it again at the sub-level's current `logicalPose()`
5. union those two boxes
6. walk every block position inside the union with `BlockPos.betweenClosed(min, max)`

Step 5 is where it goes wrong. The two boxes are the same entity in the same tick, but
seen from a frame that has moved in between. If the sub-level rotated or travelled a long
way since its last pose, the two land far apart and the union spans everything between
them. That union has nothing to do with the size of the ship or the size of the entity.

Step 6 then walks it, four times over, for one entity taking one step.

Numbers from a real world:

```
region   155 x 985 x 379  =  57,863,825 positions
one server tick stuck for 207 seconds
six entities involved
```

Flying mobs make it much worse, because they call `Entity.move()` every tick. In the case
above it was six blazes near a parked airship.

## What Plumbline does

One mixin. It measures the union before step 6 and returns an empty iterable when the
region is larger than the cap. The loop finds nothing, no sub-level collision is applied
on that pass, and the entity moves normally.

Sable already does this. It compares the same volume against 125,000,000 and logs
`Enormous local sub-level collision bounds, quitting.` The problem is only the number: the
57.9 million case above went straight through that ceiling and still froze the server for
three and a half minutes. Plumbline is the same test at 262,144.

So this is a one line disagreement about a constant, wearing a mod as a hat.

## On that constant

Worth being straight about what is and is not known.

In 35,502 observed collision passes with sub-levels at rest, nothing came within an order
of magnitude of 262,144. In a session where something was wrong, 120 out of 120 passes
exceeded it, at 1.1 to 2.3 million positions each.

What has not been measured is how large a legitimate pass gets while a sub-level is
rotating fast, because the union grows with rotation by design. There is no sample of that.
If you find entities passing through a spinning sub-level, raise `guardMaxVolume` and open
an issue with the numbers, because that sample is the missing one.

Sable's 125,000,000 is the only figure anyone has published a justification for, and it is
demonstrably too high.

## /plumbline report

Most reports of this amount to "airships lag my server", which nobody can act on.
`/plumbline report` writes markdown to the log: mod versions, the cap in force, how many
collision passes were seen, and every oversized region with a hit count.

The seen count matters as much as the skip count. Zero skips out of zero passes means the
mixin never ran. Zero skips out of thirty thousand means it is working and your world is
fine, and those deserve different replies.

`/plumbline status` prints one line. Both need permission level 2.

## Config

`config/plumbline-common.toml`

| option | default | meaning |
|---|---|---|
| `enabled` | `true` | master switch, still counts passes when off |
| `guardMaxVolume` | `262144` | largest region a collision pass may walk |
| `logRegions` | `true` | log each distinct oversized region once |

## Requirements

Minecraft 1.21.1, NeoForge 21.1+, Sable 2.0+, Java 21. Singleplayer and dedicated servers.

## Building

`build.sh` compiles with plain javac against the jars in a normal Minecraft install. There
is no Gradle setup because an untested NeoGradle config would be worse than none. If you
want to add one, that is a very welcome pull request.

```
MC_LIBS=/path/to/launcher/libraries SABLE_JAR=/path/to/sable.jar ./build.sh
```

## History

Earlier versions also shipped a "healer" that periodically checked each sub-level's
bounding box and asked Sable to recompute any that reached outside the world height
limits. It was removed in 1.1.0 because it could not work.

`sub.boundingBox()` is the sub-level's box in overworld coordinates, which is small and
correct. The region that explodes is built from the entity's box in sub-level local
coordinates, and the two never meet. The healer ran for a full session against a world
where the guard was skipping 120 out of 120 passes, and correctly reported nothing wrong,
because nothing was wrong with the thing it was looking at.

## Credits

[Sable](https://github.com/ryanhcode/sable) is by ryanhcode and does all the actual work.
Plumbline is unaffiliated and not endorsed by anyone involved with it.

Related upstream issues: [#857](https://github.com/ryanhcode/sable/issues/857),
[#338](https://github.com/ryanhcode/sable/issues/338) and
[#1098](https://github.com/ryanhcode/sable/issues/1098).

If you want to actually manage sub-levels, listing and freezing and archiving and
deleting them, use
[Shtreimel](https://www.curseforge.com/minecraft/mc-mods/shtreimel-sable-server-utility)
or [Sable CleanUp](https://modrinth.com/mod/sable-cleanup). Plumbline only does the one
thing.

MIT.
