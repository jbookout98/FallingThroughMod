package com.forgivingworld.event;

import com.forgivingworld.ForgivingWorldMod;
import com.forgivingworld.config.DimensionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
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
        if (playerEntity == null || gotoDim == null) return false;

        ServerLevel gotoWorld = playerEntity.server.getLevel(ResourceKey.create(Registries.DIMENSION, gotoDim.to));
        if (gotoWorld == null) {
            playerEntity.sendSystemMessage(Component.literal("[DEBUG] Target dimension not found").withStyle(ChatFormatting.RED));
            return false;
        }

        double targetSpawnY = gotoDim.teleportToYlevel;

        playerEntity.sendSystemMessage(Component.literal("§a[DEBUG] GUI Spawn Y = " + targetSpawnY).withStyle(ChatFormatting.GREEN));

        Ship rawShip = null;

        // 1. If the player is actively sitting in the helm/seat, grab it immediately
        if (playerEntity.getVehicle() != null) {
            rawShip = VSGameUtilsKt.getShipManagingPos(playerEntity.level(), playerEntity.getVehicle().blockPosition());
        }

        // 2. MATHEMATICAL BOUNDING BOX CHECK
        if (rawShip == null) {
            try {
                var shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(playerEntity.serverLevel());
                if (shipObjectWorld != null) {
                    Vector3d playerPosWorld = new Vector3d(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ());

                    // Loop through all loaded ships in the dimension
                    for (LoadedServerShip ship : shipObjectWorld.getLoadedShips()) {
                        // Transform player's Overworld position into the Ship's coordinate space
                        Vector3d playerPosShip = ship.getWorldToShip().transformPosition(new Vector3d(playerPosWorld));
                        AABBic shipAABB = ship.getShipAABB();

                        // Check if player is inside the ship's 3D box (with a 2-block buffer for jumping/lag)
                        double buffer = 2.0;
                        boolean isOnShip =
                                playerPosShip.x >= (shipAABB.minX() - buffer) && playerPosShip.x <= (shipAABB.maxX() + buffer) &&
                                        playerPosShip.y >= (shipAABB.minY() - buffer) && playerPosShip.y <= (shipAABB.maxY() + buffer) &&
                                        playerPosShip.z >= (shipAABB.minZ() - buffer) && playerPosShip.z <= (shipAABB.maxZ() + buffer);

                        if (isOnShip) {
                            rawShip = ship;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }


        // Teleport the ship using the search (always looks lower)
        if (rawShip != null) {
            // 1. Calculate driver's relative position BEFORE the ship moves
            Vector3dc oldShipPos = rawShip.getTransform().getPositionInWorld();
            Vec3 relativeOffset = playerEntity.position().subtract(oldShipPos.x(), oldShipPos.y(), oldShipPos.z());

            // 2. Teleport ship and get the actual new position
            Vector3dc newShipPos = attemptShipTeleport(playerEntity, rawShip, gotoWorld, targetSpawnY, playerEntity.getX(), playerEntity.getZ());

            if (newShipPos != null) {
                // 3. Teleport driver exactly relative to the new ship position
                playerEntity.teleportTo(gotoWorld,
                        newShipPos.x() + relativeOffset.x,
                        newShipPos.y() + relativeOffset.y,
                        newShipPos.z() + relativeOffset.z,
                        playerEntity.getYRot(),
                        playerEntity.getXRot());

                playerEntity.fallDistance = 0;
                playerEntity.setDeltaMovement(0, -0.1, 0);
                return true;
            }
        }

        // FALLBACK: If the player teleported WITHOUT a ship
        playerEntity.teleportTo(gotoWorld,
                playerEntity.getX(),
                targetSpawnY + 1.62,   // Only use this absolute Y if they have no ship!
                playerEntity.getZ(),
                playerEntity.getYRot(),
                playerEntity.getXRot());

        playerEntity.fallDistance = 0;
        playerEntity.setDeltaMovement(0, -0.1, 0);

        return true;
    }
    private BlockPos findSuitableSpawnPos(ServerLevel targetWorld, double targetX, double targetZ, AABBic shipAABB) {
        // Starting height (your config spawnY or a safe default)
        int baseY = 120;

        // Try several Y levels around the desired height
        for (int offset = 0; offset <= 80; offset += 8) { // try ±0, ±8, ±16, ... up to ±80
            for (int sign : new int[] {1, -1}) { // try above then below
                int testY = baseY + sign * offset;

                double shiftedMinY = shipAABB.minY() + (testY - shipAABB.minY()); // assuming shipAABB is centered at 0,0,0
                double shiftedMaxY = shipAABB.maxY() + (testY - shipAABB.minY());

                BlockPos testPos = BlockPos.containing(targetX, testY, targetZ);

                if (isSpaceClear(targetWorld, testPos, shipAABB)) {
                    return testPos;
                }
            }
        }

        // Ultimate fallback: just use the config height and clear anyway
        return BlockPos.containing(targetX, baseY, targetZ);
    }

    private boolean isSpaceClear(ServerLevel world, BlockPos center, AABBic shipAABB) {
        int minX = (int) Math.floor(center.getX() + shipAABB.minX());
        int minY = center.getY() + (int) Math.floor(shipAABB.minY());
        int minZ = (int) Math.floor(center.getZ() + shipAABB.minZ());
        int maxX = (int) Math.ceil(center.getX() + shipAABB.maxX());
        int maxY = center.getY() + (int) Math.ceil(shipAABB.maxY());
        int maxZ = (int) Math.ceil(center.getZ() + shipAABB.maxZ());

        // Quick check: make sure the area is loaded
        if (!world.hasChunk(minX >> 4, minZ >> 4) || !world.hasChunk(maxX >> 4, maxZ >> 4)) {
            return false;
        }

        // Check for solid blocks in the ship's volume
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isEmptyBlock(pos)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static Vector3dc attemptShipTeleport(final ServerPlayer playerEntity, Ship rawShip, ServerLevel gotoWorld, double shipY, double x, double z) {
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

        if (rawShip == null) return null;

        try {
            AABBic shipAABB = rawShip.getShipAABB();
            BlockPos suitablePos = findSuitableSpawnPos(gotoWorld, x, z, shipAABB, shipY);

            Vector3d shipTarget = new Vector3d(suitablePos.getX() + 0.5, suitablePos.getY(), suitablePos.getZ() + 0.5);

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

            clearSmallBuffer(gotoWorld, suitablePos);
            teleportPassengersOnShip(playerEntity, rawShip, playerEntity.serverLevel(), gotoWorld, shipTarget);

            //playerEntity.sendSystemMessage(Component.literal("[DEBUG] Ship placed safely at Y=" + suitablePos.getY() + " (searched for space)").withStyle(ChatFormatting.AQUA));

            return shipTarget; // RETURN THE NEW SHIP TARGET HERE

        } catch (Exception e) {
            return null; // Return null on failure
        }
    }


    // Checks if the ship's bounding box fits at this position
    private static BlockPos findSuitableSpawnPos(ServerLevel world, double x, double z, AABBic shipAABB, double preferredY) {
        int baseY = (int) preferredY;

        // 1. Try the exact Spawn Y from GUI first
        if (isSpaceClear(world, x, baseY, z, shipAABB)) {
            return BlockPos.containing(x, baseY, z);
        }

        // 2. Always prefer looking LOWER (this is what you asked for)
        for (int offset = 6; offset <= 120; offset += 6) {   // checks -6, -12, -18 ... down to -120
            int testY = baseY - offset;
            if (isSpaceClear(world, x, testY, z, shipAABB)) {
                return BlockPos.containing(x, testY, z);
            }
        }

        return BlockPos.containing(x, baseY, z);
    }

    private static boolean isSpaceClear(ServerLevel world, double x, int y, double z, AABBic shipAABB) {
        int minX = (int) Math.floor(x + shipAABB.minX());
        int minY = y + (int) Math.floor(shipAABB.minY());
        int minZ = (int) Math.floor(z + shipAABB.minZ());
        int maxX = (int) Math.ceil(x + shipAABB.maxX());
        int maxY = y + (int) Math.ceil(shipAABB.maxY());
        int maxZ = (int) Math.ceil(z + shipAABB.maxZ());

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (!world.isEmptyBlock(new BlockPos(bx, by, bz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void clearSmallBuffer(ServerLevel level, BlockPos pos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 12; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (!level.isEmptyBlock(p)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
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
    private static void teleportPassengersOnShip(ServerPlayer driver, Ship ship, ServerLevel sourceWorld, ServerLevel targetWorld, Vector3d shipTarget) {

        AABBic shipAABB = ship.getShipAABB();

        // Calculate the maximum dimensions of the ship
        double sizeX = shipAABB.maxX() - shipAABB.minX();
        double sizeY = shipAABB.maxY() - shipAABB.minY();
        double sizeZ = shipAABB.maxZ() - shipAABB.minZ();

        double maxExtents = (Math.max(sizeX, Math.max(sizeY, sizeZ)) / 2.0) + 5.0;

        Vector3dc worldCenter = ship.getTransform().getPositionInWorld();

        // Create a vanilla Minecraft AABB in the Dimension
        AABB searchBox = new AABB(
                worldCenter.x() - maxExtents,
                worldCenter.y() - maxExtents,
                worldCenter.z() - maxExtents,
                worldCenter.x() + maxExtents,
                worldCenter.y() + maxExtents,
                worldCenter.z() + maxExtents
        );

        // Ask the server to ONLY give us players inside this specific box
        List<ServerPlayer> nearbyPlayers = sourceWorld.getEntitiesOfClass(ServerPlayer.class, searchBox);

        // === 2. NARROW-PHASE: Loop ONLY through the nearby players ===
        for (ServerPlayer p : nearbyPlayers) {
            if (p == driver) continue;

            // Get the player's current world position
            Vector3d playerPosWorld = new Vector3d(p.getX(), p.getY(), p.getZ());

            // Transform the player's position into the Ship's local space
            Vector3d playerPosShip = ship.getWorldToShip().transformPosition(playerPosWorld);

            // Check if that local position is inside the ship's exact shipyard AABB
            double buffer = 1.5;
            boolean isOnShip =
                    playerPosShip.x >= (shipAABB.minX() - buffer) && playerPosShip.x <= (shipAABB.maxX() + buffer) &&
                            playerPosShip.y >= (shipAABB.minY() - buffer) && playerPosShip.y <= (shipAABB.maxY() + buffer) &&
                            playerPosShip.z >= (shipAABB.minZ() - buffer) && playerPosShip.z <= (shipAABB.maxZ() + buffer);

            if (isOnShip) {
                // Convert JOML Vector3dc to Minecraft Vec3
                Vector3dc oldShipPos = ship.getTransform().getPositionInWorld();
                Vec3 oldShipVec = new Vec3(oldShipPos.x(), oldShipPos.y(), oldShipPos.z());

                // Calculate where the player should end up relative to the new ship center
                Vec3 relative = p.position().subtract(oldShipVec);
                Vec3 newPos = new Vec3(shipTarget.x, shipTarget.y, shipTarget.z).add(relative);

                // Teleport the player
                p.teleportTo(targetWorld, newPos.x, newPos.y, newPos.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                p.setDeltaMovement(0, -0.1, 0);
                p.sendSystemMessage(Component.literal("§2You teleported with the ship!").withStyle(ChatFormatting.DARK_GREEN));
            }
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