package plumbline;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /plumbline report} dumps what has been observed as markdown, so it can go
 * straight into a bug report instead of being described as "airships lag my server".
 */
public final class PlumblineCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("plumbline")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("report").executes(c -> report(c.getSource()))));
    }

    private static int status(CommandSourceStack src) {
        Map<String, Observations.Finding> findings = Observations.findings();
        long repaired = findings.values().stream().filter(f -> f.repaired).count();
        long seen = Observations.guardSeen();
        src.sendSuccess(() -> Component.literal(
            "[Plumbline] collision scans seen: " + seen
            + (seen == 0L ? " (guard has not been consulted yet)" : "")
            + "; skipped: " + Observations.guardTotal()
            + " across " + Observations.guardRegions().size() + " distinct region(s); "
            + "sub-levels flagged: " + findings.size()
            + " (" + repaired + " repaired). Nothing is ever deleted."), false);
        return 1;
    }

    private static int report(CommandSourceStack src) {
        String text = build(src);
        // Chat truncates and mangles long text; the log is what people actually paste.
        Plumbline.LOG.info("\n{}", text);
        src.sendSuccess(() -> Component.literal(
            "[Plumbline] Report written to the server log (latest.log), "
            + "starting at '### Plumbline report'."), false);
        return 1;
    }

    private static String build(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Plumbline report\n\n");
        sb.append("Sub-level bounding boxes that reach outside the world's build limits.\n");
        sb.append("Blocks cannot exist there, so these bounds are wrong regardless of build size.\n\n");

        sb.append("| item | value |\n|---|---|\n");
        sb.append("| Minecraft | ").append(safe(() -> src.getServer().getServerVersion())).append(" |\n");
        sb.append("| Sable | ").append(modVersion("sable")).append(" |\n");
        sb.append("| Create Aeronautics | ").append(modVersion("aeronautics")).append(" |\n");
        sb.append("| Plumbline | ").append(modVersion("plumbline")).append(" |\n");
        ServerLevel lvl = src.getLevel();
        sb.append("| World height | ").append(lvl.getMinBuildHeight())
          .append(" .. ").append(lvl.getMaxBuildHeight()).append(" |\n");
        sb.append("| Height slack allowed | ").append(PlumblineRuntime.worldHeightSlack).append(" |\n");
        sb.append("| Guard volume cap | ").append(PlumblineRuntime.guardMaxVolume).append(" |\n\n");

        Map<String, String> inspected = Observations.inspected();
        sb.append("**Sub-levels the healer looked at on its last pass: ")
          .append(inspected.size()).append("**\n\n");
        if (inspected.isEmpty()) {
            sb.append("_The healer enumerated no sub-levels. If the guard below has a non-zero\n");
            sb.append("skip count then the two disagree, and the healer is looking in the wrong\n");
            sb.append("place rather than finding nothing wrong._\n\n");
        } else {
            sb.append("| sub-level | dimension, bounds and volume |\n|---|---|\n");
            for (Map.Entry<String, String> e : inspected.entrySet()) {
                sb.append("| `").append(e.getKey()).append("` | `")
                  .append(e.getValue()).append("` |\n");
            }
            sb.append('\n');
        }

        Map<String, Observations.Finding> findings = Observations.findings();
        sb.append("**Of those, flagged as having impossible bounds: ")
          .append(findings.size()).append("**\n\n");
        if (findings.isEmpty()) {
            sb.append("_none observed_\n\n");
        } else {
            sb.append("| sub-level | dimension | reason | bounds before | bounds after | repaired |\n");
            sb.append("|---|---|---|---|---|---|\n");
            for (Observations.Finding f : findings.values()) {
                sb.append("| `").append(f.subLevelId).append("` | ").append(f.dimension)
                  .append(" | ").append(f.reason)
                  .append(" | `").append(f.before).append("` | `").append(f.after)
                  .append("` | ").append(f.repaired ? "yes" : "**no**").append(" |\n");
            }
            sb.append('\n');
        }

        Map<String, AtomicLong> regions = Observations.guardRegions();
        long seen = Observations.guardSeen();
        sb.append("**Guard: ").append(seen).append(" collision scan(s) seen, ")
          .append(Observations.guardTotal()).append(" skipped as oversized, across ")
          .append(regions.size()).append(" distinct region(s)**\n\n");
        if (seen == 0L) {
            sb.append("_The guard was never consulted. Either no entity moved near a sub-level\n");
            sb.append("during this session, or the mixin did not apply._\n");
        } else if (regions.isEmpty()) {
            sb.append("_Guard is live and nothing was oversized._\n");
        } else {
            sb.append("| region (min -> max) | hits |\n|---|---|\n");
            for (Map.Entry<String, AtomicLong> e : regions.entrySet()) {
                sb.append("| `").append(e.getKey()).append("` | ").append(e.getValue().get()).append(" |\n");
            }
        }

        sb.append("\nRelated: ryanhcode/sable#857 (bounds not derived from the contraption footprint), ");
        sb.append("#338, #1098.\n");
        return sb.toString();
    }

    private static String modVersion(String id) {
        try {
            return ModList.get().getModContainerById(id)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("(not installed)");
        } catch (Throwable t) {
            return "(unknown)";
        }
    }

    private interface Sup {
        String get();
    }

    private static String safe(Sup s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return "(unknown)";
        }
    }
}
