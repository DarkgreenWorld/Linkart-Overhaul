package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.api.LinkableMinecart;
import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartUtils;
import com.darkgreen_world.linkart.utility.CollisionUtils;
import com.darkgreen_world.linkart.utility.LoadingCarts;
import net.minecraft.core.UUIDUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@Mixin({AbstractMinecart.class})
public abstract class AbstractMinecartEntityMixin extends Entity implements LinkableMinecart {

    @Unique private AbstractMinecart linkart$following;
    @Unique private AbstractMinecart linkart$follower;
    @Unique private UUID linkart$followingUUID;
    @Unique private UUID linkart$followerUUID;
    @Unique private ItemStack linkart$itemStack = ItemStack.EMPTY;
    @Unique private final CartMotion linkart$motion = new CartMotion();
    @Unique private Vec3 linkart$beforePush;

    // Spacing error a parked train ignores
    @Unique private static final double PARKED_SLACK = 0.08;
    // Speed at which piled-up carts move apart
    @Unique private static final double SPREAD_SPEED = 0.3;
    // Gap error beyond which a cart may exceed its speed limit to catch up
    @Unique private static final double CATCH_UP_SLACK = 0.05;

    public AbstractMinecartEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void linkart$tick(CallbackInfo ci) {
        if (level().isClientSide()) return;
        AbstractMinecart cast = (AbstractMinecart) (Object) this;
        CartMotion motion = this.linkart$motion;

        CartUtils.plan(cast);
        // Not part of a train
        if (!motion.plannedThisTick()) return;

        boolean onRails = CartUtils.isOnRails(cast);
        motion.begin(position(), onRails, !onRails && !onGround());

        if (motion.ahead == null) {
            linkart$lead(cast);
        } else {
            CartMotion other = motion.ahead.linkart$getMotion();
            motion.trainTravel = other.tickedThisTick() ? other.trainTravel : 0;

            if (motion.startedOnRails) {
                linkart$follow(cast, motion.ahead);
            } else {
                linkart$chase(cast, motion.ahead);
            }
        }

        if (LinkartConfiguration.chunkloading && linkart$getFollowing() != null) {
            if (linkart$getFollower() != null && !CartUtils.approximatelyZero(this.getDeltaMovement().length())) {
                ((ServerLevel) this.level()).getChunkSource().addTicketWithRadius(TicketType.PORTAL, this.chunkPosition(), LinkartConfiguration.chunkloadingRadius);
                LoadingCarts.getOrCreate((ServerLevel) this.level()).addCart(cast);
            } else {
                LoadingCarts.getOrCreate((ServerLevel) this.level()).removeCart(cast);
            }
        }
    }

    // The front cart runs at the train's speed and is otherwise vanilla.
    @Unique
    private void linkart$lead(AbstractMinecart cast) {
        CartMotion motion = this.linkart$motion;
        // Off the rails: left to vanilla
        if (!motion.startedOnRails) return;

        Vec3 facing = motion.facing != null ? motion.facing : CartUtils.towardsFirst(cast);
        if (facing == null) return;

        double speed = motion.trainSpeed != 0 ? motion.trainSpeed : linkart$hold();
        setDeltaMovement(facing.x * speed, getDeltaMovement().y, facing.z * speed);
        motion.drive(speed);
    }

    // The speed that keeps a parked cart where it is, against a slope or its own engine.
    @Unique
    private double linkart$hold() {
        this.linkart$motion.staying = true;
        return this.linkart$motion.hold;
    }

