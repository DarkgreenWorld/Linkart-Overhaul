package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Unique private final Set<AbstractMinecart> linkart$tickedCarts = new ReferenceOpenHashSet<>();

    @Inject(at = @At("HEAD"), method = "tick(Ljava/util/function/BooleanSupplier;)V")
    private void linkart$resetTickedCarts(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        linkart$tickedCarts.clear();
        CartMotion.advanceClock();
    }

    // Tick a linked cart only after the cart ahead of it in its train, so it can repeat what that cart did this tick
    // rather than what it did the tick before. Otherwise every cart that comes first in the entity list lags a tick
    // behind, and its gap stretches and snaps back with every change in speed.
    @Inject(at = @At("HEAD"), method = "tickNonPassenger", cancellable = true)
    private void linkart$tickAheadFirst(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof AbstractMinecart minecart)) return;
        if (!linkart$tickedCarts.add(minecart)) {
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
