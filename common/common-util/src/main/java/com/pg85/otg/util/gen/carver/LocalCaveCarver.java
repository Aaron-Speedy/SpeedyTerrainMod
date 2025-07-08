package com.pg85.otg.util.gen.carver;

import com.pg85.otg.util.materials.LocalMaterialData;

public abstract class LocalCaveCarver {
    protected final LocalMaterialData AIR;
    protected final LocalMaterialData CAVE_AIR;
    protected final LocalMaterialData WATER;
    protected final LocalMaterialData LAVA;

    public LocalCaveCarver(LocalMaterialData air, LocalMaterialData caveAir, LocalMaterialData water, LocalMaterialData lava) {
        this.AIR = air;
        this.CAVE_AIR = caveAir;
        this.WATER = water;
        this.LAVA = lava;
    }
}
