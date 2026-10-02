package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.core.jmx.Server;

public class CartUtils {

    public static void spawnChainParticles(AbstractMinecart entity) {
        Level level = entity.level();
        if (!level.isClientSide()) {
            //? if <26.1 {
            //((ServerLevel) level).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, entity.linkart$getLinkItem()), entity.getX(), entity.getY() + 0.3, entity.getZ(), 15, 0.2, 0.2, 0.2, 0.2);
            //? } else
            ((ServerLevel) level).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, entity.linkart$getLinkItem().getItem()), entity.getX(), entity.getY() + 0.3, entity.getZ(), 15, 0.2, 0.2, 0.2, 0.2);
        }
    }

    // The way vanilla looks for the rail a cart is on: the block it is in, or the one below
    public static boolean isOnRails(AbstractMinecart entity) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        return level.getBlockState(pos).getBlock() instanceof BaseRailBlock
                || level.getBlockState(pos.below()).getBlock() instanceof BaseRailBlock;
    }

    public static boolean approximatelyZero(double a) {
        return Math.abs(0 - a) < 0.00029146489604938;
    }

    public static void unlinkFromParent(AbstractMinecart entity) {
        if (entity == null) return;
        var following = entity.linkart$getFollowing();
        if (following == null) return;

        following.linkart$setFollower(null);
        entity.linkart$setFollowing(null);

        if (!entity.linkart$getLinkItem().isEmpty()) {
            //? if <=1.21.1 {
            //entity.spawnAtLocation(entity.linkart$getLinkItem());
            //?} else
            entity.spawnAtLocation((ServerLevel) entity.level(), entity.linkart$getLinkItem());
        }

        entity.linkart$setLinkItem(ItemStack.EMPTY);
    }

    public static void linkTo(AbstractMinecart minecart, AbstractMinecart to, ItemStack linkingItem) {
        minecart.linkart$setFollowing(to);
        to.linkart$setFollower(minecart);

        if (!linkingItem.isEmpty()) {
            ItemStack linkStack = linkingItem.copy();
            linkStack.setCount(1);
            minecart.linkart$setLinkItem(linkStack);
        }

        CartUtils.spawnChainParticles(minecart);
    }

    public static boolean hasFreeEnd(AbstractMinecart entity) {
        return entity.linkart$getFollowing() == null || entity.linkart$getFollower() == null;
    }

    /** The carts of the train a cart belongs to, in the order of its chain. */
    public static List<AbstractMinecart> train(AbstractMinecart cart) {
        AbstractMinecart first = cart;
        for (int i = 0; i < MAX_CARTS && first.linkart$getFollowing() != null; i++) {
            first = first.linkart$getFollowing();
        }

        List<AbstractMinecart> carts = new ArrayList<>();
        for (AbstractMinecart next = first; next != null && carts.size() < MAX_CARTS; next = next.linkart$getFollower()) {
            carts.add(next);
        }

        return carts;
    }

    // Turns the chain of a train round, so that every cart follows the one that used to follow it. The train itself
    // stays as it is.
    public static void reverse(AbstractMinecart cart) {
        List<AbstractMinecart> carts = train(cart);
        List<ItemStack> linkItems = new ArrayList<>();
        for (AbstractMinecart member : carts) linkItems.add(member.linkart$getLinkItem());

        for (int i = 0; i < carts.size(); i++) {
            AbstractMinecart member = carts.get(i);
            CartMotion motion = member.linkart$getMotion();
            Vec3 facing = motion.facing;
            boolean last = i + 1 == carts.size();

            member.linkart$setFollowing(last ? null : carts.get(i + 1));
            member.linkart$setFollower(i == 0 ? null : carts.get(i - 1));
            // A link's item is kept by the cart that follows
            member.linkart$setLinkItem(last ? ItemStack.EMPTY : linkItems.get(i + 1));
            motion.reverse(facing);
        }
    }

    private static final int MAX_CARTS = 1024;
    // A cart ahead that moved less than this in a tick is standing still
    public static final double REST = 1.0E-3;
    // Vanilla only lets a rider push a cart that is slower than this, and only by this much a tick
    private static final double HAND_SPEED = 0.1;
    private static final double HAND_PUSH = 0.001;
    // The front cart losing more than this share of its speed in one tick has hit something or is being braked
    private static final double OBSTRUCTED_SHARE = 0.75;

    // Linked carts move as one body. Every cart still runs its own vanilla physics, so a powered rail, a slope, a rider or
    // a shove acts on whichever cart it reaches, but what it does to that cart's speed is shared out over the whole train.
    // The cart at the front in the direction of travel is the one that actually goes at the train's speed; the others
    // repeat the path of the cart ahead of them (see AbstractMinecartEntityMixin). Which end is the front is decided anew
    // every tick, so a train has no fixed head and runs the same both ways.
    // This decides the speed, the direction and the order of the train a cart belongs to, once per tick.
    public static void plan(AbstractMinecart cart) {
        if (cart.linkart$getMotion().plannedThisTick()) return;
        // A crash can break the train, and what is left of it has to be looked at afresh
        if (!plan(cart, true)) plan(cart, false);
    }

    private static boolean plan(AbstractMinecart cart, boolean mayBreak) {
        CartMotion motion = cart.linkart$getMotion();

        if (cart.linkart$getFollowing() == null && cart.linkart$getFollower() == null) {
            motion.clear();
            return true;
        }

        List<AbstractMinecart> carts = train(cart);

        if (carts.size() < 2 || !carts.contains(cart)) {
            motion.clear();
            return true;
        }

        double total = 0;
        int count = 0;
        double obstructed = Double.NaN;
        AbstractMinecart stopped = null;
        double impact = 0;
        double handDriven = 0;
        int hands = 0;

        for (AbstractMinecart member : carts) {
            CartMotion state = member.linkart$getMotion();
            state.drift = 0;
            if (state.mode == CartMotion.Mode.SLAVED) continue;

            Vec3 facing = state.facing != null ? state.facing : towardsFirst(member);
            Vec3 velocity = member.getDeltaMovement();
            double actual = facing == null ? 0 : velocity.x * facing.x + velocity.z * facing.z;

            if (state.mode == CartMotion.Mode.DRIVEN) {
                // What this cart's own tick, and anything pushing it since, made of the speed it was given
                state.drift = actual - state.commanded;
                double estimate = state.trainSpeed + state.drift;
                total += estimate;

                if (state.leading && Math.abs(state.commanded) > 0.03 && actual / state.commanded < OBSTRUCTED_SHARE) {
                    obstructed = actual;
                    stopped = member;
                    impact = Math.abs(state.commanded - actual);
                }

                if (Math.abs(state.trainSpeed) < HAND_SPEED && Math.abs(actual - state.commanded) > HAND_PUSH / 2
                        && member.getFirstPassenger() instanceof Player) {
                    handDriven += estimate;
                    hands++;
                }
            } else {
                total += actual;
            }

            count++;
        }

        if (mayBreak && stopped != null && crash(stopped, impact)) return false;

        double speed;
        if (!Double.isNaN(obstructed)) {
            // The rest of the train can get no further than its front cart, whatever is still driving the carts behind
            speed = obstructed;
        } else if (hands > 0) {
            // A rider's push is tiny and meant for one cart. Shared out it is lost in the drag of the empty carts, so
            // a train being driven by hand goes the way the ridden cart alone would
            speed = handDriven / hands;
        } else if (count > 0) {
            speed = total / count;
        } else {
            speed = carts.get(0).linkart$getMotion().trainSpeed;
        }

        // The point below which vanilla stops a cart on its rail
        if (!Double.isFinite(speed) || Math.abs(speed) < 1.0E-5) speed = 0;

        int direction = speed < 0 ? -1 : 1;

        for (int i = 0; i < carts.size(); i++) {
            int ahead = i - direction;
            int trailing = direction > 0 ? carts.size() - 1 - i : i;
            carts.get(i).linkart$getMotion().assign(ahead >= 0 && ahead < carts.size() ? carts.get(ahead) : null, direction, speed, trailing);
        }

        return true;
    }

    // A link holds whatever it is pulling at up to breakSpeed, and a little less for every further cart behind it.
    // Asked for more, by a bend, a landing or a crash, the train lets go of as many carts at the back as it takes.
    // Returns the first cart let go, or null if the train held.
    public static @Nullable AbstractMinecart strain(AbstractMinecart puller, AbstractMinecart pulled, double speed) {
        double limit = LinkartConfiguration.breakSpeed / 20;
        if (limit <= 0 || speed <= 0) return null;

        CartMotion motion = pulled.linkart$getMotion();
        int allowed;

        if (speed > limit) {
            allowed = 0;
        } else if (LinkartConfiguration.breakLoadPerCart <= 0) {
            return null;
        } else {
            allowed = 1 + (int) Math.min((limit / speed - 1) / LinkartConfiguration.breakLoadPerCart, MAX_CARTS);
        }

        if (motion.trailing + 1 <= allowed) return null;

        AbstractMinecart last = puller;
        AbstractMinecart loose = pulled;

        for (int i = 0; i < allowed && loose != null; i++) {
            last = loose;
            loose = behind(loose, motion.direction);
        }

        if (loose == null) return null;

        if (loose.linkart$getFollowing() == last) {
            unlinkFromParent(loose);
        } else if (last.linkart$getFollowing() == loose) {
            unlinkFromParent(last);
        } else {
            return null;
        }

        return loose;
    }

    /** The next cart towards the back of a train going the given way along its chain. */
    public static @Nullable AbstractMinecart behind(AbstractMinecart cart, int direction) {
        return direction > 0 ? cart.linkart$getFollower() : cart.linkart$getFollowing();
    }

    // The front cart was stopped short. The carts its links can't hold back break off and carry on at the speed they
    // had, which until now was only kept for them as the train's.
    private static boolean crash(AbstractMinecart stopped, double impact) {
        int direction = stopped.linkart$getMotion().direction;
        AbstractMinecart pulled = behind(stopped, direction);
        if (pulled == null) return false;

        AbstractMinecart loose = strain(stopped, pulled, impact);
        if (loose == null) return false;

        for (int i = 0; loose != null && i < MAX_CARTS; i++) {
            CartMotion state = loose.linkart$getMotion();

            if (state.mode == CartMotion.Mode.DRIVEN && state.facing != null) {
                Vec3 velocity = loose.getDeltaMovement();
                double speed = state.trainSpeed + velocity.x * state.facing.x + velocity.z * state.facing.z - state.commanded;
                loose.setDeltaMovement(state.facing.x * speed, velocity.y, state.facing.z * speed);
            }

            state.mode = CartMotion.Mode.FREE;
            loose = behind(loose, direction);
        }

        return true;
    }

    /** Where the first cart of the chain lies, judging by the carts next to this one at the start of the tick. */
    public static @Nullable Vec3 towardsFirst(AbstractMinecart cart) {
        Vec3 position = startOf(cart);
        AbstractMinecart following = cart.linkart$getFollowing();
        Vec3 direction;

        if (following != null) {
            direction = position.vectorTo(startOf(following));
        } else {
            AbstractMinecart follower = cart.linkart$getFollower();
            if (follower == null) return null;
            direction = startOf(follower).vectorTo(position);
        }

        return horizontal(direction);
    }

    public static @Nullable Vec3 horizontal(Vec3 direction) {
        double length = direction.horizontalDistance();
        return length > 1.0E-4 ? new Vec3(direction.x / length, 0, direction.z / length) : null;
    }

    private static Vec3 startOf(AbstractMinecart cart) {
        CartMotion motion = cart.linkart$getMotion();
        return motion.tickedThisTick() ? motion.start : cart.position();
    }
}
