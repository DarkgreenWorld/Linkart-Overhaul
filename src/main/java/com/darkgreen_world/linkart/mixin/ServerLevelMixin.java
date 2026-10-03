package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    // Tick the cart ahead first, so a cart follows this tick's movement rather than last tick's.
    @Inject(at = @At("HEAD"), method = "tickNonPassenger", cancellable = true)
    private void linkart$tickAheadFirst(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof AbstractMinecart minecart)) return;
        if (minecart.linkart$getMotion().tickedThisTick()) {
            // Already ticked this tick, ahead of a cart following it
            ci.cancel();
            return;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        CartUtils.plan(minecart);
        CartMotion motion = minecart.linkart$getMotion();
        AbstractMinecart ahead = motion.plannedThisTick() ? motion.ahead : null;
        // Same conditions under which the level would tick it itself
        if (ahead != null
                && ahead.level() == level
                && !ahead.isRemoved()
                && !ahead.isPassenger()
                && !level.tickRateManager().isEntityFrozen(ahead)
                && level.isPositionEntityTicking(ahead.blockPosition())) {
            level.guardEntityTick(level::tickNonPassenger, ahead);
        }
    }
}
