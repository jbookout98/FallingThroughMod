package com.forgivingworld;

import com.cupboard.config.CupboardConfig;
import com.forgivingworld.config.*;
import com.forgivingworld.config.ConfigureUpdatePacket;   // ← your packet class
import com.forgivingworld.event.EventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import java.util.Random;

@Mod(ForgivingWorldMod.MODID)
public class ForgivingWorldMod
{
    public static final String MODID = "forgivingworld";
    public static final Logger LOGGER = LogManager.getLogger();

    public static CupboardConfig<CommonConfiguration> config = new CupboardConfig<>(MODID, new CommonConfiguration());
    public static Random rand = new Random();

    // === NETWORK CHANNEL (this fixes the NETWORK error) ===
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "network"),
            () -> "1",
            s -> true,
            s -> true
    );

    public ForgivingWorldMod()
    {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> "", (c, b) -> true));

        Mod.EventBusSubscriber.Bus.FORGE.bus().get().register(EventHandler.class);

        // Register the packet
        NETWORK.registerMessage(0, ConfigureUpdatePacket.class,
                ConfigureUpdatePacket::encode,
                ConfigureUpdatePacket::new,
                ConfigureUpdatePacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        LOGGER.info(MODID + " mod initialized");
    }
}