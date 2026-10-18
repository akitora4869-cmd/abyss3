package com.abyssal.arcanum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative physical skills; stored in the existing progression NBT. */
public final class CombatSkills {
    private static final String ROOT = "AbyssalProgress";
    private static CompoundTag data(ServerPlayer p) { return p.getPersistentData().getCompound(ROOT); }
    public static void init() { MinecraftForge.EVENT_BUS.register(new CombatSkills()); }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcana")
            .then(Commands.literal("learn")
                .then(Commands.literal("dive").executes(c -> learn(c.getSource().getPlayerOrException(),"dive",3)))
                .then(Commands.literal("sweep").executes(c -> learn(c.getSource().getPlayerOrException(),"sweep",6))))
            .then(Commands.literal("cast")
                .then(Commands.literal("dive").executes(c -> dive(c.getSource().getPlayerOrException())))
                .then(Commands.literal("sweep").executes(c -> sweep(c.getSource().getPlayerOrException())))));
    }
    private static int learn(ServerPlayer p,String id,int requiredLevel) {
        CompoundTag t=data(p);
        if(t.getBoolean("skill_"+id)||t.getInt("level")<requiredLevel||t.getInt("sp")<1){
            p.sendSystemMessage(Component.literal("§c習得不可：Lv"+requiredLevel+" / SP1が必要です"));return 0;
        }
        t.putBoolean("skill_"+id,true);t.putInt("sp",t.getInt("sp")-1);
        p.sendSystemMessage(Component.literal("§a習得："+(id.equals("dive")?"飛燕撃":"扇閃")));return 1;
    }
    private static boolean ready(ServerPlayer p,String id,int cooldown) {
        CompoundTag t=data(p);long now=p.serverLevel().getGameTime();
        if(!t.getBoolean("skill_"+id)){p.sendSystemMessage(Component.literal("§c未習得のスキルです"));return false;}
        if(now<t.getLong("cd_"+id)){p.sendSystemMessage(Component.literal("§cクールタイム中："+((t.getLong("cd_"+id)-now+19)/20)+"秒"));return false;}
        t.putLong("cd_"+id,now+cooldown);return true;
    }
    private static int dive(ServerPlayer p) {
        if(!p.onGround()){p.sendSystemMessage(Component.literal("§c飛燕撃は地上から使用してください"));return 0;}
        if(!ready(p,"dive",100))return 0;
        Vec3 dir=p.getLookAngle();Vec3 horizontal=new Vec3(dir.x,0,dir.z);
        if(horizontal.lengthSqr()<0.01)horizontal=new Vec3(0,0,1);
        horizontal=horizontal.normalize();
        p.setDeltaMovement(horizontal.scale(1.15).add(0,0.55,0));
        p.hurtMarked=true;
        CompoundTag t=data(p);t.putLong("diveUntil",p.serverLevel().getGameTime()+25);
        t.putBoolean("diveActive",true);t.putBoolean("diveWasAirborne",false);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD,p.getX(),p.getY()+0.2,p.getZ(),14,0.3,0.1,0.3,0.06);
        return 1;
    }
    private static int sweep(ServerPlayer p) {
        if(!ready(p,"sweep",160))return 0;
        ServerLevel world=p.serverLevel();Vec3 forward=p.getLookAngle();
        forward=new Vec3(forward.x,0,forward.z);
        if(forward.lengthSqr()<0.01)forward=new Vec3(0,0,1);
        forward=forward.normalize();Vec3 eye=p.getEyePosition();
        Set<UUID> hit=new HashSet<>();
        for(LivingEntity target:world.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(5),e->e!=p&&e.isAlive())){
            Vec3 delta=target.position().subtract(p.position());Vec3 flat=new Vec3(delta.x,0,delta.z);
            double distance=flat.length();
            if(distance>4.5||distance<0.001||Math.abs(delta.y)>2.5)continue;
            if(flat.normalize().dot(forward)<0.5)continue; // 120-degree frontal cone
            if(world.clip(new ClipContext(eye,target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            if(hit.add(target.getUUID())){
                target.hurt(world.damageSources().playerAttack(p),5.0f);
                target.push(flat.x/distance*0.55,0.12,flat.z/distance*0.55);
                target.hurtMarked=true;
            }
        }
        double base=Math.atan2(forward.z,forward.x);
        for(int i=0;i<=24;i++){
            double angle=base+Math.toRadians(-60+120.0*i/24);
            for(double radius=1.5;radius<=4.5;radius+=1.5){
                world.sendParticles(ParticleTypes.SWEEP_ATTACK,p.getX()+Math.cos(angle)*radius,p.getY()+1.1,p.getZ()+Math.sin(angle)*radius,1,0,0,0,0);
            }
        }
        return 1;
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        CompoundTag t=data(p);
        if(!t.getBoolean("diveActive"))return;
        long now=p.serverLevel().getGameTime();
        if(!p.onGround())t.putBoolean("diveWasAirborne",true);
        if((p.onGround()&&t.getBoolean("diveWasAirborne"))||now>=t.getLong("diveUntil")){
            t.putBoolean("diveActive",false);
            if(!p.onGround()||!t.getBoolean("diveWasAirborne"))return;
            ServerLevel world=p.serverLevel();
            world.sendParticles(ParticleTypes.SWEEP_ATTACK,p.getX(),p.getY()+0.3,p.getZ(),12,1.1,0.1,1.1,0.02);
            for(LivingEntity target:world.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(2.2,1.2,2.2),e->e!=p&&e.isAlive())){
                if(target.distanceToSqr(p)>6.25)continue;
                target.hurt(world.damageSources().playerAttack(p),3.0f);
            }
        }
    }
}
