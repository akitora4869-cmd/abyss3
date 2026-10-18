package com.abyssal.arcanum;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Elemental hit type is stored in the vanilla DamageSource, not in global mutable state. */
public final class ElementalDamage {
    private ElementalDamage() {}
    public static final String[] ELEMENTS = {"fire", "frost", "storm", "wind", "earth", "light", "dark", "abyss"};
    public static void init() { MinecraftForge.EVENT_BUS.register(new ElementalDamage()); }
    public static ResourceKey<DamageType> key(String element) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("abyssal_arcanum", "spell_"+element));
    }
    public static DamageSource source(Level level, LivingEntity caster, String element) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key(element)), caster);
    }
    public static String elementOf(DamageSource source) {
        for(String element : ELEMENTS) if(source.is(key(element))) return element;
        return "";
    }
    /** Each adaptation resists its own element; a fixed opposite element bypasses part of that resistance. */
    public static String weakness(String adapted) {
        return switch(adapted) {
            case "fire" -> "frost";
            case "frost" -> "fire";
            case "storm" -> "earth";
            case "wind" -> "storm";
            case "earth" -> "wind";
            case "light" -> "dark";
            case "dark" -> "light";
            case "abyss" -> "light";
            default -> "";
        };
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onElementalHurt(LivingHurtEvent event) {
        String incoming=elementOf(event.getSource());
        if(incoming.isEmpty()) return;
        // Abyss damage is inherently dangerous to everything without explicit abyss resistance.
        // Resistance is an explicit flag or the invasive / abyssal-boss classification.
        var tag = event.getEntity().getPersistentData();
        String kind = tag.getString("ArcanaVariant");
        boolean abyssResistant = tag.getBoolean("ArcanaAbyssResistance")
                || tag.getBoolean("AbyssalBoss")
                || kind.equals("invasive") || kind.equals("corrupted");
        float multiplier;
        if(incoming.equals("abyss")) {
            multiplier = abyssResistant ? 0.05f : 1.75f;
        } else {
            if(!(event.getEntity() instanceof Mob)) return;
            String adapted = tag.getString("ArcanaElement");
            if(kind.equals("invasive") || kind.equals("corrupted") || tag.getBoolean("AbyssalBoss")) adapted="abyss";
            if(!kind.equals("adaptive") && !kind.equals("elemental")
                    && !kind.equals("invasive") && !kind.equals("corrupted")
                    && !tag.getBoolean("AbyssalBoss")) return;
            if(adapted.isEmpty()) return;
            multiplier = incoming.equals(adapted) ? 0.20f
                    : incoming.equals(weakness(adapted)) ? 1.75f : 1f;
        }
        event.setAmount(event.getAmount()*multiplier);
    }
}
