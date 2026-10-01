package com.darkgreen_world.linkart.api;

import com.darkgreen_world.linkart.utility.CartMotion;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface LinkableMinecart {

    default @Nullable AbstractMinecart linkart$getFollowing() {
        throw new IllegalStateException("Implemented via mixin");
    }

    default void linkart$setFollowing(AbstractMinecart following) {
        throw new IllegalStateException("Implemented via mixin");
    }

    default @Nullable AbstractMinecart linkart$getFollower() {
        throw new IllegalStateException("Implemented via mixin");
    }

    default void linkart$setFollower(AbstractMinecart follower) {
        throw new IllegalStateException("Implemented via mixin");
    }

    default ItemStack linkart$getLinkItem() {
        throw new IllegalStateException("Implemented via mixin");
    }

    default void linkart$setLinkItem(ItemStack linkItem) {
        throw new IllegalStateException("Implemented via mixin");
    }

    default CartMotion linkart$getMotion() {
        throw new IllegalStateException("Implemented via mixin");
    }
}
