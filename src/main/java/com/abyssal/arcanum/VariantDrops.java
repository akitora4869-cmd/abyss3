package com.abyssal.arcanum;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Locale;

public final class VariantDrops {
    private static final String VARIANT="ArcanaVariant";
    public static void init(){MinecraftForge.EVENT_BUS.register(new VariantDrops());}
    @SubscribeEvent public void spawn(EntityJoinLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Mob mob))return;
        if(mob.getType()!=EntityType.ZOMBIE && mob.getType()!=EntityType.SKELETON && mob.getType()!=EntityType.SPIDER && mob.getType()!=EntityType.ENDERMAN)return;
        // Only natural spawns are eligible; summoned and reloaded mobs must not reroll.
        if(!mob.getPersistentData().getBoolean("ArcanaSpawnChecked")){
            mob.getPersistentData().putBoolean("ArcanaSpawnChecked",true);
            float roll=level.random.nextFloat();String kind=roll<0.002f?"invasive":roll<0.02f?"adaptive":roll<0.06f?"berserk":"normal";
            if(!kind.equals("normal")){
                mob.getPersistentData().putString(VARIANT,kind);
                mob.setCustomName(Component.literal(switch(kind){case "invasive"->"§5侵食種 ";case "adaptive"->"§b適応種 ";default->"§6狂化種 ";}) .append(mob.getName()));
                mob.setCustomNameVisible(true);

            }
        }
        // Apply once per entity, including old saved variants, without multiplying on chunk reload.
        if (!mob.getPersistentData().getBoolean("ArcanaBalanceV1")) {
            mob.getPersistentData().putBoolean("ArcanaBalanceV1",true);
            MobBalance.apply(mob);
        }
    }
    @SubscribeEvent public void drops(LivingDropsEvent e){
        if(e.getEntity().level().isClientSide())return;
        LivingEntity mob=e.getEntity();CompoundTag tag=mob.getPersistentData();
        String kind=tag.getString(VARIANT);
        net.minecraft.world.item.Item item=switch(kind){case "berserk", "variant"->ModContent.VARIANT_ESSENCE.get();case "adaptive", "elemental"->ModContent.ELEMENTAL_CORE.get();case "invasive", "corrupted"->ModContent.CORRUPTED_ESSENCE.get();default->null;};
        if(tag.getBoolean("AbyssalBoss"))item=ModContent.ABYSS_HEART.get();
        if(item!=null){int count=tag.getBoolean("AbyssalBoss")?1:1+mob.getRandom().nextInt(2);e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(mob.level(),mob.getX(),mob.getY(),mob.getZ(),new ItemStack(item,count)));}
    }
}
