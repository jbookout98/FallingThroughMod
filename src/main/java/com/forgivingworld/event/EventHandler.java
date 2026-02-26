package com.forgivingworld.event;

import com.forgivingworld.ForgivingWorldMod;
import com.forgivingworld.config.DimensionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.UUID;
// Valkyrien Skies Imports
import org.joml.Vector3dc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.joml.Vector3d;         // already there
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import com.github.litermc.vtil.api.teleport.TeleportUtil;  // ← example — replace with the real one you see
import org.joml.Vector3d;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class EventHandler {
    public static final TicketType<ChunkPos> TELEPORT_TICKET = TicketType.create("forgivingworldTP", Comparator.comparingLong(ChunkPos::toLong), 20 * 60);
    private static final Integer TP_TIME = 10;
    private static final Map<UUID, Integer> playerTpTime = new HashMap<>();
    private static final Map<UUID, Long> lastTpTime = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        final Player player = event.player;

        if (player.level().isClientSide() || player.level().getGameTime() % 20 != 0 || player.isRemoved() || event.phase == TickEvent.Phase.START) {
            return;
        }


        final Long lastTime = lastTpTime.get(player.getUUID());
        if (lastTime != null && (System.currentTimeMillis() - lastTime) < 1000L * ForgivingWorldMod.config.getCommonConfig().teleportCooldown) {
            player.sendSystemMessage(Component.literal("§7[DEBUG TICK] Still on cooldown").withStyle(ChatFormatting.GRAY));
            return;
        }

        final List<DimensionData> dimensionTPs = ForgivingWorldMod.config.getCommonConfig().dimensionConnections.get(player.level().dimension().location());

        if (dimensionTPs == null || dimensionTPs.isEmpty()) {
            return;
        }


        DimensionData tp = null;
        for (final DimensionData data : dimensionTPs) {
            boolean should = data.shouldTP(player.getY());
            if (should) {
                tp = data;
                break;
            }
        }

        if (tp == null) {
            playerTpTime.remove(player.getUUID());
            return;
        }


        if (ForgivingWorldMod.config.getCommonConfig().instantTeleport
                || player.getY() < tp.belowY && Math.abs(player.getY() - tp.belowY) > 15
                || player.getY() > tp.aboveY && Math.abs(player.getY() - tp.aboveY) > 15) {
            tryTpPlayer((ServerPlayer) player, tp);
        } else {
            int time = playerTpTime.computeIfAbsent(player.getUUID(), player2 -> 0) + 1;
            playerTpTime.put(player.getUUID(), time);

            ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 50, 1, 0.5, 1, 0.05);

            if (time == 1) {
                player.sendSystemMessage(Component.translatable((player.getY() > tp.aboveY ? "forgivingworld.pullup" : "forgivingworld.pulldown")).withStyle(ChatFormatting.DARK_AQUA));
            }

            if (time > TP_TIME) {
                if (tryTpPlayer((ServerPlayer) player, tp)) {
                    playerTpTime.remove(player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onVoidDamageRecv(final LivingHurtEvent event) {
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;

            final List<DimensionData> dimensions = ForgivingWorldMod.config.getCommonConfig().dimensionConnections.get(player.level().dimension().location());
            if (dimensions == null) return;

            for (final DimensionData data : dimensions) {
                if (player.getY() < data.belowY) {
                    if (tryTpPlayer(player, data)) {
                        event.setAmount(0);
                        break;
                    }
                }
            }
        }
    }


    private static boolean tryTpPlayer(final ServerPlayer playerEntity, DimensionData gotoDim) {
        playerEntity.sendSystemMessage(Component.literal("[DEBUG] tryTpPlayer STARTED → " + gotoDim.to + " | Player Y: " + playerEntity.getY()).withStyle(ChatFormatting.YELLOW));

        try {
            final ServerLevel world = (ServerLevel) playerEntity.level();

            ServerLevel gotoWorld = null;
            for (final ResourceKey<Level> key : world.getServer().levelKeys()) {
                if (key.location().equals(gotoDim.to)) {
                    gotoWorld = world.getServer().getLevel(key);
                    break;
                }
            }
            if (gotoWorld == null) return false;

            final double x = playerEntity.getX();
            final double z = playerEntity.getZ();

            double shipY = gotoDim.to.toString().contains("the_nether") ? 75.0 : 175.0;

            BlockPos tpPos = gotoDim.getSpawnPos(gotoWorld, x, z);
            if (tpPos != null) shipY = tpPos.getY();

            lastTpTime.put(playerEntity.getUUID(), System.currentTimeMillis());

            Ship rawShip = null;
            if (playerEntity.getVehicle() != null) rawShip = VSGameUtilsKt.getShipManagingPos(world, playerEntity.getVehicle().blockPosition());
            if (rawShip == null) rawShip = VSGameUtilsKt.getShipManagingPos(world, playerEntity.blockPosition());
            if (rawShip == null) rawShip = VSGameUtilsKt.getShipManagingPos(world, playerEntity.blockPosition().below());

            if (rawShip != null) {
                attemptShipTeleport(playerEntity, rawShip, gotoWorld, shipY, x, z);
            }

            playerEntity.teleportTo(gotoWorld, x, shipY + 2.0, z, playerEntity.getYRot(), playerEntity.getXRot());

            playerEntity.setDeltaMovement(0, -0.1, 0);
            playerEntity.setOnGround(true);
            playerEntity.fallDistance = 0;

            if (gotoDim.slowFallDuration > 0) {
                playerEntity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, gotoDim.slowFallDuration));
            }

            playerEntity.sendSystemMessage(Component.literal("§2[ ForgivingWorld ] Seamless jump — you + ship still riding at the helm!").withStyle(ChatFormatting.DARK_GREEN));
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void attemptShipTeleport(final ServerPlayer playerEntity, Ship rawShip, ServerLevel gotoWorld, double shipY, double x, double z) {
        LoadedServerShip loadedShip = null;
        if (rawShip instanceof LoadedServerShip l) {
            loadedShip = l;
        } else {
            try {
                long shipId = rawShip.getId();
                var shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(playerEntity.serverLevel());
                if (shipObjectWorld != null) {
                    loadedShip = shipObjectWorld.getLoadedShips().getById(shipId);
                }
            } catch (Exception ignored) {}
        }

        if (rawShip != null) {
            try {
                Vector3d shipTarget = new Vector3d(x, shipY, z);

                if (loadedShip != null) {
                    TeleportUtil.TeleportData data = new TeleportUtil.TeleportData(
                            gotoWorld,
                            shipTarget,
                            loadedShip.getTransform().getShipToWorldRotation(),
                            loadedShip.getVelocity(),
                            loadedShip.getOmega()
                    );
                    TeleportUtil.teleportShip(loadedShip, data);
                }

                clearSpaceAroundShip(gotoWorld, rawShip);

                playerEntity.sendSystemMessage(Component.literal("[DEBUG] Ship placed under you (auto size based on longest AABB side)").withStyle(ChatFormatting.AQUA));
            } catch (Exception ignored) {}
        }
    }

    private static void clearSpaceAroundShip(ServerLevel level, Ship ship) {
        try {
            org.joml.primitives.AABBic aabb = ship.getShipAABB();

            // Calculate longest side of the ship
            double width = aabb.maxX() - aabb.minX();
            double height = aabb.maxY() - aabb.minY();
            double depth = aabb.maxZ() - aabb.minZ();
            double longestSide = Math.max(Math.max(width, height), depth);

            int radius = (int) (longestSide / 2.0 + 3.0);

            Vector3dc center = ship.getTransform().getPositionInWorld();
            BlockPos centerPos = BlockPos.containing(center.x(), center.y(), center.z());

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = centerPos.offset(dx, dy, dz);
                        if (!level.isEmptyBlock(pos)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ultra-safe fallback for any error
            try {
                Vector3dc pos = ship.getTransform().getPositionInWorld();
                BlockPos center = BlockPos.containing(pos.x(), pos.y(), pos.z());
                for (int dx = -25; dx <= 25; dx++) {
                    for (int dy = -15; dy <= 60; dy++) {
                        for (int dz = -25; dz <= 25; dz++) {
                            BlockPos p = center.offset(dx, dy, dz);
                            if (!level.isEmptyBlock(p)) {
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static Entity dimensionTPEntity(final Entity original, final ServerLevel gotoWorld, final double x, final double y, final double z, final int slowFallDuration) {
        Entity entity = original.getType().create(gotoWorld);
        if (entity != null) {
            entity.restoreFrom(original);
            original.remove(Entity.RemovalReason.CHANGED_DIMENSION);
            if (slowFallDuration > 0 && entity instanceof Mob) {
                ((Mob) entity).addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFallDuration));
            }
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            entity.setDeltaMovement(Vec3.ZERO);
            gotoWorld.addDuringTeleport(entity);
            return entity;
        }
        return null;
    }
}