package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.Linkart;
import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

// The first of two picked carts. Linked carts get unlinked, others linked; the order doesn't matter.
public record CartOperation(AbstractMinecart minecart) {

    private static final Map<Player, CartOperation> OPERATIONS = new WeakHashMap<>();

    public static InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
        if (level.isClientSide() || !(entity instanceof AbstractMinecart minecart)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        // Vanilla interaction unless sneaking
        if (!stack.is(Linkart.LINKERS) || !player.isShiftKeyDown()) return InteractionResult.PASS;

        CartOperation operation = OPERATIONS.remove(player);
        InteractionResult result;
        if (operation == null) {
            OPERATIONS.put(player, new CartOperation(minecart));
            result = InteractionResult.SUCCESS;
        } else if (operation.minecart() != minecart && minecart.isAlive() && operation.minecart().isAlive()) {
            result = operation.perform(minecart, player, stack);
        } else {
            result = InteractionResult.FAIL;
        }

        ((ServerLevel) level).sendParticles(result.consumesAction() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
                minecart.getX(), minecart.getY() + 0.2, minecart.getZ(), 10, 0.5, 0.5, 0.5, 0.5);
        return result;
    }

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
        //Linking a train to itself makes a ring. An Ouroboros, if you will.
        if (CartUtils.train(first).contains(other)) return InteractionResult.FAIL;
        if (Math.abs(other.distanceTo(first) - 1) > LinkartConfiguration.pathfindingDistance)
            return InteractionResult.FAIL; //Linking beyond pathfindingDistance, will just break on first tick.

        //Reverse either chain if needed for the free ends to fit.
        if (first.linkart$getFollower() != null) CartUtils.reverse(first);
        if (other.linkart$getFollowing() != null) CartUtils.reverse(other);

        CartUtils.linkTo(other, first, stack);
        //The link keeps one item, dropped when it comes apart.
        if (!player.isCreative()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
