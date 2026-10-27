package com.akashiic.bellow.storage;

import java.nio.ByteBuffer;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.api.IDeepWorld;
import com.akashiic.bellow.compat.angelica.AngelicaTiles;
import com.akashiic.bellow.core.Compat;
import com.akashiic.bellow.net.DeepNet;
import com.falsepattern.chunk.api.DataManager;
import com.falsepattern.chunk.internal.DataRegistryImpl;

/**
 * Persists and transports the deep sections through ChunkAPI.
 * <p>
 * NBT: {@code akashicbellow.deep = {V, Gen, Sections:[{Y:signed byte, <sub-chunk managers>}]}}. Sections are written by
 * the same sub-chunk managers that write vanilla sections (vanilla ids/meta/light, EndlessIDs), so the format of a deep
 * section is identical to a vanilla one. They are never stored in the vanilla {@code Sections} list: the vanilla and
 * ChunkAPI readers index an array with {@code Y} and would throw on negative values.
 * <p>
 * Packet: {@code short minY, int slotMask, then per set bit: int length, ChunkAPI cubic blob}. Empty for chunks without
 * deep data and for the "unload" form of S21 ({@code forceUpdate && mask == 0}).
 */
public final class DeepDataManager implements DataManager.ChunkDataManager, DataManager.PacketDataManager {

    public static final String DOMAIN = AkashicBellow.MODID;
    public static final String ID = "deep";
    private static final byte NBT_VERSION = 1;

    /** Upper bound for one section blob when the registry reports less (EndlessIDs worst case is ~25 KB). */
    private static final int SECTION_BOUND = 32 * 1024;

    private final int maxPacketSize;
    private final ThreadLocal<byte[]> scratch;
    private boolean warnedOverflow;

    public DeepDataManager() {
        int perSection = Math.max(DataRegistryImpl.maxPacketSizeCubic(), SECTION_BOUND);
        this.maxPacketSize = 2 + 4 + DeepConstants.MAX_DEEP_SECTIONS * (4 + perSection);
        this.scratch = ThreadLocal.withInitial(() -> new byte[perSection]);
    }

    @Override
    public @NotNull String domain() {
        return DOMAIN;
    }

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String version() {
        return "1";
    }

    @Override
    public @Nullable String newInstallDescription() {
        return "Akashic Bellow stores the blocks below Y 0 of each chunk under the 'akashicbellow' tag."
            + " Chunks saved before the install receive their deep layer when they are loaded.";
    }

    @Override
    public @NotNull String uninstallMessage() {
        return "Akashic Bellow removed: every block below Y 0 is discarded when its chunk is saved again."
            + " Natural bedrock that the mod replaced at Y 0..4 is not restored.";
    }

    @Override
    public @Nullable String versionChangeMessage(String priorVersion) {
        return null;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeChunkToNBT(Chunk chunk, NBTTagCompound nbt) {
        IDeepChunk deep = (IDeepChunk) chunk;
        nbt.setByte("V", NBT_VERSION);
        nbt.setByte("Gen", deep.bellow$getGenState());
        ExtendedBlockStorage[] sections = deep.bellow$peekDeepSections();
        if (sections == null) return;
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < sections.length; slot++) {
            ExtendedBlockStorage ebs = sections[slot];
            if (ebs == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Y", (byte) (slot - DeepConstants.MAX_DEEP_SECTIONS));
            DataRegistryImpl.writeSubChunkToNBT(chunk, ebs, tag);
            list.appendTag(tag);
        }
        nbt.setTag("Sections", list);
    }

    @Override
    public void readChunkFromNBT(Chunk chunk, NBTTagCompound nbt) {
        // May run on the chunk IO thread: touch nothing but this chunk.
        IDeepChunk deep = (IDeepChunk) chunk;
        deep.bellow$setFromDisk(true);
        deep.bellow$setGenState(nbt.getByte("Gen"));
        if (!nbt.hasKey("Sections", 9)) return;
        NBTTagList list = nbt.getTagList("Sections", 10);
        if (list.tagCount() == 0) return;
        boolean hasSky = !chunk.worldObj.provider.hasNoSky;
        ExtendedBlockStorage[] sections = new ExtendedBlockStorage[DeepConstants.MAX_DEEP_SECTIONS];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int sectionY = tag.getByte("Y");
            int slot = DeepConstants.slot(sectionY);
            if (slot < 0) {
                AkashicBellow.LOG.error(
                    "Chunk [{}, {}]: ignoring deep section with invalid Y {}",
                    chunk.xPosition,
                    chunk.zPosition,
                    sectionY);
                continue;
            }
            ExtendedBlockStorage ebs = new ExtendedBlockStorage(sectionY << 4, hasSky);
            DataRegistryImpl.readSubChunkFromNBT(chunk, ebs, tag);
            ebs.removeInvalidBlocks();
            sections[slot] = ebs;
        }
        deep.bellow$setDeepSections(sections);
    }

    @Override
    public void cloneChunk(Chunk from, Chunk to) {
        IDeepChunk src = (IDeepChunk) from;
        IDeepChunk dst = (IDeepChunk) to;
        dst.bellow$setGenState(src.bellow$getGenState());
        dst.bellow$setFromDisk(src.bellow$isFromDisk());
        ExtendedBlockStorage[] sections = src.bellow$peekDeepSections();
        if (sections == null) {
            dst.bellow$setDeepSections(null);
            return;
        }
        boolean hasSky = !to.worldObj.provider.hasNoSky;
        ExtendedBlockStorage[] copy = new ExtendedBlockStorage[sections.length];
        for (int slot = 0; slot < sections.length; slot++) {
            ExtendedBlockStorage ebs = sections[slot];
            if (ebs == null) continue;
            ExtendedBlockStorage c = new ExtendedBlockStorage(ebs.getYLocation(), hasSky);
            DataRegistryImpl.cloneSubChunk(from, ebs, c);
            c.removeInvalidBlocks();
            copy[slot] = c;
        }
        dst.bellow$setDeepSections(copy);
    }

