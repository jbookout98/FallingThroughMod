package com.forgivingworld.config;

import com.forgivingworld.ForgivingWorldMod;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ForgivingWorldConfigScreen extends Screen {
    private final Screen parent;
    private CommonConfiguration cfg;
    private List<DimensionData> tempList;

    public ForgivingWorldConfigScreen(Screen parent) {
        super(Component.literal("Forgiving World Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cfg = ForgivingWorldMod.config.getCommonConfig();
        tempList = new ArrayList<>(cfg.dimensionDataList);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Forgiving World - Dimension Stack"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Layers (Top = First)"));

        for (int i = 0; i < tempList.size(); i++) {
            final int index = i;
            DimensionData data = tempList.get(i);

            String label = data.from + " → " + data.to;
            category.addEntry(entryBuilder.startTextDescription(Component.literal("§e" + label)).build());

            // From Dimension
            category.addEntry(entryBuilder.startStrField(Component.literal("   From Dimension"), data.from.toString())
                    .setDefaultValue(data.from.toString())
                    .setSaveConsumer(str -> {
                        data.from = new ResourceLocation(str);
                        saveChanges();
                    })
                    .build());

            // To Dimension
            category.addEntry(entryBuilder.startStrField(Component.literal("   To Dimension"), data.to.toString())
                    .setDefaultValue(data.to.toString())
                    .setSaveConsumer(str -> {
                        data.to = new ResourceLocation(str);
                        saveChanges();
                    })
                    .build());

            // Above Y
            category.addEntry(entryBuilder.startIntField(Component.literal("   Above Y (fly up)"), data.aboveY)
                    .setDefaultValue(data.aboveY).setMin(-1000).setMax(10000)
                    .setSaveConsumer(value -> { data.aboveY = value; saveChanges(); })
                    .build());

            // Below Y
            category.addEntry(entryBuilder.startIntField(Component.literal("   Below Y (fall down)"), data.belowY)
                    .setDefaultValue(data.belowY).setMin(-1000).setMax(10000)
                    .setSaveConsumer(value -> { data.belowY = value; saveChanges(); })
                    .build());

            // Spawn Y
            category.addEntry(entryBuilder.startIntField(Component.literal("   Spawn Y (landing height)"), data.teleportToYlevel)
                    .setDefaultValue(data.teleportToYlevel).setMin(-1000).setMax(10000)
                    .setSaveConsumer(value -> { data.teleportToYlevel = value; saveChanges(); })
                    .build());

            // Reorder and Remove buttons using your ButtonEntry
            category.addEntry(new ButtonEntry(Component.literal("↑ Move Up"), () -> {
                if (index > 0) {
                    swapLayers(index, index - 1);
                }
            }));

            category.addEntry(new ButtonEntry(Component.literal("↓ Move Down"), () -> {
                if (index < tempList.size() - 1) {
                    swapLayers(index, index + 1);
                }
            }));

            category.addEntry(new ButtonEntry(Component.literal("Remove Layer"), () -> {
                tempList.remove(index);
                saveChanges();
                Minecraft.getInstance().setScreen(new ForgivingWorldConfigScreen(parent));
            }));
        }

        // Add New Layer
        category.addEntry(new ButtonEntry(Component.literal("Add New Layer"), () -> {
            DimensionData newLayer = new DimensionData(
                    new ResourceLocation("minecraft:overworld"),
                    new ResourceLocation("minecraft:overworld"),
                    DimensionData.SPAWNTYPE.AIR
            );
            newLayer.aboveY = 1000;
            newLayer.belowY = -64;
            newLayer.teleportToYlevel = 70;
            newLayer.slowFallDuration = 400;

            tempList.add(newLayer);
            saveChanges();
            Minecraft.getInstance().setScreen(new ForgivingWorldConfigScreen(parent));
        }));

        Minecraft.getInstance().setScreen(builder.build());
    }

    private void swapLayers(int i, int j) {
        DimensionData temp = tempList.get(i);
        tempList.set(i, tempList.get(j));
        tempList.set(j, temp);
        saveChanges();
        Minecraft.getInstance().setScreen(new ForgivingWorldConfigScreen(parent));
    }

    private void saveChanges() {
        cfg.dimensionDataList.clear();
        cfg.dimensionDataList.addAll(tempList);
        cfg.dimensionConnections.clear();
        for (DimensionData d : cfg.dimensionDataList) {
            cfg.dimensionConnections.computeIfAbsent(d.from, k -> new ArrayList<>()).add(d);
        }
        ForgivingWorldMod.config.save();
    }
}