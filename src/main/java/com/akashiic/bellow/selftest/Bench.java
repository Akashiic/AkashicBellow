package com.akashiic.bellow.selftest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ThreadedFileIOBase;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.api.BellowAPI;

import io.netty.buffer.Unpooled;

/**
 * Cost measurement ({@code -Dakashicbellow.selftest.phase=bench}): generates a fresh 23x23 chunk area far from spawn
 * and reports heap per chunk, generation time, region file bytes per chunk and chunk packet bytes. Run it once per
 * configuration (deep layer disabled, -64, -512) on a new world and compare.
 */
public final class Bench {

    private static final int CENTER = 4000; // chunk coordinates, far from anything generated at startup
    private static final int RADIUS = 11;

    private Bench() {}

    public static void run(MinecraftServer server) {
        WorldServer world = server.worldServerForDimension(0);
        List<String> out = new ArrayList<>();
        int chunks = (2 * RADIUS + 1) * (2 * RADIUS + 1);
        out.add("INFO floor " + BellowAPI.getMinY(world) + ", " + chunks + " new chunks");

        long heap0 = usedHeap();
        long t0 = System.nanoTime();
        List<Chunk> loaded = new ArrayList<>();
        for (int cx = CENTER - RADIUS; cx <= CENTER + RADIUS; cx++) {
            for (int cz = CENTER - RADIUS; cz <= CENTER + RADIUS; cz++) {
                loaded.add(world.theChunkProviderServer.loadChunk(cx, cz));
            }
        }
        long t1 = System.nanoTime();
        long heap1 = usedHeap();
        out.add(String.format("MEASURE generation ms/chunk: %.2f", (t1 - t0) / 1e6 / chunks));
        out.add(String.format("MEASURE heap KB/chunk: %.1f", (heap1 - heap0) / 1024.0 / chunks));

        long packetBytes = 0;
        int sampled = 0;
        for (Chunk c : loaded) {
            if (Math.abs(c.xPosition - CENTER) > RADIUS - 2 || Math.abs(c.zPosition - CENTER) > RADIUS - 2) continue;
            try {
                PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
                new S21PacketChunkData(c, true, 65535).writePacketData(buf);
                packetBytes += buf.readableBytes();
                sampled++;
            } catch (Exception e) {
                out.add("FAIL packet: " + e);
            }
        }
        out.add(
            String.format("MEASURE chunk packet KB (full column, compressed): %.1f", packetBytes / 1024.0 / sampled));

        try {
            world.saveAllChunks(true, null);
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        } catch (MinecraftException | InterruptedException e) {
            out.add("FAIL save: " + e);
        }
        File regionDir = new File(world.getChunkSaveLocation(), "region");
        long regionBytes = 0;
        for (int rx = (CENTER - RADIUS) >> 5; rx <= (CENTER + RADIUS) >> 5; rx++) {
            for (int rz = (CENTER - RADIUS) >> 5; rz <= (CENTER + RADIUS) >> 5; rz++) {
                regionBytes += new File(regionDir, "r." + rx + "." + rz + ".mca").length();
            }
        }
        out.add(String.format("MEASURE region file KB/chunk: %.1f", regionBytes / 1024.0 / chunks));

        for (String l : out) AkashicBellow.LOG.info("[bench] {}", l);
        try {
            Files.write(new File("akashicbellow-bench.txt").toPath(), out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            AkashicBellow.LOG.error("Cannot write bench report", e);
        }
        if (Boolean.getBoolean("akashicbellow.selftest.exit")) server.initiateShutdown();
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread()
                    .interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }
}