    // Repeats the path of the cart ahead, which ticked first (see ServerLevelMixin), and closes the spacing error.
    @Unique
    private void linkart$follow(AbstractMinecart cast, AbstractMinecart ahead) {
        CartMotion motion = this.linkart$motion;
        CartMotion other = ahead.linkart$getMotion();
        boolean aheadMoved = other.tickedThisTick();
        double vertical = getDeltaMovement().y;
        // Track direction of travel
        Vec3 forward = motion.facing == null ? null : motion.direction > 0 ? motion.facing : motion.facing.reverse();

        if (!(aheadMoved ? other.startedOnRails : CartUtils.isOnRails(ahead))) {
            // Cart ahead has left the rails: carry on at train speed
            Vec3 toAhead = position().vectorTo(ahead.position());

            if (toAhead.length() - LinkartConfiguration.distance - linkart$slack(ahead) > LinkartConfiguration.pathfindingDistance) {
                linkart$unlink(cast, ahead);
                return;
            }

            if (forward == null) forward = CartUtils.horizontal(toAhead);
            if (forward == null) return;

            double speed = Math.abs(motion.trainSpeed);
            setDeltaMovement(forward.x * speed, vertical, forward.z * speed);
            motion.drive(motion.trainSpeed);
            return;
        }

        // Spacing is measured between start-of-tick positions
        Vec3 toAhead = position().vectorTo(aheadMoved ? other.start : ahead.position());
        double gap = toAhead.length();
        double error = gap - LinkartConfiguration.distance;

        if (error > LinkartConfiguration.pathfindingDistance) {
            linkart$unlink(cast, ahead);
            return;
        }

        Vec3 towards = CartUtils.horizontal(toAhead);
        if (forward == null) forward = towards;

        if (forward == null) {
            // No direction to go by
            setDeltaMovement(0, vertical, 0);
            motion.drive(0);
            return;
        }

        if (towards == null) {
            error = -LinkartConfiguration.distance;
        } else if (motion.facing != null && forward.dot(towards) < 0.1) {
            // The cart ahead is not ahead along the track
            Vec3 aheadFacing = aheadMoved ? other.startFacing : other.facing;

            if (aheadFacing != null && aheadFacing.dot(motion.facing) > 0.5) {
                // Same heading: we overshot it
                error = -gap - LinkartConfiguration.distance;
            } else {
                // Hairpin: straight-line distance means nothing
                error = 0;
            }
        }

        // 3D error, horizontal travel
        double correction = gap > 1.0E-4 ? error * toAhead.horizontalDistance() / gap : error;
        double travel = aheadMoved ? motion.direction * other.travelled : 0;
        // A cart leaving the rails overshoots its speed that tick
        if (aheadMoved && !CartUtils.isOnRails(ahead)) travel = Math.min(travel, Math.abs(motion.trainSpeed));

        double target;
        boolean placing = false;
        if (travel > CartUtils.REST) {
            // Under way: wait rather than back up, which oscillates on powered rails
            target = Math.max(travel + correction, 0);
        } else {
            placing = Math.abs(correction) > PARKED_SLACK;
            target = travel + (placing ? correction : 0);
            if (!placing && Math.abs(travel) < 1.0E-4) target += motion.direction * linkart$hold();
        }

        double speed = Math.abs(target);

        if (speed < 1.0E-4) {
            setDeltaMovement(0, vertical, 0);
            motion.drive(0);
            return;
        }

        Vec3 steer = target > 0 ? forward : forward.reverse();
        // Tie-break if the rail was turned under a parked cart
        if (towards != null) steer = steer.add(towards.scale(target > 0 ? 0.25 : -0.25)).normalize();

        setDeltaMovement(steer.x * speed, vertical, steer.z * speed);
        motion.drive(motion.direction * target);
        // Parked: repositioning is not drive
        if (motion.trainSpeed == 0 && (placing || Math.abs(travel) >= 1.0E-4)) motion.mode = CartMotion.Mode.SLAVED;
        // Lift the speed limit only to catch up. Doubled, as some carts get a share of their limit
        if (error > CATCH_UP_SLACK) motion.speedCap = speed * 2 + 0.5;
    }

