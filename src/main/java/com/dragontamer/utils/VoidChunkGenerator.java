package com.dragontamer;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import java.util.Random;

public class VoidChunkGenerator extends ChunkGenerator {
    
    @Override
    public byte[] generate(World world, Random random, int chunkX, int chunkZ) {
        // 1.12.2 использует byte[] вместо ChunkData
        return new byte[65536];
    }
    
    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}
