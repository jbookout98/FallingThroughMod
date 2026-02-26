package com.forgivingworld.config;

import com.forgivingworld.ForgivingWorldMod;
import com.forgivingworld.config.ForgivingWorldConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ForgivingWorldMod.MODID, value = Dist.CLIENT)
public class ForgivingWorldConfigCommand {

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("forgivingworld")
                .then(Commands.literal("config")
                        .executes(ctx -> {
                            // This runs only on the client — completely safe on dedicated servers
                            Minecraft.getInstance().execute(() -> {
                                Minecraft.getInstance().setScreen(new ForgivingWorldConfigScreen(null));
                            });
                            return 1;
                        })
                )
        );
    }
}