package dev.chipsxo.chipscritters;



import net.minecraft.client.Minecraft;

import net.minecraft.core.component.DataComponents;

import net.minecraft.world.item.ItemStack;



/** Verified against Hypixel's public item resource. Reads only the player's own inventory slots. */

final class HauntedInventory {

    static int count(Minecraft client,String id,String name){

        if(client.player==null)return 0;int count=0;

        for(int i=0;i<=40;i++)count+=count(client.player.getInventory().getItem(i),id,name);

        return count;

    }

    static boolean matches(String actualName,String actualId,String id,String name){return name.equals(actualName.replaceAll("\u00a7.",""))&&id.equals(actualId);}

    static int count(ItemStack stack,String id,String name){

        if(stack==null||stack.isEmpty()||!stack.getHoverName().getString().replaceAll("§.","").equals(name))return 0;

        var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return 0;

        var tag=data.copyTag();String actual=tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",tag.getStringOr("id",""));

        return matches(stack.getHoverName().getString(),actual,id,name)?stack.getCount():0;

    }

}

