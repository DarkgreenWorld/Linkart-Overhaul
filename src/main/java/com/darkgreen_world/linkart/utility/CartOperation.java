package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;

// The first of the two carts a player picks. What happens on the second depends only on the two carts, not on the
// order they were picked in: linked to each other they come apart, otherwise they get linked.
public record CartOperation(AbstractMinecart minecart) {

    public InteractionResult perform(AbstractMinecart other, Player player, ItemStack stack) {
        AbstractMinecart first = this.minecart;

        if (other.linkart$getFollowing() == first) {
            CartUtils.unlinkFromParent(other);
            return InteractionResult.SUCCESS;
        }

        if (first.linkart$getFollowing() == other) {
            CartUtils.unlinkFromParent(first);
            return InteractionResult.SUCCESS;
        }

        //A cart in the middle of a train has no end left to link.
        if (!CartUtils.hasFreeEnd(first) || !CartUtils.hasFreeEnd(other)) return InteractionResult.FAIL;
        //Linking the two ends of one train makes a ring, with no front to lead it. An Ouroboros, if you will.
        if (CartUtils.train(first).contains(other)) return InteractionResult.FAIL;
        if (Math.abs(other.distanceTo(first) - 1) > LinkartConfiguration.pathfindingDistance)
            return InteractionResult.FAIL; //Linking beyond pathfindingDistance, will just break on first tick.

        //A chain runs one way, each cart following the next. Which way means nothing to the train, so either train
        //is turned round if that is what it takes for the two free ends to fit.
        if (first.linkart$getFollower() != null) CartUtils.reverse(first);
        if (other.linkart$getFollowing() != null) CartUtils.reverse(other);

        CartUtils.linkTo(other, first, stack);
        //The link keeps one of the stack, to drop when it comes apart.
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
