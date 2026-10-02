package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.Linkart;
import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class LoadingCarts extends SavedData {

    private static final Codec<LoadingCarts> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BlockPos.CODEC.listOf().fieldOf("chunksToSave").forGetter(LoadingCarts::positionsToSave)
        ).apply(instance, LoadingCarts::new)
    );

    private static final SavedDataType<LoadingCarts> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Linkart.ID, "loading_carts"),
            LoadingCarts::new, CODEC, null
    );

    public static LoadingCarts getOrCreate(ServerLevel serverLevel) {
        return serverLevel.getDataStorage().computeIfAbsent(TYPE);
    }

    private final Set<BlockPos> chunksToReload = new HashSet<>();
    private final Set<AbstractMinecart> cartsToBlockPos = new HashSet<>();

    public LoadingCarts() { this(List.of()); }

    public LoadingCarts(Collection<BlockPos> chunksToReload) {
        this.chunksToReload.addAll(chunksToReload);
    }

    // Positions whose chunks get loaded again after a restart
    private List<BlockPos> positionsToSave() {
        Set<BlockPos> positions = new HashSet<>(chunksToReload);
        for (AbstractMinecart minecart : cartsToBlockPos) {
            if (!minecart.isRemoved()) positions.add(minecart.blockPosition());
        }
        return List.copyOf(positions);
    }

    public void tick(ServerLevel level) {
        if (!chunksToReload.isEmpty()) {
            for (BlockPos pos : chunksToReload) {
                ChunkPos chunkPos = ChunkPos.containing(pos);
                level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunkPos, LinkartConfiguration.chunkloadingRadius);
            }
            chunksToReload.clear();
            setDirty();
        }
    }

    public void addCart(AbstractMinecart cart) {
        cartsToBlockPos.add(cart);
        setDirty();
    }

    public void removeCart(AbstractMinecart cart) {
        cartsToBlockPos.remove(cart);
        setDirty();
    }
}
