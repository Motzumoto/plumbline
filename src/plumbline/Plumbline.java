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
 * Plumbline -- sub-level bounds repair for Sable.
 * <p>
 * Two independent layers, so a failure in one cannot disable the other:
 * <ol>
 *   <li>a mixin guard that stops a corrupt bounding box from wedging the server tick;</li>
 *   <li>a healer that periodically recomputes impossible bounds through Sable's public API.</li>
 * </ol>
 * Plumbline never deletes anything.
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

        LOG.info("[Plumbline] loaded -- guarding Sable sub-level collision bounds. Nothing is ever deleted.");
    }
}
