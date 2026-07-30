package plumbline;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Sub-level bounds repair for Sable.
 * <p>
 * Two parts, kept separate so one breaking doesn't take the other with it: a mixin guard
 * that stops a bad bounding box locking up the server tick, and a healer that recomputes
 * those bounds through Sable's public API. Nothing is ever deleted.
 */
@Mod("plumbline")
public class Plumbline {

    public static final Logger LOG = LogUtils.getLogger();

    public Plumbline(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, PlumblineConfig.SPEC);

        modBus.addListener(ModConfigEvent.Loading.class, e -> PlumblineConfig.sync());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> PlumblineConfig.sync());

        NeoForge.EVENT_BUS.register(new BoundsHealer());
        NeoForge.EVENT_BUS.register(new PlumblineCommand());

        LOG.info("[Plumbline] loaded, watching Sable sub-level bounds. Nothing gets deleted.");
    }
}
