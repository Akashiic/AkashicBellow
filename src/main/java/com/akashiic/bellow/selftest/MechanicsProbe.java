package com.akashiic.bellow.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Functional checks of mod machines whose logic has its own notion of "bottom of the world" (IC2 Miner and
 * Magnetizer). Everything is reflective: the probe compiles without the mods and reports them as absent.
 */
final class MechanicsProbe {

    private final WorldServer world;
    private final int x0, z0;
    private final int floor;
    private Miner surfaceMiner, deepMiner;
    private TileEntity magnetizer;
    private int magX, magZ, magTop;

    private static final class Miner {

        final TileEntity te;
        final int startY;

        Miner(TileEntity te, int startY) {
            this.te = te;
            this.startY = startY;
        }
    }

    MechanicsProbe(WorldServer world, int x0, int z0) {
        this.world = world;
        this.x0 = x0;
        this.z0 = z0;
        this.floor = BellowAPI.getMinY(world);
    }

    void setup(List<String> out) {
        try {
            Class.forName("ic2.core.Ic2Items");
        } catch (ClassNotFoundException e) {
            out.add("INFO IC2 not present: machine checks skipped");
            return;
        }
        try {
            this.surfaceMiner = this.miner(this.x0, 2, this.z0); // ~5 blocks per 1200 ticks in deepslate
            this.deepMiner = this.miner(this.x0 + 6, this.floor + 20, this.z0);
            this.magnetizerSetup();
            out.add(
                "INFO IC2 machines placed: miner(surface)=" + (this.surfaceMiner != null)
                    + " miner(deep)="
                    + (this.deepMiner != null)
                    + " magnetizer="
                    + (this.magnetizer != null));
        } catch (Throwable t) {
            out.add("FINDING IC2 machine setup failed: " + t);
        }
    }

    /** Called every tick: machines only run while powered and stocked. */
    void tick() {
        try {
            for (Miner m : new Miner[] { this.surfaceMiner, this.deepMiner }) {
                if (m == null) continue;
                setField(m.te, "energy", 10000.0D);
                ItemStack pipes = ic2Item("miningPipe").copy();
                pipes.stackSize = 64;
                slotPut(m.te, "pipeSlot", pipes);
            }
            if (this.magnetizer != null) setField(this.magnetizer, "energy", 10000.0D);
        } catch (ReflectiveOperationException ignored) {
            // reported by report()
        }
    }

    /** Returns the number of failed checks. */
    int report(List<String> out) {
        int failed = 0;
        if (this.surfaceMiner != null) {
            int tip = this.pipeTip(this.surfaceMiner);
            boolean ok = tip < 0;
            if (!ok) failed++;
            out.add(
                (ok ? "PASS" : "FINDING") + " IC2 miner from Y "
                    + this.surfaceMiner.startY
                    + " digs into the deep layer - pipe tip at Y "
                    + tip);
        }
        if (this.deepMiner != null) {
            int tip = this.pipeTip(this.deepMiner);
            boolean ok = tip < this.deepMiner.startY - 1;
            if (!ok) failed++;
            out.add(
                (ok ? "PASS" : "FINDING") + " IC2 miner inside the deep layer (Y "
                    + this.deepMiner.startY
                    + ") digs - pipe tip at Y "
                    + tip);
        }
        if (this.magnetizer != null) {
            int magnetized = 0, below = 0;
            for (int y = this.magTop; y >= this.magTop - 12; y--) {
                if (y >= 0) continue;
                below++;
                if (this.world.getBlockMetadata(this.magX, y, this.magZ) > 0) magnetized++;
            }
            boolean ok = magnetized == below;
            if (!ok) failed++;
            out.add(
                (ok ? "PASS" : "FINDING") + " IC2 magnetizer charges iron fences below Y 0 - "
                    + magnetized
                    + "/"
                    + below);
        }
        return failed;
    }

    private Miner miner(int x, int y, int z) throws ReflectiveOperationException {
        TileEntity te = this.placeMachine("TileEntityMiner", x, y, z);
        if (te == null) return null;
        slotPut(te, "drillSlot", ic2Item("miningDrill").copy());
        return new Miner(te, y);
    }

    private void magnetizerSetup() throws ReflectiveOperationException {
        int x = this.x0 + 12, z = this.z0, y = 2;
        TileEntity te = this.placeMachine("TileEntityMagnetizer", x, y, z);
        if (te == null) return;
        int facing = ((Number) te.getClass()
            .getMethod("getFacing")
            .invoke(te)).intValue();
        if (facing < 2) {
            te.getClass()
                .getMethod("setFacing", short.class)
                .invoke(te, (short) 2);
            facing = 2;
        }
        int[][] off = { { 0, 0 }, { 0, 0 }, { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        this.magX = x + off[facing][0];
        this.magZ = z + off[facing][1];
        this.magTop = y;
        Block fence = Block.getBlockFromItem(ic2Item("ironFence").getItem());
        for (int fy = y; fy >= y - 12; fy--) this.world.setBlock(this.magX, fy, this.magZ, fence, 0, 2);
        this.magnetizer = te;
    }

    /** Finds the IC2 machine block/meta whose tile entity class has the given simple name and places it. */
    private TileEntity placeMachine(String simpleName, int x, int y, int z) {
        for (Object o : Block.blockRegistry) {
            Block block = (Block) o;
            String id = Block.blockRegistry.getNameForObject(block);
            if (id == null || !id.startsWith("IC2:")) continue;
            for (int meta = 0; meta < 16; meta++) {
                if (!block.hasTileEntity(meta)) continue;
                this.world.setBlock(x, y - 1, z, Blocks.stone, 0, 2);
                this.world.setBlock(x, y, z, block, meta, 3);
                TileEntity te = this.world.getTileEntity(x, y, z);
                if (te != null && te.getClass()
                    .getSimpleName()
                    .equals(simpleName)) return te;
                this.world.setBlockToAir(x, y, z);
            }
        }
        return null;
    }

    private int pipeTip(Miner m) {
        int x = m.te.xCoord, z = m.te.zCoord;
        int lowest = m.startY;
        Block pipe = Block.getBlockFromItem(safeItem("miningPipe").getItem());
        Block tip = Block.getBlockFromItem(safeItem("miningPipeTip").getItem());
        for (int y = m.startY - 1; y >= this.floor; y--) {
            Block b = this.world.getBlock(x, y, z);
            if (b == pipe || b == tip) lowest = y;
            else break;
        }
        return lowest;
    }

    private static ItemStack safeItem(String name) {
        try {
            return ic2Item(name);
        } catch (ReflectiveOperationException e) {
            return new ItemStack(Blocks.air);
        }
    }

    private static ItemStack ic2Item(String name) throws ReflectiveOperationException {
        return (ItemStack) Class.forName("ic2.core.Ic2Items")
            .getField(name)
            .get(null);
    }

    private static void slotPut(Object te, String slot, ItemStack stack) throws ReflectiveOperationException {
        Object invSlot = findField(te.getClass(), slot).get(te);
        Method put = invSlot.getClass()
            .getMethod("put", ItemStack.class);
        put.invoke(invSlot, stack);
    }

    private static void setField(Object o, String name, Object value) throws ReflectiveOperationException {
        findField(o.getClass(), name).set(o, value);
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }
}
