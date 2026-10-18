package com.abyssal.arcanum;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

public final class ArcanaNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(AbyssalArcanum.MODID,"main"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void init() {
        CHANNEL.messageBuilder(TreePacket.class,0,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TreePacket::encode).decoder(TreePacket::decode).consumerMainThread(TreePacket::handle).add();
    }
    public static void openTree(ServerPlayer player, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new TreePacket(tag.getInt("level"),tag.getInt("sp"),tag.getBoolean("skill_fireball"),tag.getBoolean("skill_frost"),tag.getBoolean("skill_thunder"),tag.getInt("stat_strength"),tag.getInt("stat_arcane"),tag.getInt("stat_vitality"),tag.getInt("stat_defense"),tag.getInt("stat_agility"),tag.getInt("stat_mana")));
    }
    public record TreePacket(int level,int sp,boolean fireball,boolean frost,boolean thunder,int strength,int arcane,int vitality,int defense,int agility,int mana) {
        static void encode(TreePacket p,FriendlyByteBuf b){b.writeVarInt(p.level);b.writeVarInt(p.sp);b.writeBoolean(p.fireball);b.writeBoolean(p.frost);b.writeBoolean(p.thunder);b.writeVarInt(p.strength);b.writeVarInt(p.arcane);b.writeVarInt(p.vitality);b.writeVarInt(p.defense);b.writeVarInt(p.agility);b.writeVarInt(p.mana);}
        static TreePacket decode(FriendlyByteBuf b){return new TreePacket(b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());}
        static void handle(TreePacket p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->ClientTree.open(p));ctx.get().setPacketHandled(true);}
    }
}
