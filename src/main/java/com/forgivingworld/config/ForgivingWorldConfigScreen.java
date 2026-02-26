package com.forgivingworld.config;

import com.forgivingworld.ForgivingWorldMod;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ForgivingWorldConfigScreen extends Screen {
    private final Screen parent;
    private CommonConfiguration cfg;   // ← Class field so findConnection can see it

    public ForgivingWorldConfigScreen(Screen parent) {
        super(Component.literal("Forgiving World Config"));
        this.parent = parent;
    }

    // Helper method - now works because cfg is a class field
    private DimensionData findConnection(String fromDim, String toDim) {
        return cfg.dimensionDataList.stream()
                .filter(d -> fromDim.equals(d.from.toString()) && toDim.equals(d.to.toString()))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void init() {
        cfg = ForgivingWorldMod.config.getCommonConfig();   // ← Assign once here

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Forgiving World - Dimension Stack"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Vertical Stack Layers"));

        // ==================== Overworld → Aether ====================
        DimensionData owToAether = findConnection("minecraft:overworld", "aether:the_aether");
        if (owToAether != null) {
            category.addEntry(entryBuilder.startIntField(Component.literal("Overworld → Aether (above Y)"), owToAether.aboveY)
                    .setDefaultValue(350).setMin(0).setMax(10000)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("minecraft:overworld", "aether:the_aether", value, owToAether.belowY, owToAether.teleportToYlevel)))
                    .build());

            category.addEntry(entryBuilder.startIntField(Component.literal("Overworld → Aether Spawn Y"), owToAether.teleportToYlevel)
                    .setDefaultValue(16).setMin(0).setMax(500)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("minecraft:overworld", "aether:the_aether", owToAether.aboveY, owToAether.belowY, value)))
                    .build());
        }

        // ==================== Aether → The End ====================
        DimensionData aetherToEnd = findConnection("aether:the_aether", "minecraft:the_end");
        if (aetherToEnd != null) {
            category.addEntry(entryBuilder.startIntField(Component.literal("Aether → The End (above Y)"), aetherToEnd.aboveY)
                    .setDefaultValue(4500).setMin(0).setMax(10000)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("aether:the_aether", "minecraft:the_end", value, aetherToEnd.belowY, aetherToEnd.teleportToYlevel)))
                    .build());

            category.addEntry(entryBuilder.startIntField(Component.literal("Aether → The End Spawn Y"), aetherToEnd.teleportToYlevel)
                    .setDefaultValue(80).setMin(0).setMax(500)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("aether:the_aether", "minecraft:the_end", aetherToEnd.aboveY, aetherToEnd.belowY, value)))
                    .build());
        }

        // ==================== The End falling → Aether ====================
        DimensionData endToAether = findConnection("minecraft:the_end", "aether:the_aether");
        if (endToAether != null) {
            category.addEntry(entryBuilder.startIntField(Component.literal("The End falling → Aether (below Y)"), endToAether.belowY)
                    .setDefaultValue(0).setMin(0).setMax(10000)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("minecraft:the_end", "aether:the_aether", 255, value, endToAether.teleportToYlevel)))
                    .build());

            category.addEntry(entryBuilder.startIntField(Component.literal("The End → Aether Spawn Y"), endToAether.teleportToYlevel)
                    .setDefaultValue(130).setMin(0).setMax(500)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("minecraft:the_end", "aether:the_aether", 255, endToAether.belowY, value)))
                    .build());
        }

        // ==================== Aether falling → Overworld ====================
        DimensionData aetherToOw = findConnection("aether:the_aether", "minecraft:overworld");
        if (aetherToOw != null) {
            category.addEntry(entryBuilder.startIntField(Component.literal("Aether falling → Overworld (below Y)"), aetherToOw.belowY)
                    .setDefaultValue(0).setMin(0).setMax(10000)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("aether:the_aether", "minecraft:overworld", 255, value, aetherToOw.teleportToYlevel)))
                    .build());

            category.addEntry(entryBuilder.startIntField(Component.literal("Aether → Overworld Spawn Y"), aetherToOw.teleportToYlevel)
                    .setDefaultValue(80).setMin(0).setMax(500)
                    .setSaveConsumer(value -> ForgivingWorldMod.NETWORK.sendToServer(
                            new ConfigureUpdatePacket("aether:the_aether", "minecraft:overworld", 255, aetherToOw.belowY, value)))
                    .build());
        }

        Minecraft.getInstance().setScreen(builder.build());
    }
}