    // ------------------------------------------------------------------ packets

    @Override
    public int maxPacketSize() {
        return this.maxPacketSize;
    }

    @Override
    public void writeToBuffer(Chunk chunk, int subChunkMask, boolean forceUpdate, ByteBuffer buffer) {
        if (forceUpdate && subChunkMask == 0) return; // S21 "unload chunk" form
        int minY = BellowAPI.getMinY(chunk.worldObj);
        if (minY >= 0) return;
        ExtendedBlockStorage[] sections = ((IDeepChunk) chunk).bellow$peekDeepSections();
        if (sections == null) return;

        int wanted = forceUpdate ? -1 : DeepNet.pendingSlotMask();
        int firstSlot = (minY >> 4) + DeepConstants.MAX_DEEP_SECTIONS;
        int mask = 0;
        for (int slot = firstSlot; slot < sections.length; slot++) {
            if (sections[slot] != null && (wanted & 1 << slot) != 0) mask |= 1 << slot;
        }
        if (mask == 0) return;

        buffer.putShort((short) minY);
        int maskPos = buffer.position();
        buffer.putInt(mask);
        byte[] tmp = this.scratch.get();
        int written = 0;
        for (int slot = firstSlot; slot < sections.length; slot++) {
            if ((mask & 1 << slot) == 0) continue;
            int len = DataRegistryImpl.writeToBufferCubic(chunk, sections[slot], tmp);
            if (buffer.remaining() < 4 + len) {
                if (!this.warnedOverflow) {
                    this.warnedOverflow = true;
                    AkashicBellow.LOG.error(
                        "Deep chunk data of [{}, {}] exceeds the packet budget; sending a truncated set of sections",
                        chunk.xPosition,
                        chunk.zPosition);
                }
                break;
            }
            buffer.putInt(len);
            buffer.put(tmp, 0, len);
            written |= 1 << slot;
        }
        buffer.putInt(maskPos, written);
    }

    @Override
    public void readFromBuffer(Chunk chunk, int subChunkMask, boolean forceUpdate, ByteBuffer buffer) {
        IDeepChunk deep = (IDeepChunk) chunk;
        if (!buffer.hasRemaining()) {
            if (forceUpdate) deep.bellow$setDeepSections(null);
            return;
        }
        int minY = buffer.getShort();
        World world = chunk.worldObj;
        if (((IDeepWorld) world).bellow$getMinY() != minY) ((IDeepWorld) world).bellow$setMinY(minY);

        int mask = buffer.getInt();
        ExtendedBlockStorage[] sections = deep.bellow$getDeepSections();
        boolean hasSky = !world.provider.hasNoSky;
        byte[] tmp = this.scratch.get();
        for (int slot = 0; slot < sections.length; slot++) {
            if ((mask & 1 << slot) == 0) {
                if (forceUpdate) sections[slot] = null;
                continue;
            }
            int len = buffer.getInt();
            byte[] data = len <= tmp.length ? tmp : new byte[len];
            buffer.get(data, 0, len);
            ExtendedBlockStorage ebs = sections[slot];
            if (ebs == null)
                ebs = sections[slot] = new ExtendedBlockStorage((slot - DeepConstants.MAX_DEEP_SECTIONS) << 4, hasSky);
            DataRegistryImpl.readFromBufferCubic(chunk, ebs, data);
            ebs.removeInvalidBlocks();
        }
        if (world.isRemote) {
            createTileEntities(chunk, sections, mask);
            int x = chunk.xPosition << 4;
            int z = chunk.zPosition << 4;
            world.markBlockRangeForRenderUpdate(x, minY, z, x + 15, -1, z + 15);
        }
    }

    /**
     * Client side: creates the tile entities of the received deep sections, as vanilla does lazily and Angelica eagerly
     * for Y >= 0 at the end of {@code Chunk.fillChunk}. Angelica's chunk meshing reads tile entities from a snapshot
     * and
     * forbids creating them on its worker threads, so a deep tile entity that is not created here is never rendered.
     */
    private static void createTileEntities(Chunk chunk, ExtendedBlockStorage[] sections, int mask) {
        for (int slot = 0; slot < sections.length; slot++) {
            ExtendedBlockStorage ebs = sections[slot];
            if ((mask & 1 << slot) == 0 || ebs == null || ebs.isEmpty()) continue;
            int baseY = (slot - DeepConstants.MAX_DEEP_SECTIONS) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                Block block = ebs.getBlockByExtId(x, y, z);
                int meta = ebs.getExtBlockMetadata(x, y, z);
                if (block == Blocks.air || !block.hasTileEntity(meta)) continue;
                TileEntity existing = (TileEntity) chunk.chunkTileEntityMap.get(new ChunkPosition(x, baseY + y, z));
                if (existing != null && !existing.isInvalid()) continue;
                TileEntity te = block.createTileEntity(chunk.worldObj, meta);
                if (te == null) continue;
                te.xCoord = (chunk.xPosition << 4) + x;
                te.yCoord = baseY + y;
                te.zCoord = (chunk.zPosition << 4) + z;
                if (Compat.angelica) AngelicaTiles.awaitDescriptor(te);
                chunk.addTileEntity(te);
            }
        }
    }
}
