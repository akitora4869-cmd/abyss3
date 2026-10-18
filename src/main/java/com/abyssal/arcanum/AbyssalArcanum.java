package com.abyssal.arcanum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod(AbyssalArcanum.MODID)
public final class AbyssalArcanum {
    public static final String MODID = "abyssal_arcanum";
    private static final double RADIUS = 64.0;
    private static final Map<UUID, Arena> ARENAS = new HashMap<>();
    private static final DustParticleOptions AURA = new DustParticleOptions(new Vector3f(0.52f, 0.14f, 0.95f), 1.35f);
    private static final DustParticleOptions WARNING = new DustParticleOptions(new Vector3f(0.80f, 0.30f, 1.0f), 1.5f);

    public AbyssalArcanum() { MinecraftForge.EVENT_BUS.register(this); ModContent.init(); Progression.init(); ArcanaNetwork.init(); VariantDrops.init(); VariantCombat.init(); CombatSkills.init(); PowerStats.init(); ElementalDamage.init(); }

    private static final class Arena {
        final UUID bossId;
        final ServerLevel level;
        final BlockPos center;
        int ticks = 0;
        int skillCooldown = 70;
        int pendingFangs = -1;
        int shockwaveWindup = -1;
        int shockwaveStep = -1;
        long waveId = 0;
        int phase = 1;
        int emptyTicks = 0;
        final Set<UUID> participants = new HashSet<>();
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("深淵の番人・ウォーデン"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

        Arena(UUID bossId, ServerLevel level, BlockPos center) {
            this.bossId = bossId; this.level = level; this.center = center;
        }
        boolean contains(Vec3 pos) {
            double dx = pos.x - (center.getX() + .5), dz = pos.z - (center.getZ() + .5);
            return dx * dx + dz * dz <= RADIUS * RADIUS;
        }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        Progression.register(dispatcher);
        CombatSkills.register(dispatcher); PowerStats.register(dispatcher);
        dispatcher.register(Commands.literal("abyss")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("warden").executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                ServerLevel level = source.getLevel();
                BlockPos center = BlockPos.containing(source.getPosition());
                Warden boss = EntityType.WARDEN.create(level);
                if (boss == null) return 0;
                boss.moveTo(center.getX() + .5, center.getY() + 1, center.getZ() + .5, 0, 0);
                boss.setCustomName(Component.literal("§5深淵の番人・ウォーデン"));
                boss.setCustomNameVisible(true);
                boss.setPersistenceRequired();
                boss.getPersistentData().putBoolean("AbyssalBoss", true);
                boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(800.0);
                boss.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(25.0);
                boss.setHealth(800.0f);
                if (!level.addFreshEntity(boss)) return 0;
                Arena arena = new Arena(boss.getUUID(), level, center);
                ARENAS.put(boss.getUUID(), arena);
                source.sendSuccess(() -> Component.literal("深淵ウォーデンを召喚しました。半径64ブロックが戦闘領域です。"), true);
                return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                ServerLevel level = ctx.getSource().getLevel();
                int removed = 0;
                Iterator<Arena> it = ARENAS.values().iterator();
                while (it.hasNext()) {
                    Arena arena = it.next();
                    if (arena.level != level) continue;
                    Entity entity = level.getEntity(arena.bossId);
                    if (entity != null) entity.discard();
                    closeArena(arena);
                    it.remove(); removed++;
                }
                final int count = removed;
                ctx.getSource().sendSuccess(() -> Component.literal("終了した深淵戦闘: " + count), true);
                return 1;
            })));
    }

    @SubscribeEvent
    public void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Iterator<Arena> it = ARENAS.values().iterator();
        while (it.hasNext()) {
            Arena arena = it.next();
            if (arena.level != level) continue;
            Entity entity = level.getEntity(arena.bossId);
            if (!(entity instanceof Warden boss) || !boss.isAlive()) { closeArena(arena); it.remove(); continue; }
            arena.ticks++;
            arena.bar.setProgress(Math.max(0f, boss.getHealth() / boss.getMaxHealth()));
            int newPhase = boss.getHealth() <= boss.getMaxHealth() * .33f ? 3 : boss.getHealth() <= boss.getMaxHealth() * .66f ? 2 : 1;
            if (newPhase > arena.phase) {
                arena.phase = newPhase;
                arena.skillCooldown = Math.min(arena.skillCooldown, 35);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.5, boss.getZ(), 160, 3, 2, 3, .15);
                for (ServerPlayer player : arena.bar.getPlayers()) player.sendSystemMessage(Component.literal("§5深淵の番人が第" + newPhase + "段階へ移行した！"));
            }
            if (arena.ticks % 10 == 0) {
                level.sendParticles(AURA, boss.getX(), boss.getY() + 1.6, boss.getZ(), 15, 0.8, 1.1, 0.8, 0.02);
            }
            // Refresh participant membership every tick so leaving the circle restores time immediately.
            boolean occupied = false;
            for (ServerPlayer player : level.players()) {
                boolean inside = player.isAlive() && !player.isSpectator() && arena.contains(player.position());
                if (inside) {
                    occupied = true;
                    arena.participants.add(player.getUUID());
                    arena.bar.addPlayer(player);
                    if (arena.ticks % 20 == 0) player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), 18000L, false));
                } else {
                    arena.bar.removePlayer(player);
                    if (arena.participants.remove(player.getUUID())) restoreTimeIfNotInAnotherArena(player, arena);
                }
            }
            if (!occupied) arena.emptyTicks++; else arena.emptyTicks = 0;
            if (arena.emptyTicks >= 100) { boss.discard(); closeArena(arena); it.remove(); continue; }
            if (arena.shockwaveWindup >= 0 && --arena.shockwaveWindup == 0) {
                arena.shockwaveWindup = -1;
                arena.shockwaveStep = 0;
                arena.waveId = level.getGameTime();
            }
            if (arena.shockwaveStep >= 0) {
                shockwave(level, boss, arena.shockwaveStep, arena.phase, arena.waveId);
                if (++arena.shockwaveStep > 14) arena.shockwaveStep = -1;
            }
            if (arena.pendingFangs >= 0) {
                arena.pendingFangs--;
                if (arena.pendingFangs == 0) {
                    spawnFangRing(level, boss);
                    arena.pendingFangs = -1;
                }
            }
            if (--arena.skillCooldown <= 0 && arena.pendingFangs < 0) {
                arena.skillCooldown = arena.phase == 3 ? 90 : arena.phase == 2 ? 120 : 160;
                if (arena.ticks % 2 == 0) arena.pendingFangs = 24;
                else arena.shockwaveWindup = 30;
                level.sendParticles(WARNING, boss.getX(), boss.getY() + 0.15, boss.getZ(), 85, 5.5, .1, 5.5, 0.02);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.3, boss.getZ(), 55, 1.5, 1.0, 1.5, .1);
            }
        }
    }

    private static void restoreTimeIfNotInAnotherArena(ServerPlayer player, Arena departing) {
        for (Arena other : ARENAS.values()) {
            if (other != departing && other.level == player.serverLevel() && other.contains(player.position())) return;
        }
        player.connection.send(new ClientboundSetTimePacket(player.serverLevel().getGameTime(), player.serverLevel().getDayTime(), player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)));
    }

    private static void closeArena(Arena arena) {
        arena.bar.removeAllPlayers();
        for (UUID id : arena.participants) {
            ServerPlayer player = arena.level.getServer().getPlayerList().getPlayer(id);
            if (player != null) restoreTimeIfNotInAnotherArena(player, arena);
        }
        arena.participants.clear();
    }

    // Expanding annular hit detection: one hit per player per wave, with a visible expanding ring.
    private static void shockwave(ServerLevel level, Warden boss, int step, int phase, long waveId) {
        double radius = 1.5 + step * 0.85;
        int points = 36 + step * 4;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(WARNING, boss.getX() + Math.cos(angle) * radius,
                boss.getY() + .2, boss.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
            new AABB(boss.blockPosition()).inflate(15))) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
            double dx = player.getX() - boss.getX(), dz = player.getZ() - boss.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dist - radius) <= .55 && Math.abs(player.getY() - boss.getY()) < 2.0 && player.onGround()) {
                long identity = boss.getUUID().getLeastSignificantBits() ^ waveId;
                if (player.getPersistentData().getLong("AbyssShockwaveId") != identity) {
                    player.getPersistentData().putLong("AbyssShockwaveId", identity);
                    player.hurt(level.damageSources().mobAttack(boss), phase == 3 ? 13f : 9f);
                    if (dist > .01) player.push(dx / dist * 1.5, .55, dz / dist * 1.5);
                }
            }
        }
    }

    private static void spawnFangRing(ServerLevel level, Warden boss) {
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? 3.5 : 6.5;
            int count = ring == 0 ? 12 : 20;
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double x = boss.getX() + Math.cos(angle) * radius;
                double z = boss.getZ() + Math.sin(angle) * radius;
                BlockPos ground = BlockPos.containing(x, boss.getY(), z);
                for (int y = 3; y >= -4; y--) {
                    BlockPos candidate = ground.offset(0, y, 0);
                    if (level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)
                        && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()) {
                        EvokerFangs fangs = new EvokerFangs(level, x, candidate.getY(), z, (float) angle, 0, boss);
                        level.addFreshEntity(fangs);
                        break;
                    }
                }
            }
        }
    }

    private static boolean protectedAt(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        for (Arena arena : ARENAS.values()) {
            if (arena.level == level && arena.contains(Vec3.atCenterOf(pos))) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (protectedAt(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (protectedAt(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> protectedAt(event.getLevel(), pos));
    }
}
