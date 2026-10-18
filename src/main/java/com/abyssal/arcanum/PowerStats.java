package com.abyssal.arcanum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.TickEvent;
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Unified offense progression. Every 1 point = +3% physical or +4% spell damage. */
public final class PowerStats {
    private static final String ROOT="AbyssalProgress";
    private static final String[] NAMES={"strength","arcane","vitality","defense","agility","mana"};
    public static int points(LivingEntity entity,String stat){
        if(entity instanceof Player p)return p.getPersistentData().getCompound(ROOT).getInt("stat_"+stat);
        CompoundTag n=entity.getPersistentData();
        // Non-player progression follows the mob's enhanced tier, not player SP.
        String variant=n.getString("ArcanaVariant");
        int tier=switch(variant){case "berserk" -> 2;case "adaptive" -> 4;case "invasive" -> 8;default -> 0;};
        if(n.getBoolean("AbyssBoss") || n.getBoolean("AbyssalBoss"))tier=Math.max(tier,12);
        if(stat.equals("strength"))return variant.equals("adaptive")?tier/2:tier;
        return variant.equals("berserk")?tier/2:tier;
    }
    public static float physical(LivingEntity e,float base){return base*(1f+0.03f*points(e,"strength"));}
    public static float magic(LivingEntity e,float base){return base*(1f+0.04f*points(e,"arcane"));}
    private static final UUID HP_ID=UUID.fromString("67cbd2b1-11aa-4a3e-b9ef-147e6b1f1101");
    private static final UUID ARMOR_ID=UUID.fromString("67cbd2b1-11aa-4a3e-b9ef-147e6b1f1102");
    private static final UUID SPEED_ID=UUID.fromString("67cbd2b1-11aa-4a3e-b9ef-147e6b1f1103");
    private static void apply(AttributeInstance attr,UUID id,String name,double amount,AttributeModifier.Operation op){
        if(attr==null)return;
        AttributeModifier old=attr.getModifier(id);
        if(old!=null && old.getAmount()==amount)return;
        if(old!=null)attr.removeModifier(id);
        if(amount!=0)attr.addTransientModifier(new AttributeModifier(id,name,amount,op));
    }
    public static int maxMana(Player p){return 100+Math.max(1,p.getPersistentData().getCompound(ROOT).getInt("level"))*2+points(p,"mana")*10;}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || e.player.level().isClientSide() || e.player.tickCount%20!=0)return;
        Player p=e.player;
        float health=p.getHealth();
        apply(p.getAttribute(Attributes.MAX_HEALTH),HP_ID,"Arcana Vitality",points(p,"vitality")*2.0,AttributeModifier.Operation.ADDITION);
        apply(p.getAttribute(Attributes.ARMOR),ARMOR_ID,"Arcana Defense",points(p,"defense")*0.5,AttributeModifier.Operation.ADDITION);
        apply(p.getAttribute(Attributes.MOVEMENT_SPEED),SPEED_ID,"Arcana Agility",points(p,"agility")*0.005,AttributeModifier.Operation.MULTIPLY_BASE);
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    public static void init(){MinecraftForge.EVENT_BUS.register(new PowerStats());}
    public static void register(CommandDispatcher<CommandSourceStack> d){
        for(String name:NAMES)d.register(Commands.literal("arcana").then(Commands.literal("upgrade")
            .then(Commands.literal(name).executes(c->upgrade(c.getSource().getPlayerOrException(),name)))));
    }
    private static int upgrade(ServerPlayer p,String name){
        CompoundTag t=p.getPersistentData().getCompound(ROOT);
        int rank=t.getInt("stat_"+name);
        if(t.getInt("sp")<1||rank>=30){p.sendSystemMessage(Component.literal("§cSP不足、または能力値上限(30)です"));return 0;}
        t.putInt("sp",t.getInt("sp")-1);t.putInt("stat_"+name,rank+1);
        p.sendSystemMessage(Component.literal("§a"+switch(name){case "strength"->"筋力";case "arcane"->"魔力";case "vitality"->"体力";case "defense"->"防御力";case "agility"->"敏捷性";default->"最大MP";}+" Lv."+(rank+1)));
        ArcanaNetwork.openTree(p,t);return 1;
    }
    @SubscribeEvent public void onHurt(LivingHurtEvent e){
        if(e.getSource().getEntity() instanceof LivingEntity attacker){
            // Spell hits with a magic damage type use arcane; normal hits use strength.
            if(!e.getSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) e.setAmount(physical(attacker,e.getAmount()));
        }
    }
}