    // Off the rails: head straight for the cart ahead and stay a space behind it.
    @Unique
    private void linkart$chase(AbstractMinecart cast, AbstractMinecart ahead) {
        CartMotion motion = this.linkart$motion;
        Vec3 toAhead = position().vectorTo(ahead.position());
        double distance = Math.max(toAhead.length() - LinkartConfiguration.distance, 0);

        if (distance - linkart$slack(ahead) > LinkartConfiguration.pathfindingDistance) {
            linkart$unlink(cast, ahead);
            return;
        }

        motion.mode = CartMotion.Mode.SLAVED;

        if (distance <= 0 && (ahead.onGround() || CartUtils.isOnRails(ahead))) {
            // Next to a landed cart: fall freely and move apart sideways rather than hover above it
            double horizontalGap = toAhead.horizontalDistance();
            double room = Math.min(LinkartConfiguration.distance - horizontalGap, SPREAD_SPEED);
            Vec3 away = horizontalGap > 0.05 ? CartUtils.horizontal(toAhead.reverse()) : linkart$behind();

            if (room < 0.02 || away == null) {
                room = 0;
                away = Vec3.ZERO;
            }

            setDeltaMovement(away.x * room, getDeltaMovement().y, away.z * room);
            motion.speedCap = room * 2 + 0.5;
            return;
        }

        // Go slower (1.0->0.8) the closer (1->0) we are
        double speed = distance <= 1 ? distance * (0.8 + 0.2 * distance) : distance;
        setDeltaMovement(toAhead.normalize().scale(speed));
        motion.speedCap = speed * 2 + 0.5;
    }

    // Away from the cart ahead along the train, when positions can't tell
    @Unique
    private Vec3 linkart$behind() {
        Vec3 facing = this.linkart$motion.facing;
        if (facing == null) return new Vec3(1, 0, 0);
        return this.linkart$motion.direction > 0 ? facing.reverse() : facing;
    }

    // Extra gap allowed off the rails: a cart covers up to twice its speed the tick it leaves them.
    @Unique
    private double linkart$slack(AbstractMinecart ahead) {
        return 2 * Math.max(ahead.getDeltaMovement().length(), Math.abs(this.linkart$motion.trainSpeed));
    }

    // The follower of the two holds the link item
    @Unique
    private void linkart$unlink(AbstractMinecart cast, AbstractMinecart ahead) {
        CartUtils.unlinkFromParent(ahead == linkart$getFollowing() ? cast : ahead);
    }

    @Inject(at = @At("RETURN"), method = "tick")
    private void linkart$finishTick(CallbackInfo ci) {
        if (level().isClientSide()) return;
        AbstractMinecart cast = (AbstractMinecart) (Object) this;
        CartMotion motion = this.linkart$motion;
        boolean moved = motion.plannedThisTick() && motion.tickedThisTick();

        motion.finish(position(), CartUtils.towardsFirst(cast));
        if (!moved) return;

        Vec3 velocity = getDeltaMovement();
        motion.settle(motion.facing == null ? 0 : velocity.x * motion.facing.x + velocity.z * motion.facing.z);

        if (motion.ahead == null) {
            motion.trainTravel = Math.min(Math.abs(motion.travelled), CartUtils.speedLimit(cast));
        }

        // Sync every tick: vanilla's per-cart 3-tick sync lets linked carts drift apart on screen
        if (position().distanceToSqr(motion.start) > 1.0E-8) {
            this.needsSync = true;
        }

        linkart$strain(cast);
    }

    // Strain from bends and landings. Crashes show a tick later and are handled in CartUtils.plan.
    @Unique
    private void linkart$strain(AbstractMinecart cast) {
        CartMotion motion = this.linkart$motion;
        AbstractMinecart ahead = motion.ahead;

        if (motion.startedAirborne && (onGround() || CartUtils.isOnRails(cast))) {
            double speed = motion.start.distanceTo(position());

            if (ahead != null) {
                CartUtils.strain(ahead, cast, speed);
            } else {
                // The front cart strains the cart behind it
                AbstractMinecart behind = CartUtils.behind(cast, motion.direction);
                if (behind != null) CartUtils.strain(cast, behind, speed);
            }
        } else if (motion.startedOnRails && ahead != null && motion.startFacing != null && motion.facing != null) {
            // A quarter turn or more is a full bend
            double turn = Math.acos(Mth.clamp(motion.startFacing.dot(motion.facing), -1, 1)) / (Math.PI / 2);
            // At the front cart's speed, not this cart's
            CartUtils.strain(ahead, cast, motion.trainTravel * Math.min(turn, 1));
        }
    }

    // Raises the speed limit to speedCap when set
    @Inject(at = @At("RETURN"), method = "getMaxSpeed", cancellable = true)
    private void linkart$liftSpeedCap(ServerLevel level, CallbackInfoReturnable<Double> cir) {
        if (linkart$motion.speedCap > cir.getReturnValue()) cir.setReturnValue(linkart$motion.speedCap);
    }

