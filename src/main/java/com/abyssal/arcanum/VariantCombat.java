package com.abyssal.arcanum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.List;

/** Server-authoritative variant combat. No client packets or block damage. */
public final class VariantCombat {
    private static final DustParticleOptions FIRE = new DustParticleOptions(new Vector3f(1f,.25f,.04f),1.25f);
    private static final DustParticleOptions ICE = new DustParticleOptions(new Vector3f(.3f,.85f,1f),1.25f);
    private static final DustParticleOptions STORM = new DustParticleOptions(new Vector3f(.8f,.6f,1f),1.25f);
    private static final DustParticleOptions ABYSS = new DustParticleOptions(new Vector3f(.52f,.1f,.9f),1.5f);
    private static final String ELEMENT = "ArcanaElement";
    private VariantCombat() {}
    public static void init() { MinecraftForge.EVENT_BUS.register(new VariantCombat()); }

    @SubscribeEvent public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        String kind=mob.getPersistentData().getString("ArcanaVariant");
        if (kind.equals("adaptive") && !mob.getPersistentData().contains(ELEMENT)) {
            String[] elements={"fire","frost","storm"};
            mob.getPersistentData().putString(ELEMENT,elements[level.random.nextInt(elements.length)]);
        }
    }

    @SubscribeEvent public void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        // Bounded nearby scan once per 10 ticks. No full-world entity iteration every tick.
        if (level.getGameTime()%10 != 0) return;
        for (Player player : level.players()) {
            if (player.isSpectator()) continue;
            List<Mob> mobs=level.getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(32),m -> !m.isDeadOrDying() && m.getPersistentData().contains("ArcanaVariant"));
            for (Mob mob : mobs) {
                if (mob.getPersistentData().getLong("ArcanaCombatTick") == level.getGameTime()) continue;
                mob.getPersistentData().putLong("ArcanaCombatTick",level.getGameTime());
                runCombat(level,mob);
            }
        }
    }

    private static void runCombat(ServerLevel level,Mob mob) {
        String kind=mob.getPersistentData().getString("ArcanaVariant");
        if (!kind.equals("berserk") && !kind.equals("adaptive") && !kind.equals("invasive")) return;
        LivingEntity target=mob.getTarget();
        if (!(target instanceof Player player) || player.isCreative() || player.isSpectator() || !target.isAlive()) return;
        double dist=mob.distanceToSqr(target);
        if (dist>28*28 || !mob.hasLineOfSight(target)) return;
        long now=level.getGameTime();
        long ready=mob.getPersistentData().getLong("ArcanaNextSkill");
        if (kind.equals("invasive")) {
            if (now%20==0) aura(level,mob,ABYSS,10);
            if(now<ready) return;
            // Teleport flank + radial pulse. Preserve existing world blocks.
            Vec3 behind=target.position().subtract(target.getLookAngle().scale(2.5));
            BlockPos pos=BlockPos.containing(behind);
            if(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty() && level.getBlockState(pos.below()).isSolidRender(level,pos.below())) {
                mob.teleportTo(behind.x,behind.y,behind.z);
            }
            aura(level,mob,ABYSS,45);
            for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(4),v -> v instanceof Player nearbyPlayer && !nearbyPlayer.isSpectator() && !nearbyPlayer.isCreative())) {
                double distance=mob.distanceTo(victim);
                if(distance<=4) {victim.hurt(ElementalDamage.source(level,mob,"abyss"),PowerStats.magic(mob,(float)(7*(1-distance/7)))); Vec3 push=victim.position().subtract(mob.position()).normalize().scale(.8);victim.push(push.x,.25,push.z);}
            }
            mob.getPersistentData().putLong("ArcanaNextSkill",now+140);
        } else if(kind.equals("adaptive")) {
            if(now<ready) return;
            String element=mob.getPersistentData().getString(ELEMENT);
            if(element.isEmpty()) {element="fire";mob.getPersistentData().putString(ELEMENT,element);}
            DustParticleOptions color=element.equals("frost")?ICE:element.equals("storm")?STORM:FIRE;
            aura(level,mob,color,32);
            // Telegraph then apply instant ranged spell; limited to 16 blocks.
            if(dist<=16*16) {
                target.hurt(ElementalDamage.source(level,mob,element),PowerStats.magic(mob,element.equals("storm")?7:5));
                switch(element) {
                    case "fire" -> target.setSecondsOnFire(4);
                    case "frost" -> {target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze()+60,target.getTicksFrozen()+90));target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,1));}
                    case "storm" -> {Vec3 v=target.position().subtract(mob.position()).normalize().scale(.6);target.push(v.x,.2,v.z);}
                }
                level.sendParticles(color,target.getX(),target.getY()+1,target.getZ(),24,.5,.7,.5,.08);
            }
            mob.getPersistentData().putLong("ArcanaNextSkill",now+100);
        } else {
            if(now<ready || dist<4*4 || dist>16*16) return;
            Vec3 direction=target.position().subtract(mob.position()).normalize();
            mob.setDeltaMovement(mob.getDeltaMovement().add(direction.scale(.85)).add(0,.16,0));
            mob.hasImpulse=true;
            aura(level,mob,FIRE,18);
            mob.getPersistentData().putLong("ArcanaNextSkill",now+90);
        }
    }
    private static void aura(ServerLevel level,Mob mob,DustParticleOptions particle,int count) {
        level.sendParticles(particle,mob.getX(),mob.getY()+mob.getBbHeight()*.6,mob.getZ(),count,mob.getBbWidth()*.6,mob.getBbHeight()*.45,mob.getBbWidth()*.6,.025);
    }
}
