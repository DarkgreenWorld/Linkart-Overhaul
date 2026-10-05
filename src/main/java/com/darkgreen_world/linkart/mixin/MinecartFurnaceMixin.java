package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CartUtils;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Above the default priority
@Mixin(value = MinecartFurnace.class, priority = 1100)
public abstract class MinecartFurnaceMixin {

    @Unique private @Nullable Vec3 linkart$push;

    // The push the engine adds
    @ModifyArg(method = "applyNaturalSlowdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 linkart$takePush(Vec3 push) {
        linkart$push = push;
        return push;
    }

    // Linkart turns the push into speed
    @WrapMethod(method = "applyNaturalSlowdown")
    private Vec3 linkart$engine(Vec3 movement, Operation<Vec3> original) {
        linkart$push = null;
        Vec3 result = original.call(movement);
        MinecartFurnace furnace = (MinecartFurnace) (Object) this;
        if (linkart$push == null || furnace.level().isClientSide()) return result;

        Vec3 push = linkart$push.horizontal();
        Vec3 saved = furnace.push;
        furnace.push = Vec3.ZERO;
        Vec3 coasting = original.call(movement);
        furnace.push = saved;

        CartMotion motion = furnace.linkart$getMotion();
        motion.thrust = push;
        double length = push.length();
        Vec3 velocity = coasting;
        if (!motion.tickedThisTick() && length > 1.0E-6) {
            Vec3 way = push.scale(1 / length);
            double along = coasting.dot(way);
            velocity = coasting.add(way.scale(CartUtils.driven(along, movement.dot(way), length, 1, furnace.isInWater() ? 0.1 : 1) - along));
        }

        // better_minecart_with_furnace pushes the minecarts ahead by what the engine gave
        if (CartUtils.SET_ENGINE_VELOCITY != null) {
            try {
                CartUtils.SET_ENGINE_VELOCITY.invoke(furnace, velocity);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return velocity;
    }
}
