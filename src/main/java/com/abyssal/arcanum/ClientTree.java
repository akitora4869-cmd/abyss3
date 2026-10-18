package com.abyssal.arcanum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class ClientTree {
    private static final KeyMapping KEY = new KeyMapping("key.abyssal_arcanum.tree",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.abyssal_arcanum");
    public static void open(ArcanaNetwork.TreePacket data){Minecraft.getInstance().setScreen(new TreeScreen(data));}
    @Mod.EventBusSubscriber(modid=AbyssalArcanum.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(KEY);}
    }
    @Mod.EventBusSubscriber(modid=AbyssalArcanum.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static class Input {
        @SubscribeEvent public static void key(InputEvent.Key event){if(KEY.consumeClick() && Minecraft.getInstance().player!=null && Minecraft.getInstance().screen==null)Minecraft.getInstance().player.connection.sendCommand("arcana tree");}
    }
    private static final class TreeScreen extends Screen {
        private final ArcanaNetwork.TreePacket data;
        TreeScreen(ArcanaNetwork.TreePacket d){super(Component.literal("魔導スキルツリー"));data=d;}
        @Override protected void init(){
            int x=width/2-160,y=height/2-84;
            node(x,y,"レーヴ",data.fireball(),2,"fireball");
            node(x+110,y,"氷弾術",data.frost(),5,"frost");
            node(x+220,y,"雷撃術",data.thunder(),10,"thunder");
            node(x+55,y+30,"飛燕撃",false,3,"dive");
            node(x+165,y+30,"扇閃",false,6,"sweep");
            stat(x,y+66,"筋力",data.strength(),"strength");
            stat(x+110,y+66,"魔力",data.arcane(),"arcane");
            stat(x+220,y+66,"体力",data.vitality(),"vitality");
            stat(x,y+94,"防御力",data.defense(),"defense");
            stat(x+110,y+94,"敏捷性",data.agility(),"agility");
            stat(x+220,y+94,"最大MP",data.mana(),"mana");
            addRenderableWidget(Button.builder(Component.literal("閉じる"),b->onClose()).bounds(width/2-45,y+130,90,20).build());
        }
        private void node(int x,int y,String name,boolean learned,int level,String skill){
            boolean can=!learned && data.level()>=level && data.sp()>0;
            Button b=Button.builder(Component.literal(name+(learned?" ✓":" Lv"+level)),button->{
                Minecraft.getInstance().player.connection.sendCommand("arcana learn "+skill);
            }).bounds(x,y,100,22).build();b.active=can;addRenderableWidget(b);
        }
        private void stat(int x,int y,String name,int rank,String id){
            Button b=Button.builder(Component.literal(name+" +"+rank+" (SP1)"),button->Minecraft.getInstance().player.connection.sendCommand("arcana upgrade "+id)).bounds(x,y,100,22).build();
            b.active=data.sp()>0 && rank<30;addRenderableWidget(b);
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            renderBackground(g);
            g.drawCenteredString(font,title,width/2,height/2-112,0xD6AAFF);
            g.drawCenteredString(font,"Lv "+data.level()+"  |  SP "+data.sp()+"  |  クリックで習得",width/2,height/2-98,0xFFFFFF);
            super.render(g,mouseX,mouseY,partial);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
