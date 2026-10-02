package com.darkgreen_world.linkart.utility;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Per-tick state of a linked cart. Speeds and distances are horizontal and signed: positive is towards the first
 * cart of the chain.
 */
public class CartMotion {

    public enum Mode {
        /** Runs on its own velocity. */
        FREE,
        /** Given a speed. What vanilla makes of it counts towards the train's. */
        DRIVEN,
        /** Only kept in place. Doesn't count towards the train's speed. */
        SLAVED
    }

    // Shorter moves add up rather than set a direction
    private static final double MIN_STEP = 1.0E-4;
    // More than this in a tick is a teleport
    private static final double MAX_TRAVEL = 128;
    // More than a slope or a furnace cart adds in a tick. A powered rail's launch is not held against
    private static final double HOLD_LIMIT = 0.1;

    private static long clock;

    /** Horizontal unit vector along the track at the cart, pointing towards the first cart of the chain. */
    public @Nullable Vec3 facing;

    /** The cart to follow this tick, or null for the cart at the front. */
    public @Nullable AbstractMinecart ahead;
    /** Which way the train is going this tick: 1 towards the first cart of the chain, -1 towards the last. */
    public int direction = 1;
    /** The speed of the train this tick. */
    public double trainSpeed;
    /** How far the front cart went this tick, capped at its speed limit. */
    public double trainTravel;
    /** Whether this cart is at the front this tick. */
    public boolean leading;
    /** How many carts come after this one this tick. */
    public int trailing;

    public Mode mode = Mode.FREE;
    /** Speed the cart was given this tick. */
    public double commanded;
    /** Speed limit override for this tick. */
    public double speedCap;
    /** Speed that keeps the cart in place: the opposite of what its own tick adds, on a slope for one. */
    public double hold;
    /** Whether the cart was only being kept in place this tick. */
    public boolean staying;

    /** Position at the start of the tick. */
    public @Nullable Vec3 start;
    /** Facing at the start of the tick. */
    public @Nullable Vec3 startFacing;
    public boolean startedOnRails;
    /** Neither on rails nor on the ground at the start of the tick. */
    public boolean startedAirborne;
    /** Distance covered along the track this tick. */
    public double travelled;

    private long plannedAt = -1;
    private long tickedAt = -1;
    private @Nullable Vec3 waypoint;
    private double path;
    private @Nullable Vec3 firstDirection;
    private @Nullable Vec3 lastDirection;

    /** Called once per level tick, before any cart moves. */
    public static void advanceClock() {
        clock++;
    }

    public void assign(@Nullable AbstractMinecart ahead, int direction, double trainSpeed, int trailing) {
        this.plannedAt = clock;
        this.ahead = ahead;
        this.leading = ahead == null;
        this.trailing = trailing;
        this.direction = direction;
        this.trainSpeed = trainSpeed;
        this.mode = Mode.FREE;
        this.commanded = 0;
        this.speedCap = 0;
    }

    /** Whether the cart's train has been planned in the current level tick. */
    public boolean plannedThisTick() {
        return this.plannedAt == clock;
    }

    public void begin(Vec3 position, boolean onRails, boolean airborne) {
        this.tickedAt = clock;
        this.start = position;
        this.startFacing = this.facing;
        this.startedOnRails = onRails;
        this.startedAirborne = airborne;
        this.staying = false;
        this.waypoint = position;
        this.path = 0;
        this.firstDirection = null;
        this.lastDirection = null;
        this.travelled = 0;
    }

    /** Whether this cart has already started its tick in the current level tick. */
    public boolean tickedThisTick() {
        return this.tickedAt == clock && this.start != null;
    }

    public void drive(double speed) {
        this.commanded = speed;
        this.mode = Mode.DRIVEN;
    }

    /** After the cart's own tick: whatever speed a cart that was to stay still has left is cancelled next time. */
    public void settle(double speed) {
        this.hold = this.staying ? this.hold - speed : 0;
        if (Math.abs(this.hold) > HOLD_LIMIT) this.hold = 0;
    }

    /** Adds the move since the last waypoint to the path. */
    public void mark(Vec3 position) {
        if (this.waypoint == null) return;

        double x = position.x - this.waypoint.x;
        double z = position.z - this.waypoint.z;
        double length = Math.sqrt(x * x + z * z);
        if (length < MIN_STEP) return;

        this.path += length;
        this.lastDirection = new Vec3(x / length, 0, z / length);
        if (this.firstDirection == null) this.firstDirection = this.lastDirection;
        this.waypoint = position;
    }

    /**
     * @param towardsFirst fallback for an unusable facing
     */
    public void finish(Vec3 position, @Nullable Vec3 towardsFirst) {
        if (this.waypoint == null) return;
        mark(position);
        this.waypoint = null;

        if (this.firstDirection == null || this.lastDirection == null || this.path > MAX_TRAVEL) {
            this.travelled = 0;
            return;
        }

        // facing is the track direction where this tick began
        double alignment = this.facing == null ? 0 : this.firstDirection.dot(this.facing);
        if (Math.abs(alignment) < 0.2 && towardsFirst != null) alignment = this.firstDirection.dot(towardsFirst);

        boolean forward = alignment >= 0;
        this.travelled = forward ? this.path : -this.path;
        this.facing = forward ? this.lastDirection : this.lastDirection.reverse();
    }

    /**
     * The chain was reversed.
     *
     * @param facing the facing before
     */
    public void reverse(@Nullable Vec3 facing) {
        this.facing = facing == null ? null : facing.reverse();
        this.direction = -this.direction;
        this.trainSpeed = -this.trainSpeed;
        this.commanded = -this.commanded;
        this.hold = -this.hold;
    }

    /** For a cart that is not part of a train. */
    public void clear() {
        this.facing = null;
        this.ahead = null;
        this.leading = false;
        this.plannedAt = -1;
        this.mode = Mode.FREE;
        this.commanded = 0;
        this.speedCap = 0;
        this.hold = 0;
        this.staying = false;
        this.waypoint = null;
        this.travelled = 0;
    }
}
