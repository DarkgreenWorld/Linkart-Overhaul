package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.api.LinkableMinecart;
import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartUtils;
import com.darkgreen_world.linkart.utility.CollisionUtils;
import com.darkgreen_world.linkart.utility.LoadingCarts;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
//? if =1.21.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
*///?}
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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

    // A parked train leaves spacing errors smaller than this alone
    @Unique private static final double PARKED_SLACK = 0.08;
    // How fast carts that came down in a heap move apart
    @Unique private static final double SPREAD_SPEED = 0.3;
    // A slope adds well under this to a cart's speed in a tick. A powered rail adds more, and is not to be held against
    @Unique private static final double HOLD_LIMIT = 0.05;

    public AbstractMinecartEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    // Ensure the train doesn't break apart (especially if other minecart mods increase speed)
    // Old physics only. The experimental rail physics calls move() once per rail segment, not once per tick, so these
    // per-call lengths aren't speeds, and limiting on them capped trains at one block per tick.
    //? if =1.21.1 {
    /*// Used to smooth out acceleration
    @Unique private static final double SAFE_SPEEDUP_THRESHOLD = 0.4;
    @Unique private static final double SMOOTH_SPEEDUP_AMOUNT = 0.2;
    @Unique private static final double SAFE_SPEEDUP_DIFFERENCE = 0.02;
    @Unique private double lastMovementLength = 0.0D;  // Movement length on previous tick

    @Unique private double limitMovementLength(double targetMovementLength) {
        double cartLastMovementLength = this.lastMovementLength;

        boolean isLeading = (this.linkart$getFollowing() == null && this.linkart$getFollower() != null);
        // Don't limit if we are not the leading minecart
        if (!isLeading) return targetMovementLength;
        // Don't limit if we are below the safe speedup threshold
        if (targetMovementLength <= SAFE_SPEEDUP_THRESHOLD) return targetMovementLength;

        AbstractMinecart follower = this.linkart$getFollower();
        // Check if there are follower minecarts not at our speed
        while (follower != null) {
            double followerLastMovementLength = ((AbstractMinecartEntityMixin) (Object) follower).lastMovementLength;
            if (Math.abs(followerLastMovementLength - cartLastMovementLength) > SAFE_SPEEDUP_DIFFERENCE)
                // If so, maintain same speed
                return cartLastMovementLength;
            follower = follower.linkart$getFollower();
        }

        // Otherwise increase our speed slowly
        return Math.min(Math.max(
                        cartLastMovementLength + SMOOTH_SPEEDUP_AMOUNT,
                        SAFE_SPEEDUP_THRESHOLD),  // min
                targetMovementLength);  // max
    }

    @ModifyArg(method = "moveAlongTrack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private Vec3 modifiedMovement(Vec3 movement) {
        if (this.lastMovementLength < movement.length()) {
            final double targetMovementLength = movement.length();

            // Limit the movement length
            movement = movement.scale(limitMovementLength(targetMovementLength) / targetMovementLength);
        }

        this.lastMovementLength = movement.length();
        return movement;
    }
    *///?}

    // CHECK THIS AGAIN
    //? if =1.21.1 {
    /*@WrapOperation(method = "moveAlongTrack", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
    private double linkart$skipVelocityClamping(double value, double min, double max, Operation<Double> original) {
        if (this.linkart$getFollowing() != null) {
            AbstractMinecart following = this.linkart$getFollowing();
            while (following.linkart$getFollowing() != null) {
                following = following.linkart$getFollowing();
            }
            double parent = ((MinecartAccessor) following).linkart$getMaxSpeed();
            return Mth.clamp(value, -parent, parent);
        }
        return original.call(value, min, max);
    }
    *///?}

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
        } else if (motion.startedOnRails) {
            linkart$follow(cast, motion.ahead);
        } else {
            linkart$chase(cast, motion.ahead);
        }

        if (LinkartConfiguration.chunkloading && linkart$getFollowing() != null) {
            if (linkart$getFollower() != null && !CartUtils.approximatelyZero(this.getDeltaMovement().length())) {
                //? if <1.21.5 {
                //((ServerLevel) this.level()).getChunkSource().addRegionTicket(TicketType.PORTAL, this.chunkPosition(), LinkartConfiguration.chunkloadingRadius, this.blockPosition());
                //?} else
                ((ServerLevel) this.level()).getChunkSource().addTicketWithRadius(TicketType.PORTAL, this.chunkPosition(), LinkartConfiguration.chunkloadingRadius);
                LoadingCarts.getOrCreate((ServerLevel) this.level()).addCart(cast);
            } else {
                LoadingCarts.getOrCreate((ServerLevel) this.level()).removeCart(cast);
            }
        }
    }

    // The cart at the front goes at the train's speed and is otherwise an ordinary minecart: it is the one that runs
    // into things, gets clamped to the speed limit and decides where the rest of the train can go.
    @Unique
    private void linkart$lead(AbstractMinecart cast) {
        CartMotion motion = this.linkart$motion;
        // Off the rails it is flying, falling or sliding, and its own velocity is the only thing that knows how
        if (!motion.startedOnRails) return;

        Vec3 facing = motion.facing != null ? motion.facing : CartUtils.towardsFirst(cast);
        if (facing == null) return;

        double speed = motion.trainSpeed != 0 ? motion.trainSpeed : linkart$hold();
        setDeltaMovement(facing.x * speed, getDeltaMovement().y, facing.z * speed);
        motion.drive(speed);
    }

    // A cart that is to stay where it is can't just be given no speed: on a slope its own tick adds some downhill
    // before it moves, every tick, and a train that is held by its other carts would have this one creep off and be
    // fetched back for ever. So it is given what its tick took last time, the other way. What the slope does still
    // shows in the cart's speed afterwards, and still pulls at the train.
    @Unique
    private double linkart$hold() {
        double drift = this.linkart$motion.drift;
        return Math.abs(drift) <= HOLD_LIMIT ? -drift : 0;
    }

    // Every other cart repeats what the cart ahead of it did this tick (see ServerLevelMixin for the order): the same
    // distance along the track, plus whatever closes the error in their spacing. Chasing that cart's new position in
    // a straight line instead only works while it is within a cart's length; at speed it is several rails away,
    // round corners that the straight line cuts.
    @Unique
    private void linkart$follow(AbstractMinecart cast, AbstractMinecart ahead) {
        CartMotion motion = this.linkart$motion;
        CartMotion other = ahead.linkart$getMotion();
        boolean aheadMoved = other.tickedThisTick();
        double vertical = getDeltaMovement().y;
        // Along the track, the way the train is going
        Vec3 forward = motion.facing == null ? null : motion.direction > 0 ? motion.facing : motion.facing.reverse();

        if (!(aheadMoved ? other.startedOnRails : CartUtils.isOnRails(ahead))) {
            // The cart ahead has left the rails. Carry on at the train's speed, so as to leave them the way it did
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
            motion.speedCap = speed * 2 + 0.5;
            return;
        }

        // Spacing is judged from where both carts stood before either moved, when they were still next to each other
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
            // Never moved and sitting right on top of the cart ahead, so there is no telling which way the track runs
            setDeltaMovement(0, vertical, 0);
            motion.drive(0);
            return;
        }

        if (towards == null) {
            error = -LinkartConfiguration.distance;
        } else if (motion.facing != null && forward.dot(towards) < 0.1) {
            // The cart ahead isn't ahead of us along the track
            Vec3 aheadFacing = aheadMoved ? other.startFacing : other.facing;

            if (aheadFacing != null && aheadFacing.dot(motion.facing) > 0.5) {
                // It faces the way we do, so we have overshot it and belong a full space behind it
                error = -gap - LinkartConfiguration.distance;
            } else {
                // It is round a hairpin, where the straight line between us says nothing about the track between us
                error = 0;
            }
        }

        // The error is in three dimensions, the travel is horizontal
        double correction = gap > 1.0E-4 ? error * toAhead.horizontalDistance() / gap : error;
        double travel = aheadMoved ? motion.direction * other.travelled : 0;
        // The tick a cart leaves the rails it goes further than its speed, which is no speed to leave them at
        if (aheadMoved && !CartUtils.isOnRails(ahead)) travel = Math.min(travel, Math.abs(motion.trainSpeed));

        double target;
        boolean placing = false;
        if (travel > CartUtils.REST) {
            // Wait rather than back up while under way: a powered rail speeds up whichever way a cart is going, and
            // a cart sent back and forth across one never settles
            target = Math.max(travel + correction, 0);
        } else {
            placing = Math.abs(correction) > PARKED_SLACK;
            target = travel + (placing ? correction : 0) + motion.direction * linkart$hold();
        }

        double speed = Math.abs(target);

        if (speed < 1.0E-4) {
            setDeltaMovement(0, vertical, 0);
            motion.drive(0);
            return;
        }

        Vec3 steer = target > 0 ? forward : forward.reverse();
        // Settles which way to go if the rail was turned under a parked cart and its facing now lies across it
        if (towards != null) steer = steer.add(towards.scale(target > 0 ? 0.25 : -0.25)).normalize();

        setDeltaMovement(steer.x * speed, vertical, steer.z * speed);
        motion.drive(motion.direction * target);
        // In a train at rest a cart only moves to get back in its place, or to keep up with one that is. Nothing is
        // driving it then, whatever its rail adds
        if (motion.trainSpeed == 0 && (placing || Math.abs(travel) >= 1.0E-4)) motion.mode = CartMotion.Mode.SLAVED;
        // Generous, as some carts take a share of their speed limit rather than all of it
        motion.speedCap = speed * 2 + 0.5;
    }

    // Off the rails there is no track to measure along, and the cart ahead is being thrown about by gravity and
    // whatever it lands on. All that can be done is to head straight for it and stay a space behind.
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
            // Close enough to a cart that has come down. Standing still here, as is right behind one in flight,
            // would leave this cart hanging wherever it came down from, which after a long fall is straight above,
            // to sink onto the other's roof. Fall freely instead, and make room sideways
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

    // Away from the cart ahead along the line of the train, for when the two are too nearly on top of each other to
    // tell from where they are
    @Unique
    private Vec3 linkart$behind() {
        Vec3 facing = this.linkart$motion.facing;
        if (facing == null) return new Vec3(1, 0, 0);
        return this.linkart$motion.direction > 0 ? facing.reverse() : facing;
    }

    // How far a cart may fall behind one that is off the rails, on top of the usual distance. The tick a cart leaves
    // the rails it covers up to twice its speed, and the next one in line can't know until it gets there itself.
    @Unique
    private double linkart$slack(AbstractMinecart ahead) {
        return 2 * Math.max(ahead.getDeltaMovement().length(), Math.abs(this.linkart$motion.trainSpeed));
    }

    // The link item is kept by whichever of the two carts follows the other in the chain
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
        if (moved) linkart$strain(cast);
    }

    // Following never pulls a train apart any more, however fast it goes. What does is what would in earnest: being
    // swung round a bend or brought down to the ground with more behind a link than it can hold at that speed. The
    // third case, the front cart being stopped short, shows a tick later and is looked for in CartUtils.plan.
    @Unique
    private void linkart$strain(AbstractMinecart cast) {
        CartMotion motion = this.linkart$motion;
        AbstractMinecart ahead = motion.ahead;

        if (motion.startedAirborne && (onGround() || CartUtils.isOnRails(cast))) {
            double speed = motion.start.distanceTo(position());

            if (ahead != null) {
                CartUtils.strain(ahead, cast, speed);
            } else {
                // The front cart has nothing pulling it. Coming down, it jerks at the cart behind it instead
                AbstractMinecart behind = CartUtils.behind(cast, motion.direction);
                if (behind != null) CartUtils.strain(cast, behind, speed);
            }
        } else if (motion.startedOnRails && ahead != null && motion.startFacing != null && motion.facing != null) {
            // A quarter turn within the tick is a full bend, whether it is one corner or a hairpin: they are as tight
            double turn = Math.acos(Mth.clamp(motion.startFacing.dot(motion.facing), -1, 1)) / (Math.PI / 2);
            // At the train's speed, not this cart's: one that is catching up goes faster for a tick or two
            double speed = Math.min(Math.abs(motion.travelled), Math.abs(motion.trainSpeed));
            CartUtils.strain(ahead, cast, speed * Math.min(turn, 1));
        }
    }

    // A cart that has fallen behind has to be able to go faster than the front of the train to catch up
    @Inject(at = @At("RETURN"), method = "getMaxSpeed", cancellable = true)
    //? if >=1.21.2 {
    private void linkart$liftSpeedCap(ServerLevel level, CallbackInfoReturnable<Double> cir) {
    //? } else {
    //private void linkart$liftSpeedCap(CallbackInfoReturnable<Double> cir) {
    //? }
        if (linkart$motion.speedCap > cir.getReturnValue()) cir.setReturnValue(linkart$motion.speedCap);
    }

    @Inject(at = @At("HEAD"), method = "push(Lnet/minecraft/world/entity/Entity;)V", cancellable = true)
    void onPushAway(Entity entity, CallbackInfo ci) {
        if (!CollisionUtils.shouldCollide(this, entity)) ci.cancel();
    }

    @Inject(at = @At("RETURN"), method = "addAdditionalSaveData")
    //? if >=1.21.6 {
    private void linkart$addAdditionalSaveData(ValueOutput compoundTag, CallbackInfo ci) {
    //? } else {
    //private void linkart$addAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
    //? }
        //? if >=1.21.5 {
        compoundTag.storeNullable("LK-Following", UUIDUtil.CODEC, linkart$followingUUID);
        compoundTag.storeNullable("LK-Follower", UUIDUtil.CODEC, linkart$followerUUID);
        compoundTag.storeNullable("LK-Facing", Vec3.CODEC, linkart$motion.facing);
        if(!this.linkart$getLinkItem().isEmpty()) {
            //? if =1.21.5
            //RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            compoundTag.store("LK-ItemStack", ItemStack.CODEC, /*? if =1.21.5 >>*//*registryOps,*/ this.linkart$getLinkItem());
        }
        //?} else {
        /*if (linkart$followingUUID != null) compoundTag.putUUID("LK-Following", linkart$followingUUID);
        if (linkart$followerUUID != null) compoundTag.putUUID("LK-Follower", linkart$followerUUID);
        compoundTag.put("LK-ItemStack", this.linkart$getLinkItem().saveOptional(this.registryAccess()));
        *///?}
    }

    @Inject(at = @At("RETURN"), method = "readAdditionalSaveData")
    //? if >=1.21.6 {
    private void linkart$readAdditionalSaveData(ValueInput compoundTag, CallbackInfo ci) {
    //? } else {
    //private void linkart$readAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
    //? }
        /*? if >=1.21.5 {*/
        linkart$followingUUID = compoundTag.read("LK-Following", UUIDUtil.CODEC).orElse(null);
        linkart$followerUUID = compoundTag.read("LK-Follower", UUIDUtil.CODEC).orElse(null);
        linkart$motion.facing = compoundTag.read("LK-Facing", Vec3.CODEC).orElse(null);
        //? if =1.21.5
        //RegistryOps<Tag> registryOps = this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        this.linkart$setLinkItem(compoundTag.read("LK-ItemStack", ItemStack.OPTIONAL_CODEC/*? if =1.21.5 >> ').'*//*,registryOps*/).orElse(ItemStack.EMPTY));
        /*?} else {*/
        /*if (compoundTag.contains("LK-Following")) linkart$followingUUID = compoundTag.getUUID("LK-Following");
        if (compoundTag.contains("LK-Follower")) linkart$followerUUID = compoundTag.getUUID("LK-Follower");
        if (compoundTag.contains("LK-ItemStack", Tag.TAG_COMPOUND)) {
            this.linkart$setLinkItem(ItemStack.parseOptional(this.registryAccess(), compoundTag.getCompound("LK-ItemStack")));
        }
        *//*?}*/
    }

    @Override
    public AbstractMinecart linkart$getFollowing() {
        // An unloaded cart comes back as a new entity, so the old one must not be kept
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
        // The first cart of the chain now lies wherever the new cart ahead is
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
