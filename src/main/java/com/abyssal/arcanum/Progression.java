package com.abyssal.arcanum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

public final class Progression {
    private static final String KEY="AbyssalProgress";
    private static final String[] SKILLS={"fireball","frost","thunder"};
    private static CompoundTag data(Player p) {
        CompoundTag d=p.getPersistentData();
        if (!d.contains(KEY)) {
            CompoundTag init=new CompoundTag(); init.putInt("level",1); init.putInt("mp",100); init.putInt("sp",0);
            d.put(KEY,init);
        }
        return d.getCompound(KEY);
    }
    private static int cost(int level) {return 40+level*level*12;}
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("arcana")
            .then(Commands.literal("tree").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException(); CompoundTag t=data(p); ArcanaNetwork.openTree(p,t); return 1;}))
            .then(Commands.literal("status").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException(); CompoundTag t=data(p);
                p.sendSystemMessage(Component.literal("§dArcana Lv."+t.getInt("level")+" XP:"+t.getInt("xp")+"/"+cost(t.getInt("level"))+" SP:"+t.getInt("sp")+" 筋力:"+t.getInt("stat_strength")+" 魔力:"+t.getInt("stat_arcane")+" 体力:"+t.getInt("stat_vitality")+" 防御:"+t.getInt("stat_defense")+" 敏捷:"+t.getInt("stat_agility")+" MP:"+t.getInt("mp")+"/"+PowerStats.maxMana(p)));return 1;}))
            .then(Commands.literal("learn")
                .then(Commands.literal("fireball").executes(c->learn(c.getSource().getPlayerOrException(),"fireball",2)))
                .then(Commands.literal("frost").executes(c->learn(c.getSource().getPlayerOrException(),"frost",5)))
                .then(Commands.literal("thunder").executes(c->learn(c.getSource().getPlayerOrException(),"thunder",10))))
            .then(Commands.literal("cast")
                .then(Commands.literal("fireball").executes(c->cast(c.getSource().getPlayerOrException(),"fireball")))
                .then(Commands.literal("frost").executes(c->cast(c.getSource().getPlayerOrException(),"frost")))
                .then(Commands.literal("thunder").executes(c->cast(c.getSource().getPlayerOrException(),"thunder")))));
    }
    private static int learn(ServerPlayer p,String skill,int minLevel) {
        CompoundTag t=data(p);
        if(t.getBoolean("skill_"+skill) || t.getInt("level")<minLevel || t.getInt("sp")<1){p.sendSystemMessage(Component.literal("§c習得不可: 必要Lv "+minLevel+" / SP 1"));return 0;}
        t.putBoolean("skill_"+skill,true);t.putInt("sp",t.getInt("sp")-1);
        p.sendSystemMessage(Component.literal("§a習得: "+skill));ArcanaNetwork.openTree(p,t);return 1;
    }
    private static int cast(ServerPlayer p,String skill) {
        CompoundTag t=data(p);int mp=skill.equals("thunder")?35:20;
        long now=p.serverLevel().getGameTime();String cd="cd_"+skill;
        if(!t.getBoolean("skill_"+skill) || t.getInt("mp")<mp || now<t.getLong(cd)) {p.sendSystemMessage(Component.literal("§c魔法を使用できません（未習得・MP不足・クールタイム）"));return 0;}
        t.putInt("mp",t.getInt("mp")-mp);t.putLong(cd,now+100);
        ServerLevel level=p.serverLevel();Vec3 eye=p.getEyePosition();Vec3 dir=p.getLookAngle();
        DustParticleOptions particle=new DustParticleOptions(new Vector3f(skill.equals("fireball")?1f:0.45f,skill.equals("frost")?0.8f:0.2f,1f),1.5f);
        LivingEntity target=null;double nearest=24;
        for(LivingEntity mob:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(24),e->e!=p && e.isAlive())) {
            Vec3 delta=mob.getBoundingBox().getCenter().subtract(eye);double forward=delta.dot(dir);
            if(forward<=0 || forward>24)continue;
            double miss=delta.subtract(dir.scale(forward)).length();
            if(miss<1.6 && forward<nearest && level.clip(new net.minecraft.world.level.ClipContext(eye,mob.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS){target=mob;nearest=forward;}
        }
        double reach=target==null?16:nearest;
        for(int i=0;i<35;i++){Vec3 at=eye.add(dir.scale(reach*i/35));level.sendParticles(particle,at.x,at.y,at.z,1,0,0,0,0);}
        if(target!=null){target.hurt(ElementalDamage.source(level,p,skill.equals("fireball")?"fire":skill.equals("frost")?"frost":"storm"),PowerStats.magic(p,skill.equals("thunder")?16:10));
            if(skill.equals("fireball"))target.setSecondsOnFire(4);
            if(skill.equals("frost"))target.setTicksFrozen(Math.max(target.getTicksFrozen(),120));}
        return 1;
    }
    @SubscribeEvent public void death(LivingDeathEvent e){
        if(e.getEntity().level().isClientSide())return;
        if(e.getSource().getEntity() instanceof ServerPlayer p && e.getEntity()!=p){
            CompoundTag t=data(p);int level=t.getInt("level");int xp=t.getInt("xp")+(e.getEntity().getMaxHealth()>=100?75:15);
            while(level<100 && xp>=cost(level)){xp-=cost(level);level++;t.putInt("sp",t.getInt("sp")+(level%5==0?2:1));p.sendSystemMessage(Component.literal("§dLevel Up! Lv."+level));}
            t.putInt("level",level);t.putInt("xp",xp);
        }
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone e){if(e.isWasDeath()){
        CompoundTag old=e.getOriginal().getPersistentData();if(old.contains(KEY))e.getEntity().getPersistentData().put(KEY,old.getCompound(KEY).copy());
    }}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || e.player.level().isClientSide())return;
        if(e.player.tickCount%20==0){CompoundTag t=data(e.player);t.putInt("mp",Math.min(PowerStats.maxMana(e.player),t.getInt("mp")+2));}
    }
    @SubscribeEvent public void hurt(LivingHurtEvent e){
        if(e.getEntity() instanceof Player p && ModContent.pieces(p)>=2 && e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))e.setAmount(e.getAmount()*0.85f);
        if(e.getSource().getEntity() instanceof Player p && ModContent.pieces(p)==4)e.setAmount(e.getAmount()*1.25f);
    }
    public static void init(){MinecraftForge.EVENT_BUS.register(new Progression());}
}
