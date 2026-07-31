package plumbline;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Caps how large a block region Sable will walk for one entity collision pass.
 * <p>
 * Sable applies the same cap at 125,000,000, which is high enough that a region of 57.9
 * million can still wedge a server tick for over three minutes. Plumbline is that check
 * at a threshold low enough to matter.
 */
@Mod("plumbline")
public class Plumbline {

    public static final Logger LOG = LogUtils.getLogger();

    public Plumbline(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, PlumblineConfig.SPEC);

        modBus.addListener(ModConfigEvent.Loading.class, e -> PlumblineConfig.sync());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> PlumblineConfig.sync());

        NeoForge.EVENT_BUS.register(new PlumblineCommand());

        // Counters are static and this object outlives any one server, so a singleplayer
        // player opening a second world would otherwise see the first world's numbers.
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> Observations.reset());

        LOG.info("[Plumbline] loaded, watching Sable sub-level collision volume.");
    }
}
