package com.forgivingworld;

import com.cupboard.config.CupboardConfig;
import com.forgivingworld.config.CommonConfiguration;
import com.forgivingworld.event.EventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

import static com.forgivingworld.ForgivingWorldMod.MODID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MODID)
public class ForgivingWorldMod
{
    public static final String                              MODID  = "forgivingworld";
    public static final Logger                              LOGGER = LogManager.getLogger();
    public static       CupboardConfig<CommonConfiguration> config = new CupboardConfig<>(MODID, new CommonConfiguration());
    public static       Random                              rand   = new Random();

    public ForgivingWorldMod(IEventBus modEventBus, ModContainer modContainer)
    {
        NeoForge.EVENT_BUS.register(EventHandler.class);
        modEventBus.addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        LOGGER.info(MODID + " mod initialized");
    }
}
