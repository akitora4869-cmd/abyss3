package com.abyssal.arcanum;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Fixed per-species health, damage and armor: no six-stat player progression on mobs. */
public final class MobBalance {
    private MobBalance() {}
    public static void apply(Mob mob) {
        // Never overwrite dedicated boss attributes.
        if (mob.getPersistentData().getBoolean("AbyssalBoss")) return;
        double[] hp;
        double[] damage;
        double[] armor;
        if (mob.getType() == EntityType.ZOMBIE) {
            hp = new double[]{30,90,110,240}; damage = new double[]{4,9,7,15}; armor = new double[]{2,5,7,12};
        } else if (mob.getType() == EntityType.SKELETON) {
            hp = new double[]{26,75,95,200}; damage = new double[]{3,7,6,12}; armor = new double[]{1,4,5,10};
        } else if (mob.getType() == EntityType.SPIDER) {
            hp = new double[]{24,70,85,190}; damage = new double[]{3,8,6,13}; armor = new double[]{1,3,5,9};
        } else if (mob.getType() == EntityType.ENDERMAN) {
            hp = new double[]{55,150,180,360}; damage = new double[]{7,13,11,20}; armor = new double[]{3,7,9,14};
        } else return;
        String kind = mob.getPersistentData().getString("ArcanaVariant");
        int tier = switch (kind) {
            case "berserk", "variant" -> 1;
            case "adaptive", "elemental" -> 2;
            case "invasive", "corrupted" -> 3;
            default -> 0;
        };
        set(mob.getAttribute(Attributes.MAX_HEALTH), hp[tier]);
        set(mob.getAttribute(Attributes.ATTACK_DAMAGE), damage[tier]);
        set(mob.getAttribute(Attributes.ARMOR), armor[tier]);
        mob.setHealth(mob.getMaxHealth());
    }
    private static void set(AttributeInstance attribute, double value) {
        if (attribute != null) attribute.setBaseValue(value);
    }
}
