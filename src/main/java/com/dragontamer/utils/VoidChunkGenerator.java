package com.dragontamer;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import java.util.Random;

public class VoidChunkGenerator extends ChunkGenerator {
    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        return createChunkData(world);
    }
    
    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}