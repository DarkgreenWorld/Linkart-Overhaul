package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.darkgreen_world.linkart.mixin.MinecartAccessor;
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
            ((ServerLevel) level).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, entity.linkart$getLinkItem().getItem()), entity.getX(), entity.getY() + 0.3, entity.getZ(), 15, 0.2, 0.2, 0.2, 0.2);
        }
    }

    // Rail in the cart's block or the one below, as vanilla checks
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

    // Vanilla speed limit in blocks per tick. Only valid for the front cart.
    public static double speedLimit(AbstractMinecart entity) {
        return ((MinecartAccessor) entity).linkart$getMaxSpeed((ServerLevel) entity.level());
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

    // Reverses the chain. The train itself is unaffected.
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
            // The follower holds the link item
            member.linkart$setLinkItem(last ? ItemStack.EMPTY : linkItems.get(i + 1));
            motion.reverse(facing);
        }
    }

    private static final int MAX_CARTS = 1024;
    // Per-tick travel below which a cart is standing still
    public static final double REST = 1.0E-3;
    // Vanilla rider push: HAND_PUSH per tick, only below HAND_SPEED
    private static final double HAND_SPEED = 0.1;
    private static final double HAND_PUSH = 0.001;
    // Carts * (blocks per second)^2 * breakLoadPerCart a train can take: 20 carts at 48 by default
    private static final double LINK_STRENGTH = 940;
    // The front cart is obstructed if it keeps less than this share of its speed in a tick
    private static final double OBSTRUCTED_SHARE = 0.75;
    // Any other cart is blocked if, told to go at least this fast, it covers less than this share of the way.
    // An unpowered powered rail takes half, and is no obstacle
    private static final double BLOCKED_COMMAND = 0.05;
    private static final double BLOCKED_SHARE = 0.25;

    // Plans a train's tick: its speed (the mean of what each cart's own physics made of last tick's), its direction,
    // and who follows whom. The front cart in the direction of travel leads, so a train has no fixed head.
    public static void plan(AbstractMinecart cart) {
        if (cart.linkart$getMotion().plannedThisTick()) return;
        // A crash can break the train: plan again for what is left
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
        boolean blockedForward = false;
        boolean blockedBackward = false;

        for (AbstractMinecart member : carts) {
            CartMotion state = member.linkart$getMotion();

            if (!state.leading && !state.staying && Math.abs(state.commanded) > BLOCKED_COMMAND
                    && Math.abs(state.travelled) < BLOCKED_SHARE * Math.abs(state.commanded)) {
                if (state.commanded > 0) blockedForward = true;
                else blockedBackward = true;
            }

            if (state.mode == CartMotion.Mode.SLAVED) continue;

            Vec3 facing = state.facing != null ? state.facing : towardsFirst(member);
            Vec3 velocity = member.getDeltaMovement().add(state.pushed);
            double actual = facing == null ? 0 : velocity.x * facing.x + velocity.z * facing.z;

            if (state.mode == CartMotion.Mode.DRIVEN) {
                // What this cart's physics, and anything pushing it since, made of the speed it was given
                double estimate = state.trainSpeed + actual - state.commanded;
                // Falling short of a speed that included catching up is standing still at worst, not going backwards
                double way = Math.signum(state.trainSpeed);
                if (way != 0) estimate = way * Math.max(way * estimate, Math.min(way * actual, 0));
                total += estimate;

                if (state.leading && !state.staying && Math.abs(state.commanded) > 0.03 && actual / state.commanded < OBSTRUCTED_SHARE) {
                    obstructed = actual;
                    stopped = member;
                    // Train speed can exceed the front cart's limit
                    impact = Math.min(Math.abs(state.commanded - actual), speedLimit(member));
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
            // The train goes no further than its front cart
            speed = obstructed;
        } else if (hands > 0) {
            // A rider's push is too small to share out: follow the ridden cart
            speed = handDriven / hands;
        } else if (count > 0) {
            speed = total / count;
        } else {
            speed = carts.get(0).linkart$getMotion().trainSpeed;
        }

        // A cart that can't get on holds up the whole train, so that the rest doesn't pull away from it
        if (speed > 0 && blockedForward || speed < 0 && blockedBackward) speed = 0;

        // Vanilla's standstill threshold
        if (!Double.isFinite(speed) || Math.abs(speed) < 1.0E-5) speed = 0;

        int direction = speed < 0 ? -1 : 1;

        for (int i = 0; i < carts.size(); i++) {
            int ahead = i - direction;
            int trailing = direction > 0 ? carts.size() - 1 - i : i;
            carts.get(i).linkart$getMotion().assign(ahead >= 0 && ahead < carts.size() ? carts.get(ahead) : null, direction, speed, trailing);
        }

        return true;
    }

    // Sheds carts from the back while breakLoadPerCart * carts * speed^2 exceeds LINK_STRENGTH.
    // Returns the first cart let go, or null if the train held.
    public static @Nullable AbstractMinecart strain(AbstractMinecart puller, AbstractMinecart pulled, double speed) {
        double load = LinkartConfiguration.breakLoadPerCart;
        if (load <= 0 || speed <= 0) return null;

        double perSecond = speed * 20;
        double longest = LINK_STRENGTH / (load * perSecond * perSecond);
        // Includes the pulling cart
        int allowed = (int) Math.min(Math.max(longest - 1, 0), MAX_CARTS);

        CartMotion motion = pulled.linkart$getMotion();
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

    // The front cart was stopped: carts its links can't hold break off and keep their speed.
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
