package com.abyssal.arcanum;

import net.minecraft.world.item.*;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModContent {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AbyssalArcanum.MODID);
    public static final RegistryObject<Item> VARIANT_ESSENCE = ITEMS.register("variant_essence", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ELEMENTAL_CORE = ITEMS.register("elemental_core", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_ESSENCE = ITEMS.register("corrupted_essence", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ABYSS_HEART = ITEMS.register("abyss_heart", () -> new Item(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ABYSS_SHARD = ITEMS.register("abyss_shard", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ABYSS_WAND = ITEMS.register("abyss_wand", () -> new Item(new Item.Properties().stacksTo(1).durability(512)));
    public static final RegistryObject<Item> ABYSS_SWORD = ITEMS.register("abyss_sword", () -> new SwordItem(Tiers.DIAMOND, 5, -2.4f, new Item.Properties()));
    public static final RegistryObject<Item> ABYSS_PICKAXE = ITEMS.register("abyss_pickaxe", () -> new PickaxeItem(Tiers.DIAMOND, 2, -2.8f, new Item.Properties()));
    public static final RegistryObject<Item> ABYSS_HELMET = armor("abyss_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> ABYSS_CHESTPLATE = armor("abyss_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> ABYSS_LEGGINGS = armor("abyss_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> ABYSS_BOOTS = armor("abyss_boots", ArmorItem.Type.BOOTS);
    private static RegistryObject<Item> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ArmorItem(ArmorMaterials.NETHERITE, type, new Item.Properties()));
    }
    public static void init() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
    public static int pieces(net.minecraft.world.entity.player.Player player) {
        int count=0;
        for (ItemStack stack : player.getArmorSlots()) {
            Item item=stack.getItem();
            if (item==ABYSS_HELMET.get() || item==ABYSS_CHESTPLATE.get() || item==ABYSS_LEGGINGS.get() || item==ABYSS_BOOTS.get()) count++;
        }
        return count;
    }
}
