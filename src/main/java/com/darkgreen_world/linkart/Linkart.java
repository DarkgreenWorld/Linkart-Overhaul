package com.darkgreen_world.linkart;

import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartOperation;
import com.darkgreen_world.linkart.utility.CartUtils;
import com.darkgreen_world.linkart.utility.LinkartCommand;
import com.darkgreen_world.linkart.utility.LoadingCarts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Linkart implements ModInitializer {

    public static final String ID = "linkart";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final TagKey<Item> LINKERS = TagKey.create(itemKey(), Identifier.fromNamespaceAndPath(ID, "linkers"));

    public void onInitialize() {
        LinkartConfiguration.load();

        UseEntityCallback.EVENT.register(CartOperation::interact);

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AbstractMinecart minecart && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
                CartUtils.unlinkFromParent(minecart);
                CartUtils.unlinkFromParent(minecart.linkart$getFollower());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LinkartCommand.register(dispatcher);
        });

        ServerLevelEvents.LOAD.register((server, level) -> {
            if (LinkartConfiguration.chunkloading) LoadingCarts.getOrCreate(level);
        });

        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            CartMotion.advanceClock();
            if (LinkartConfiguration.chunkloading) {
                LoadingCarts.getOrCreate(level).tick(level);
            }
        });
    }

    private static ResourceKey<? extends Registry<Item>> itemKey() {
        return ResourceKey.createRegistryKey(Identifier.tryParse("item"));
    }
}
