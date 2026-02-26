package com.forgivingworld.config;

import com.forgivingworld.ForgivingWorldMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigureUpdatePacket {

    public final String fromDim;
    public final String toDim;
    public final int aboveY;
    public final int belowY;
    public final int spawnY;

    public ConfigureUpdatePacket(String from, String to, int above, int below, int spawn) {
        this.fromDim = from;
        this.toDim = to;
        this.aboveY = above;
        this.belowY = below;
        this.spawnY = spawn;
    }

    public ConfigureUpdatePacket(FriendlyByteBuf buf) {
        this.fromDim = buf.readUtf();
        this.toDim = buf.readUtf();
        this.aboveY = buf.readInt();
        this.belowY = buf.readInt();
        this.spawnY = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(fromDim);
        buf.writeUtf(toDim);
        buf.writeInt(aboveY);
        buf.writeInt(belowY);
        buf.writeInt(spawnY);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CommonConfiguration cfg = ForgivingWorldMod.config.getCommonConfig();

            cfg.dimensionDataList.stream()
                    .filter(d -> fromDim.equals(d.from.toString()) && toDim.equals(d.to.toString()))
                    .findFirst()
                    .ifPresent(d -> {
                        d.aboveY = aboveY;
                        d.belowY = belowY;
                        d.teleportToYlevel = spawnY;
                    });

            ForgivingWorldMod.config.save(); // Saves to the actual server config file
        });
        ctx.get().setPacketHandled(true);
    }
}