package com.forgivingworld.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ButtonEntry extends AbstractConfigListEntry<Void> {
    private final Button button;

    public ButtonEntry(Component title, Runnable action) {
        super(title, false);
        this.button = Button.builder(title, b -> action.run()).bounds(0, 0, 150, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float partialTicks) {

        this.button.setX(x + entryWidth / 2 - 75);
        this.button.setY(y);
        this.button.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.singletonList(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return Collections.singletonList(button);
    }

    @Override
    public Void getValue() { return null; }
    @Override
    public Optional<Void> getDefaultValue() { return Optional.empty(); }
    @Override
    public void save() {}
}