package com.akashiic.bellow.selftest;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Mod compatibility probe ({@code -Dakashicbellow.selftest.phase=compat}, then {@code compatverify} in a new JVM).
 * <p>
 * Every block of every mod that has a tile entity, every registered entity and every Immersive Engineering multiblock
 * is exercised twice: in the deep layer and at a surface control height, with identical surroundings. The world is
 * ticked, the deep tile entities are snapshotted, the server is stopped, and a fresh JVM compares what it reads back.
 * Only behaviour that differs between deep and control is reported as a finding: quirks a mod has everywhere are not
 * caused by the deep layer.
 */
public final class CompatProbe {

    private static final int TICKS = 1200;
    private static final int CONTROL_Y = 120;
    private static final String SNAPSHOT = "akashicbellow-compat.dat";

    private final WorldServer world;
    private final int deepY;
    private final int ox, oz;
    private final List<String> out = new ArrayList<>();
    private final List<Probe> tiles = new ArrayList<>();
    private final List<EntityProbe> entities = new ArrayList<>();
    private int findings;
    private int ticks;
    private MechanicsProbe mechanics;
    /** Next free row (blocks are laid out in bands along Z). */
    private int cursorZ;

    private static final class Probe {

        final String name;
        final int x, z;
        String deepError, controlError;
        String deepClass, controlClass;

        Probe(String name, int x, int z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }

    private static final class EntityProbe {

        final String name;
        final Entity deep, control;
        final double deepY0, controlY0;

        EntityProbe(String name, Entity deep, Entity control) {
            this.name = name;
            this.deep = deep;
            this.control = control;
            this.deepY0 = deep.posY;
            this.controlY0 = control.posY;
        }
    }

    private CompatProbe(WorldServer world) {
        this.world = world;
        this.deepY = BellowAPI.getMinY(world) + 20;
        ChunkCoordinates spawn = world.getSpawnPoint();
        // Inside the spawn area (+-128 blocks), which the server keeps loaded without players.
        this.ox = spawn.posX - 120;
        this.oz = spawn.posZ - 120;
    }

    public static void start(MinecraftServer server, String phase) {
        CompatProbe probe = new CompatProbe(server.worldServerForDimension(0));
        if (phase.equals("compatverify")) {
            probe.verify();
            probe.finish(server, "akashicbellow-compatverify.txt");
            return;
        }
        probe.place();
        probe.entities();
        probe.multiblocks();
        probe.mechanics = new MechanicsProbe(probe.world, probe.ox + 4, probe.cursorZ + 4);
        probe.mechanics.setup(probe.out);
        probe.cursorZ += 12;
        probe.out
            .add("INFO probe area: x " + probe.ox + ".." + (probe.ox + 192) + " z " + probe.oz + ".." + probe.cursorZ);
        FMLCommonHandler.instance()
            .bus()
            .register(probe);
    }

    private void finding(String what) {
        this.findings++;
        this.out.add("FINDING " + what);
    }

    // ---------------------------------------------------------------- tile entities

    private void place() {
        int i = 0;
        for (Object o : Block.blockRegistry) {
            Block block = (Block) o;
            String id = Block.blockRegistry.getNameForObject(block);
            for (int meta = 0; meta < 16; meta++) {
                boolean has;
                try {
                    has = block.hasTileEntity(meta);
                } catch (Throwable t) {
                    has = false;
                }
                if (!has) continue;
                int x = this.ox + (i % 48) * 4, z = this.oz + (i / 48) * 4;
                i++;
                Probe p = new Probe(id + ":" + meta, x, z);
                p.deepError = this.placeOne(block, meta, x, this.deepY, z);
                p.controlError = this.placeOne(block, meta, x, CONTROL_Y, z);
                p.deepClass = className(this.world.getTileEntity(x, this.deepY, z));
                p.controlClass = className(this.world.getTileEntity(x, CONTROL_Y, z));
                this.tiles.add(p);
            }
        }
        this.out.add("INFO tile entity blocks placed: " + this.tiles.size());
        this.cursorZ = this.oz + ((i + 47) / 48) * 4 + 4;
    }

    /** Stone floor, air above, then the block; identical at both heights. */
    private String placeOne(Block block, int meta, int x, int y, int z) {
        try {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                this.world.setBlock(x + dx, y - 1, z + dz, Blocks.stone, 0, 2);
                for (int dy = 0; dy < 3; dy++) this.world.setBlock(x + dx, y + dy, z + dz, Blocks.air, 0, 2);
            }
            this.world.setBlock(x, y, z, block, meta, 3);
            return null;
        } catch (Throwable t) {
            return t.toString();
        }
    }

