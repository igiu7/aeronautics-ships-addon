package com.aerovaloris;

import com.aerovaloris.anchor.AnchorHoldHandler;
import com.aerovaloris.anchor.AnchorManager;
import com.aerovaloris.rudder.RudderHandler;
import com.aerovaloris.sail.SailInteractionHandler;
import com.aerovaloris.sail.SailThrustHandler;
import com.aerovaloris.wind.WindCommand;
import com.aerovaloris.wind.WindManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Aeronautics Valoris – dodatek do Create Aeronautics / Sable:
 * <ul>
 *   <li>ciąg z żagli na statkach (zależny od liczby żagli),</li>
 *   <li>przód/tył dla Symmetric Sail,</li>
 *   <li>globalny wiatr w stylu Sea of Thieves, który mnoży ciąg zależnie od ustawienia żagla.</li>
 * </ul>
 */
@Mod(aeronauticsvaloris.MODID)
public class aeronauticsvaloris {
    public static final String MODID = "aeronauticsvaloris";
    public static final Logger LOGGER = LogUtils.getLogger();

    public aeronauticsvaloris(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        // bloki (ster)
        ModBlocks.register(modEventBus);

        // sieć
        modEventBus.addListener(WindManager::registerPayloads);

        // wiatr
        NeoForge.EVENT_BUS.addListener(WindManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WindManager::onLogin);
        NeoForge.EVENT_BUS.addListener(WindManager::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(WindManager::onRespawn);
        NeoForge.EVENT_BUS.addListener(WindCommand::register);

        // żagle
        NeoForge.EVENT_BUS.addListener(SailThrustHandler::onPrePhysicsTick);
        NeoForge.EVENT_BUS.addListener(SailInteractionHandler::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(SailInteractionHandler::onRightClickBlock);

        // ster
        NeoForge.EVENT_BUS.addListener(RudderHandler::onPrePhysicsTick);

        // kotwica
        NeoForge.EVENT_BUS.addListener(AnchorHoldHandler::onPrePhysicsTick);
        NeoForge.EVENT_BUS.addListener(AnchorManager::onServerStopped);

        // stabilność statków (prostowanie przechyłów)
        NeoForge.EVENT_BUS.addListener(com.aerovaloris.physics.ShipStabilizer::onPrePhysicsTick);

        LOGGER.info("Aeronautics Valoris: wiatr i żagle załadowane");
    }
}