    @Inject(at = @At("HEAD"), method = "push(Lnet/minecraft/world/entity/Entity;)V", cancellable = true)
    void onPushAway(Entity entity, CallbackInfo ci) {
        if (!CollisionUtils.shouldCollide(this, entity)) {
            ci.cancel();
            return;
        }

        linkart$beforePush = getDeltaMovement();
    }

    // A shove only ever counts towards the train's speed, as a linked cart's own velocity is set anew every tick.
    // Left in the velocity it would still reach the client, which plays the rolling sound for a cart standing still.
    @Inject(at = @At("RETURN"), method = "push(Lnet/minecraft/world/entity/Entity;)V")
    private void linkart$keepPush(Entity entity, CallbackInfo ci) {
        Vec3 before = linkart$beforePush;
        linkart$beforePush = null;
        if (before == null || level().isClientSide() || linkart$motion.mode == CartMotion.Mode.FREE) return;

        linkart$motion.pushed = linkart$motion.pushed.add(getDeltaMovement().subtract(before));
        setDeltaMovement(before);
    }

    @Inject(at = @At("RETURN"), method = "addAdditionalSaveData")
    private void linkart$addAdditionalSaveData(ValueOutput compoundTag, CallbackInfo ci) {
        compoundTag.storeNullable("LK-Following", UUIDUtil.CODEC, linkart$followingUUID);
        compoundTag.storeNullable("LK-Follower", UUIDUtil.CODEC, linkart$followerUUID);
        compoundTag.storeNullable("LK-Facing", Vec3.CODEC, linkart$motion.facing);
        if(!this.linkart$getLinkItem().isEmpty()) {
            compoundTag.store("LK-ItemStack", ItemStack.CODEC, this.linkart$getLinkItem());
        }
    }

    @Inject(at = @At("RETURN"), method = "readAdditionalSaveData")
    private void linkart$readAdditionalSaveData(ValueInput compoundTag, CallbackInfo ci) {
        linkart$followingUUID = compoundTag.read("LK-Following", UUIDUtil.CODEC).orElse(null);
        linkart$followerUUID = compoundTag.read("LK-Follower", UUIDUtil.CODEC).orElse(null);
        linkart$motion.facing = compoundTag.read("LK-Facing", Vec3.CODEC).orElse(null);
        this.linkart$setLinkItem(compoundTag.read("LK-ItemStack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    public AbstractMinecart linkart$getFollowing() {
        // An unloaded cart comes back as a new entity
        if (linkart$following != null && linkart$following.isRemoved()) linkart$following = null;
        if (linkart$following == null && linkart$followingUUID != null) {
            linkart$following = (AbstractMinecart) ((ServerLevel) this.level()).getEntity(linkart$followingUUID);
        }
        return linkart$following;
    }

    @Override
    public void linkart$setFollowing(AbstractMinecart following) {
        this.linkart$following = following;
        this.linkart$followingUUID = following != null ? following.getUUID() : null;
        // Facing is relative to the chain's first cart
        if (following != null) this.linkart$motion.facing = null;
    }

    @Override
    public AbstractMinecart linkart$getFollower() {
        if (linkart$follower != null && linkart$follower.isRemoved()) linkart$follower = null;
        if (linkart$follower == null && linkart$followerUUID != null) {
            linkart$follower = (AbstractMinecart) ((ServerLevel) this.level()).getEntity(linkart$followerUUID);
        }
        return linkart$follower;
    }

    @Override
    public void linkart$setFollower(AbstractMinecart follower) {
        this.linkart$follower = follower;
        this.linkart$followerUUID = follower != null ? follower.getUUID() : null;
    }

    @Override
    public ItemStack linkart$getLinkItem() {
        return linkart$itemStack;
    }

    @Override
    public void linkart$setLinkItem(ItemStack linkItem) {
        this.linkart$itemStack = linkItem == null ? ItemStack.EMPTY : linkItem;
    }

    @Override
    public CartMotion linkart$getMotion() {
        return linkart$motion;
    }
}
