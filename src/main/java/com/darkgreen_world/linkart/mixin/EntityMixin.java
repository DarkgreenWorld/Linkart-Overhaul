package com.darkgreen_world.linkart.mixin;

import com.darkgreen_world.linkart.utility.CartMotion;
import com.darkgreen_world.linkart.utility.CollisionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /*
    A linked cart's velocity is set anew every tick, so a shove only counts towards its train's speed. Left in the
    velocity it would reach the client, which plays the rolling sound for a cart that isn't moving.
    */
    @Inject(at = @At("HEAD"), method = "push(DDD)V", cancellable = true)
    void linkart$keepPush(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof AbstractMinecart minecart && !minecart.level().isClientSide()
                && minecart.linkart$getMotion().mode != CartMotion.Mode.FREE) {
            minecart.linkart$getMotion().pushed = minecart.linkart$getMotion().pushed.add(x, y, z);
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", cancellable = true)
    void linkart$onRecalculateVelocity(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof AbstractMinecart minecart) {
            // Path waypoint, one per move
            if (!minecart.level().isClientSide()) minecart.linkart$getMotion().mark(minecart.position());

            List<Entity> collisions = minecart.level().getEntities((Entity) (Object) this, minecart.getBoundingBox().expandTowards(movement));

            for (Entity entity : collisions) {
                if (!CollisionUtils.shouldCollide(minecart, entity) && minecart.level().getBlockState(minecart.blockPosition()).getBlock() instanceof BaseRailBlock) {
                    cir.setReturnValue(movement);
                    return;
                }
            }
        }
    }
}
