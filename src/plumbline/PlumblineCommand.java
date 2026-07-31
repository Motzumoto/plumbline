package plumbline;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /plumbline report} dumps what the guard has seen as markdown, so it can go
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
        long seen = Observations.guardSeen();
        src.sendSuccess(() -> Component.literal(
            "[Plumbline] collision passes seen: " + seen
            + (seen == 0L ? " (guard has not been consulted yet)" : "")
            + "; skipped as oversized: " + Observations.guardTotal()
            + " across " + Observations.guardRegions().size() + " distinct region(s)."), false);
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
        sb.append("Entity collision passes against Sable sub-levels, and how many enclosed\n");
        sb.append("more block positions than one pass can walk in reasonable time.\n\n");

        sb.append("| item | value |\n|---|---|\n");
        sb.append("| Minecraft | ").append(safe(() -> src.getServer().getServerVersion())).append(" |\n");
        sb.append("| Sable | ").append(modVersion("sable")).append(" |\n");
        sb.append("| Create Aeronautics | ").append(modVersion("aeronautics")).append(" |\n");
        sb.append("| Plumbline | ").append(modVersion("plumbline")).append(" |\n");
        sb.append("| Guard volume cap | ").append(PlumblineRuntime.guardMaxVolume).append(" |\n");
        sb.append("| Sable's own cap | 125000000 |\n\n");

        Map<String, AtomicLong> regions = Observations.guardRegions();
        long seen = Observations.guardSeen();
        sb.append("**Guard: ").append(seen).append(" collision pass(es) seen, ")
          .append(Observations.guardTotal()).append(" skipped as oversized, across ")
          .append(regions.size()).append(" distinct region(s)**\n\n");

        if (seen == 0L) {
            sb.append("_The guard was never consulted. Either no entity moved near a sub-level\n");
            sb.append("during this session, or the mixin did not apply._\n");
        } else if (regions.isEmpty()) {
            sb.append("_Guard is live and nothing was oversized._\n");
        } else {
            sb.append("| region (min -> max, sub-level local space) | hits |\n|---|---|\n");
            for (Map.Entry<String, AtomicLong> e : regions.entrySet()) {
                sb.append("| `").append(e.getKey()).append("` | ").append(e.getValue().get()).append(" |\n");
            }
            sb.append("\nThese are the union of the entity's swept bounding box transformed into\n");
            sb.append("the sub-level's local frame at its previous pose and at its current pose.\n");
            sb.append("A large one means the sub-level moved or rotated a long way between those\n");
            sb.append("two poses, not that anything about the sub-level is malformed.\n");
        }

        sb.append("\nRelated: ryanhcode/sable#857, #338, #1098.\n");
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