    private static String className(Object o) {
        return o == null ? "null"
            : o.getClass()
                .getName();
    }

    // ---------------------------------------------------------------- entities

    /** Entities that destroy their surroundings would ruin neighbouring cells and make the comparison meaningless. */
    private static boolean destructive(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String k : new String[] { "tnt", "nuke", "dynamite", "fireball", "skull", "dragon", "wither", "meteor",
            "bomb", "explosive", "creeper" }) if (n.contains(k)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private void entities() {
        int i = 0, skipped = 0, z0 = this.cursorZ;
        for (Map.Entry<String, Class<? extends Entity>> e : ((Map<String, Class<? extends Entity>>) EntityList.stringToClassMapping)
            .entrySet()) {
            Class<?> c = e.getValue();
            // Projectiles and hanging entities need their shooter/item/facing; created bare they crash on impact or
            // save at any height (vanilla potions and paintings included).
            if (destructive(e.getKey()) || c == null
                || net.minecraft.entity.IProjectile.class.isAssignableFrom(c)
                || net.minecraft.entity.projectile.EntityFireball.class.isAssignableFrom(c)
                || net.minecraft.entity.EntityHanging.class.isAssignableFrom(c)) continue;
            int x = this.ox + 2 + (i % 32) * 6, z = z0 + 2 + (i / 32) * 6;
            Entity deep = this.spawnBoxed(e.getKey(), x, this.deepY, z);
            Entity control = this.spawnBoxed(e.getKey(), x, CONTROL_Y, z);
            i++;
            if (deep == null && control == null) {
                this.out.add("SKIP entity " + e.getKey() + ": cannot be created by name at either height");
                continue;
            }
            if (deep == null || control == null) {
                this.finding("entity " + e.getKey() + " spawns only " + (deep == null ? "at control" : "deep"));
                if (deep != null) deep.setDead();
                if (control != null) control.setDead();
                continue;
            }
            // Entities created by name skip the setup their item/spawner normally does; some then crash on tick or
            // save (GC landers, paintings). Exercise both once here so such entities never reach the world tick.
            String dErr = exercise(deep), cErr = exercise(control);
            if (dErr != null && cErr != null) {
                // Second chance with the constructor the mod's item uses, e.g. GC rockets (World, x, y, z, type).
                deep.setDead();
                control.setDead();
                Entity d2 = this.spawnFull(c, x, this.deepY, z), c2 = this.spawnFull(c, x, CONTROL_Y, z);
                if (d2 != null && c2 != null) {
                    deep = d2;
                    control = c2;
                    dErr = exercise(deep);
                    cErr = exercise(control);
                    if (dErr == null && cErr == null)
                        this.out.add("INFO entity " + e.getKey() + " created with its full constructor");
                }
            }
            if (dErr != null || cErr != null) {
                deep.setDead();
                control.setDead();
                if (dErr == null || cErr == null || !dErr.equals(cErr)) {
                    this.finding("entity " + e.getKey() + " deep error=" + dErr + " control error=" + cErr);
                } else {
                    skipped++;
                    this.out
                        .add("SKIP entity " + e.getKey() + ": fails at both heights when created bare (" + dErr + ")");
                }
                continue;
            }
            this.entities.add(new EntityProbe(e.getKey(), deep, control));
        }
        this.out.add("INFO entities spawned: " + this.entities.size());
        this.out.add(
            "INFO entities that fail identically at both heights when created without their item (skipped): "
                + skipped);
        this.cursorZ = z0 + ((i + 31) / 32) * 6 + 4;
    }

    private static String exercise(Entity e) {
        try {
            e.onUpdate();
            e.writeToNBT(new NBTTagCompound());
            return null;
        } catch (Throwable t) {
            return t.getClass()
                .getName();
        }
    }

    /** Constructor {@code (World, double, double, double, SomeEnum)} with the enum's first constant, if any. */
    private Entity spawnFull(Class<?> c, int x, int y, int z) {
        for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
            Class<?>[] p = k.getParameterTypes();
            if (p.length != 5 || p[0] != net.minecraft.world.World.class || p[1] != double.class || !p[4].isEnum())
                continue;
            Object[] values = p[4].getEnumConstants();
            if (values.length == 0) continue;
            try {
                Entity e = (Entity) k.newInstance(this.world, x + 0.5, (double) y, z + 0.5, values[0]);
                return this.world.spawnEntityInWorld(e) ? e : null;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Sealed 3x3x3 stone cavity (no light, no sky) under a solid stone column, entity at its floor. */
    private Entity spawnBoxed(String name, int x, int y, int z) {
        // Solid stone up to 16 blocks above: tall entities (giants) must meet the same blocks at both heights, not
        // terrain at one and open air at the other.
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 16; dy++) for (int dz = -2; dz <= 2; dz++) {
            boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy >= 3;
            this.world.setBlock(x + dx, y + dy, z + dz, shell ? Blocks.stone : Blocks.air, 0, 2);
        }
        try {
            Entity e = EntityList.createEntityByName(name, this.world);
            if (e == null) return null;
            e.setLocationAndAngles(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
            return this.world.spawnEntityInWorld(e) ? e : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Immersive Engineering multiblocks

    private void multiblocks() {
        List<?> list;
        Method create, trigger, manual, unique;
        try {
            Class<?> handler = Class.forName("blusunrize.immersiveengineering.api.MultiblockHandler");
            Class<?> mb = Class.forName("blusunrize.immersiveengineering.api.MultiblockHandler$IMultiblock");
            list = (List<?>) handler.getMethod("getMultiblocks")
                .invoke(null);
            create = mb.getMethod(
                "createStructure",
                net.minecraft.world.World.class,
                int.class,
                int.class,
                int.class,
                int.class,
                EntityPlayer.class);
            trigger = mb.getMethod("isBlockTrigger", Block.class, int.class);
            manual = mb.getMethod("getStructureManual");
            unique = mb.getMethod("getUniqueName");
        } catch (ReflectiveOperationException e) {
            this.out.add("INFO Immersive Engineering not present");
            return;
        }
        EntityPlayer player = FakePlayerFactory.getMinecraft(this.world);
        int i = 0, formedBoth = 0, z0 = this.cursorZ;
        for (Object m : list) {
            int x = this.ox + 1 + (i % 12) * 16, z = z0 + 1 + (i / 12) * 16;
            i++;
            try {
                String name = (String) unique.invoke(m);
                ItemStack[][][] layout = (ItemStack[][][]) manual.invoke(m);
                boolean deep = this.formMultiblock(m, layout, create, trigger, player, x, this.deepY, z);
                boolean control = this.formMultiblock(m, layout, create, trigger, player, x, CONTROL_Y, z);
                if (deep != control)
                    this.finding("multiblock " + name + " formed deep=" + deep + " control=" + control);
                else if (deep) formedBoth++;
                else this.out
                    .add("INFO multiblock " + name + " not formed by the probe at either height (inconclusive)");
            } catch (Throwable t) {
                this.finding("multiblock probe crashed: " + t);
            }
        }
        this.out.add("INFO IE multiblocks formed at both heights: " + formedBoth + "/" + list.size());
        this.cursorZ = z0 + ((i + 11) / 12) * 16;
    }

    /** Builds the manual layout (and its mirror) and tries every trigger block and horizontal side. */
    private boolean formMultiblock(Object mb, ItemStack[][][] layout, Method create, Method trigger,
        EntityPlayer player, int x0, int y0, int z0) throws ReflectiveOperationException {
        for (int mirror = 0; mirror < 2; mirror++) {
            this.clear(x0, y0, z0, 14);
            List<int[]> triggers = new ArrayList<>();
            for (int h = 0; h < layout.length; h++) for (int l = 0; l < layout[h].length; l++) {
                for (int w = 0; w < layout[h][l].length; w++) {
                    ItemStack s = layout[h][l][w];
                    if (s == null || s.getItem() == null) continue;
                    Block b = Block.getBlockFromItem(s.getItem());
                    if (b == null || b == Blocks.air) continue;
                    int ww = mirror == 0 ? w : layout[h][l].length - 1 - w;
                    int x = x0 + ww, y = y0 + h, z = z0 + l;
                    this.world.setBlock(x, y, z, b, s.getItemDamage(), 2);
                    if ((Boolean) trigger.invoke(mb, b, s.getItemDamage())) triggers.add(new int[] { x, y, z });
                }
            }
            for (int[] t : triggers) for (int side = 2; side <= 5; side++) {
                if (this.world.getBlock(t[0], t[1], t[2]) == Blocks.air) continue;
                if ((Boolean) create.invoke(mb, this.world, t[0], t[1], t[2], side, player)) return true;
            }
        }
        return false;
    }

    private void clear(int x0, int y0, int z0, int size) {
        for (int x = x0 - 1; x <= x0 + size; x++) for (int z = z0 - 1; z <= z0 + size; z++) {
            this.world.setBlock(x, y0 - 1, z, Blocks.stone, 0, 2);
            for (int y = y0; y <= y0 + size; y++) this.world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
    }

    // ---------------------------------------------------------------- ticking, snapshot, verification

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (this.mechanics != null) this.mechanics.tick();
        if (++this.ticks != TICKS) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        this.afterTicks();
        if (this.mechanics != null) this.findings += this.mechanics.report(this.out);
        this.finish(MinecraftServer.getServer(), "akashicbellow-compat.txt");
    }

    private void afterTicks() {
        Set<Object> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
        loaded.addAll(this.world.loadedTileEntityList);
        int ok = 0;
        for (Probe p : this.tiles) {
            TileEntity deep = this.world.getTileEntity(p.x, this.deepY, p.z);
            TileEntity control = this.world.getTileEntity(p.x, CONTROL_Y, p.z);
            String dState = state(deep, loaded), cState = state(control, loaded);
            boolean same = equalOrNull(p.deepError, p.controlError) && p.deepClass.equals(p.controlClass)
                && dState.equals(cState);
            if (!same) {
                this.finding(
                    "tile " + p.name
                        + " deep["
                        + p.deepClass
                        + ", "
                        + dState
                        + ", "
                        + p.deepError
                        + "] control["
                        + p.controlClass
                        + ", "
                        + cState
                        + ", "
                        + p.controlError
                        + "]");
            } else ok++;
        }
        this.out.add(
            "INFO tile entities identical at both heights after " + TICKS + " ticks: " + ok + "/" + this.tiles.size());

        int eok = 0;
        for (EntityProbe e : this.entities) {
            // Movement inside the cavity is random (bats, slimes); what matters is surviving and staying in the cell.
            boolean dAlive = !e.deep.isDead, cAlive = !e.control.isDead;
            boolean dKept = e.deep.posY > e.deepY0 - 1.5, cKept = e.control.posY > e.controlY0 - 1.5;
            // Only deep doing worse than the control is a finding (a bare eye of ender flies toward Y 0: up when
            // deep, down at the control).
            if (cAlive && !dAlive || cKept && !dKept) {
                this.finding(
                    String.format(
                        "entity %s deep[alive=%s y=%.2f from %.2f] control[alive=%s y=%.2f from %.2f]",
                        e.name,
                        dAlive,
                        e.deep.posY,
                        e.deepY0,
                        cAlive,
                        e.control.posY,
                        e.controlY0));
            } else eok++;
        }
        this.out.add("INFO entities behaving the same at both heights: " + eok + "/" + this.entities.size());

        // The snapshot is taken by the final save itself: the server may run catch-up ticks after
        // initiateShutdown, and a tile entity ticking after an early snapshot would look like a persistence bug.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldSave(net.minecraftforge.event.world.WorldEvent.Save event) {
        if (event.world != this.world) return;
        NBTTagList snapshot = new NBTTagList();
        for (Probe p : this.tiles) {
            TileEntity deep = this.world.getTileEntity(p.x, this.deepY, p.z);
            TileEntity control = this.world.getTileEntity(p.x, CONTROL_Y, p.z);
            if (deep == null || control == null) continue;
            NBTTagCompound e = new NBTTagCompound();
            e.setString("Name", p.name);
            e.setInteger("X", p.x);
            e.setInteger("Z", p.z);
            e.setTag("Deep", positionless(deep));
            e.setTag("Control", positionless(control));
            snapshot.appendTag(e);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("DeepY", this.deepY);
        root.setTag("Tiles", snapshot);
        try {
            CompressedStreamTools.write(root, new File(SNAPSHOT));
        } catch (Exception ex) {
            AkashicBellow.LOG.error("Cannot write compat snapshot", ex);
        }
    }

    private static boolean equalOrNull(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String state(TileEntity te, Set<Object> loaded) {
        if (te == null) return "absent";
        if (te.isInvalid()) return "invalid";
        return loaded.contains(te) ? "ticking" : "not-in-world-list";
    }

    private static NBTTagCompound positionless(TileEntity te) {
        NBTTagCompound tag = new NBTTagCompound();
        try {
            te.writeToNBT(tag);
        } catch (Throwable t) {
            tag.setString("WriteError", t.toString());
        }
        tag.removeTag("x");
        tag.removeTag("y");
        tag.removeTag("z");
        return tag;
    }

    /** Fresh JVM: every deep tile entity must come back from disk as it was saved. */
    private void verify() {
        NBTTagCompound root;
        try {
            root = CompressedStreamTools.read(new File(SNAPSHOT));
        } catch (Exception e) {
            root = null;
        }
        if (root == null) {
            this.finding("no snapshot from the compat phase");
            return;
        }
        int deepY = root.getInteger("DeepY");
        NBTTagList list = root.getTagList("Tiles", 10);
        int ok = 0, volatileTiles = 0;
        Map<String, String> failures = new LinkedHashMap<>();
        // Keys that change on reload at the control height, per block id. A mod quirk shows up on random instances
        // (IC2 crops grow at random and do not mark their chunk dirty), so it is attributed per block, not per cell.
        Map<String, Set<String>> controlQuirks = new java.util.HashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            TileEntity control = this.world.getTileEntity(e.getInteger("X"), CONTROL_Y, e.getInteger("Z"));
            if (control == null) continue;
            controlQuirks.computeIfAbsent(blockId(e.getString("Name")), k -> new java.util.HashSet<>())
                .addAll(changedKeys(e.getCompoundTag("Control"), positionless(control)));
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int x = e.getInteger("X"), z = e.getInteger("Z");
            TileEntity deep = this.world.getTileEntity(x, deepY, z);
            if (deep == null) {
                failures.put(e.getString("Name"), "missing after reload");
                continue;
            }
            Set<String> deepChanged = changedKeys(e.getCompoundTag("Deep"), positionless(deep));
            if (deepChanged.isEmpty()) {
                ok++;
                continue;
            }
            // Keys a mod rewrites on every load (timers, caches, unsaved state) change at the control height too.
            Set<String> controlChanged = controlQuirks
                .getOrDefault(blockId(e.getString("Name")), Collections.emptySet());
            if (controlChanged.containsAll(deepChanged)) volatileTiles++;
            else {
                deepChanged.removeAll(controlChanged);
                NBTTagCompound saved = e.getCompoundTag("Deep"), loaded = positionless(deep);
                StringBuilder detail = new StringBuilder();
                for (String k : deepChanged) detail.append(' ')
                    .append(k)
                    .append(": ")
                    .append(saved.getTag(k))
                    .append(" -> ")
                    .append(loaded.getTag(k));
                failures.put(e.getString("Name"), "NBT differs after reload only deep:" + detail);
            }
        }
        for (Map.Entry<String, String> f : failures.entrySet()) this.finding("tile " + f.getKey() + " " + f.getValue());
        this.out.add("INFO deep tile entities reloaded identical: " + ok + "/" + list.tagCount());
        this.out.add(
            "INFO deep tile entities whose changed NBT keys also change at the control height for that block (not a finding): "
                + volatileTiles);
    }

    /** {@code modid:name:meta} to {@code modid:name}. */
    private static String blockId(String name) {
        int i = name.lastIndexOf(':');
        return i < 0 ? name : name.substring(0, i);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> changedKeys(NBTTagCompound before, NBTTagCompound after) {
        Set<String> keys = new java.util.HashSet<>((Set<String>) before.func_150296_c());
        keys.addAll((Set<String>) after.func_150296_c());
        Set<String> changed = new java.util.TreeSet<>();
        for (String k : keys) {
            Object a = before.getTag(k), b = after.getTag(k);
            if (a == null ? b != null : !a.equals(b)) changed.add(k);
        }
        return changed;
    }

    private void finish(MinecraftServer server, String file) {
        this.out.add(this.findings == 0 ? "RESULT PASS" : "RESULT FINDINGS (" + this.findings + ")");
        for (String line : this.out) AkashicBellow.LOG.info("[compat] {}", line);
        try {
            Files.write(new File(file).toPath(), this.out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            AkashicBellow.LOG.error("Cannot write compat report", e);
        }
        if (Boolean.getBoolean("akashicbellow.selftest.exit")) server.initiateShutdown();
    }
}
