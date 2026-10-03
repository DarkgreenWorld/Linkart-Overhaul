/*package com.darkgreen_world.linkart.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Optional;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.filefix.fixes.DimensionStorageFileFix;

@Mixin(DimensionStorageFileFix.class)
public interface PersistentStateAccessor {
    @Accessor("cache")
    //Map<String, SavedData> linkart$loadedStates();
    Map<String, Optional<SavedData>> linkart$loadedStates();
}
*/
