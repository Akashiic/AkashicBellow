package com.akashiic.bellow.weagent;

import static com.akashiic.bellow.weagent.Patcher.GET_BLOCK_Y;
import static com.akashiic.bellow.weagent.Patcher.MATH_MAX;
import static com.akashiic.bellow.weagent.Patcher.WORLD;
import static com.akashiic.bellow.weagent.Patcher.anyOf;
import static com.akashiic.bellow.weagent.Patcher.call;
import static com.akashiic.bellow.weagent.Patcher.concat;
import static com.akashiic.bellow.weagent.Patcher.copy;
import static com.akashiic.bellow.weagent.Patcher.field;
import static com.akashiic.bellow.weagent.Patcher.ldc;
import static com.akashiic.bellow.weagent.Patcher.list;
import static com.akashiic.bellow.weagent.Patcher.op;
import static com.akashiic.bellow.weagent.Patcher.push;
import static com.akashiic.bellow.weagent.Patcher.type;
import static com.akashiic.bellow.weagent.Patcher.var;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * The WorldEdit 6.1.9 sites that assume the world starts at Y 0, each replaced by the Bellow floor of the world.
 * Classes are identified by the SHA-256 of their bytes in {@code worldedit-bukkit-6.1.9.jar} (the AkashicWE-EID
 * coremod rewrites only {@code BaseBlock}, which is not a target, so the hashes hold for its patched jar too).
 * <p>
 * Not patched, on purpose: {@code //regen} (BukkitWorld.regenerate copies Y 0..255 only), snapshot restore (Anvil
 * sections 0..15), and the snow/green/thaw loops, which work down from the surface.
 */
final class WorldEditPatches implements Opcodes {

    interface ClassPatch {

        void apply(Patcher p);
    }

    static final class Target {

        final String sha256;
        final ClassPatch patch;

        Target(String sha256, ClassPatch patch) {
            this.sha256 = sha256;
            this.patch = patch;
        }
    }

    static final String WE = "com/sk89q/worldedit/";
    static final String VECTOR = WE + "Vector";
    static final String PLAYER = WE + "entity/Player";
    static final String COMMAND_DESC = "(L" + PLAYER + ";L" + WE + "LocalSession;L" + WE
        + "EditSession;Lcom/sk89q/minecraft/util/commands/CommandContext;)V";
    static final String MIN_Y_FIELD = "akashicbellow$minY";

    static final Map<String, Target> TARGETS;

    static {
        Map<String, Target> t = new LinkedHashMap<>();
        t.put(
            WE + "extent/validation/DataValidatorExtent",
            new Target(
                "842e0d5e038a694fe6dc6b880b90680afff8706e5734bba2736ebb42304181e9",
                WorldEditPatches::dataValidator));
        t.put(
            WE + "regions/CuboidRegion",
            new Target("362b76352eecced5d669a80fb9ac1709624da423e37e82d1185c086f82fc0ebd", WorldEditPatches::cuboid));
        t.put(
            WE + "regions/Polygonal2DRegion",
            new Target(
                "8a4cf56eb017c956f047b5e1a90196d58acfc93a538d2045203fc88afc39af62",
                WorldEditPatches::polygonal));
        t.put(
            WE + "world/AbstractWorld",
            new Target(
                "d6b5e26269ec39aaf1e76a08f8e235a37f7c94e7b840e64669fee6c1e483f4df",
                WorldEditPatches::abstractWorld));
        t.put(
            WE + "bukkit/selections/Polygonal2DSelection",
            new Target(
                "b4d7c6552a91e63e887fa3b34f4934741fd6b6b471f6bf4f676a508f2954ff51",
                p -> selection(
                    p,
                    "(Lorg/bukkit/World;Ljava/util/List;II)V",
                    5)));
        t.put(
            WE + "bukkit/selections/CylinderSelection",
            new Target(
                "a07f45a0259c68ab0b97401ee39f63d1fe03672b9ea951858dadcdebe411d965",
                p -> selection(
                    p,
                    "(Lorg/bukkit/World;L" + WE + "BlockVector2D;L" + WE + "BlockVector2D;II)V",
                    6)));
        t.put(
            WE + "command/SelectionCommands",
            new Target(
                "ac991e08f4e8bfddddfd5f16be8db3fa23ea17322c66df5177337ba2ebf55fce",
                WorldEditPatches::selectionCommands));
        t.put(
            WE + "command/UtilityCommands",
            new Target(
                "e0f1cf850fa4b415f85a684f4f56352ca689e98a731b7e775640b5bd1ef2401d",
                WorldEditPatches::utilityCommands));
        t.put(
            WE + "EditSession",
            new Target(
                "cbe427288218d7994203652b1cdd3e2c1f0d913f44baabf6f936a85b5e1e2f8d",
                WorldEditPatches::editSession));
        t.put(
            WE + "util/TargetBlock",
            new Target(
                "00a6d537bf602880b108aee7650e24fc577d7986a98af9af8c1bdf0ec8bb93e3",
                WorldEditPatches::targetBlock));
        t.put(
            WE + "extension/platform/AbstractPlayerActor",
            new Target(
                "4b68d87f42d486bdb26b655a99e351b44a88024408b1a22555bb7ffa99c2b293",
                WorldEditPatches::playerActor));
        TARGETS = Collections.unmodifiableMap(t);
    }

    private WorldEditPatches() {}

    /**
     * Every block WorldEdit writes passes here: {@code if (y < 0 || y > maxY) return false}. The floor is read once
     * per EditSession (a new field set by the constructor), not per block.
     */
    static void dataValidator(Patcher p) {
        String owner = p.cls.name;
        p.cls.fields.add(new FieldNode(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, MIN_Y_FIELD, "I", null, null));
        MethodNode init = p.method("<init>", "(L" + WE + "extent/Extent;L" + WORLD + ";)V");
        AbstractInsnNode ret = p.single(init, op(RETURN))[0];
        p.insertBefore(
            init,
            ret,
            concat(
                p.floor(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 2)),
                new FieldInsnNode(PUTFIELD, owner, MIN_Y_FIELD, "I")));

        MethodNode set = p.method("setBlock", "(L" + VECTOR + ";L" + WE + "blocks/BaseBlock;)Z");
        AbstractInsnNode[] s = p.single(set, var(ILOAD, 3), op(IFLT));
        p.compareWithFloor(
            set,
            s[0],
            (JumpInsnNode) s[1],
            list(new VarInsnNode(ALOAD, 0), new FieldInsnNode(GETFIELD, owner, MIN_Y_FIELD, "I")));
    }

    /** Both corners are clamped with {@code clampY(0, maxY)}. */
    static void cuboid(Patcher p) {
        MethodNode m = p.method("recalculate", "()V");
        FieldInsnNode world = p.getField("world", "L" + WORLD + ";");
        for (AbstractInsnNode[] s : p.find(m, 2, op(ICONST_0), var(ALOAD, 0), field(GETFIELD, "world"), op(IFNONNULL)))
            p.replace(m, s[0], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));
    }

    /** {@code minY = min(max(0, minY), maxY)} and the same for maxY. */
    static void polygonal(Patcher p) {
        MethodNode m = p.method("recalculate", "()V");
        FieldInsnNode world = p.getField("world", "L" + WORLD + ";");
        for (AbstractInsnNode[] s : p.find(m, 2, op(ICONST_0), var(ALOAD, 0), field(GETFIELD, "minY", "maxY"), MATH_MAX))
            p.replace(m, s[0], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));
    }

    /** {@code getMinimumPoint() = (-30000000, 0, -30000000)}: cylinder and sphere selections size from it. */
    static void abstractWorld(Patcher p) {
        MethodNode m = p.method("getMinimumPoint", "()L" + VECTOR + ";");
        AbstractInsnNode[] s = p.single(
            m,
            ldc(-30000000),
            op(ICONST_0),
            ldc(-30000000),
            call(INVOKESPECIAL, VECTOR, "<init>", "(III)V"));
        p.replace(m, s[1], p.floor(new VarInsnNode(ALOAD, 0)));
    }

    /** Bukkit selection API (used by other plugins): {@code minY = min(max(0, minY), maxHeight)}. */
    static void selection(Patcher p, String desc, int localWorld) {
        MethodNode m = p.method("<init>", desc);
        p.single(
            m,
            call(INVOKESTATIC, WE + "bukkit/BukkitUtil", "getLocalWorld", "(Lorg/bukkit/World;)L" + WE + "LocalWorld;"),
            var(ASTORE, localWorld));
        for (AbstractInsnNode[] s : p.find(m, 2, op(ICONST_0), op(ILOAD), MATH_MAX))
            p.replace(m, s[0], p.floor(new VarInsnNode(ALOAD, localWorld)));
    }

    /**
     * {@code //chunk} starts the selection at Y 0, and {@code //expand vert} grows it by {@code maxY + 1} each way,
     * which falls short of the floor from high up; it grows by {@code maxY - floor + 1} instead.
     */
    static void selectionCommands(Patcher p) {
        MethodNode chunk = p.method("chunk", COMMAND_DESC);
        p.single(
            chunk,
            var(ALOAD, 1),
            call(INVOKEINTERFACE, PLAYER, "getWorld", "()L" + WORLD + ";"),
            var(ASTORE, 5));
        for (AbstractInsnNode[] s : p.find(
            chunk,
            2,
            op(ICONST_0),
            op(ALOAD),
            call(INVOKEVIRTUAL, WE + "Vector2D", "getBlockZ", "()I"),
            push(16),
            op(IMUL),
            call(INVOKESPECIAL, VECTOR, "<init>", "(III)V"))) p.replace(chunk, s[0], p.floor(new VarInsnNode(ALOAD, 5)));

        MethodNode expand = p.method("expand", COMMAND_DESC);
        for (AbstractInsnNode[] s : p.find(
            expand,
            2,
            var(ALOAD, 1),
            call(INVOKEINTERFACE, PLAYER, "getWorld", "()L" + WORLD + ";"),
            call(INVOKEINTERFACE, WORLD, "getMaxY", "()I"),
            op(ICONST_1),
            op(IADD))) {
            p.insertAfter(expand, s[2], concat(p.floor(new VarInsnNode(ALOAD, 1), copy(s[1])), new InsnNode(ISUB)));
        }
    }

    /** {@code //removebelow} without a height removes {@code maxY + 1} blocks down; make it reach the floor. */
    static void utilityCommands(Patcher p) {
        MethodNode m = p.method("removeBelow", COMMAND_DESC);
        p.single(m, var(ALOAD, 1), call(INVOKEINTERFACE, PLAYER, "getWorld", "()L" + WORLD + ";"), var(ASTORE, 6));
        for (AbstractInsnNode[] s : p.find(
            m,
            2,
            var(ALOAD, 6),
            call(INVOKEINTERFACE, WORLD, "getMaxY", "()I"),
            op(ICONST_1),
            op(IADD))) p.insertAfter(m, s[1], concat(p.floor(new VarInsnNode(ALOAD, 6)), new InsnNode(ISUB)));
    }

    /** {@code //fill}, {@code //drain}, {@code //fixwater}/{@code //fixlava} masks and {@code //cyl} placement. */
    static void editSession(Patcher p) {
        FieldInsnNode world = p.getField("world", "L" + WORLD + ";");
        String mask = WE + "function/mask/BoundedHeightMask";

        MethodNode fill = p.method("fillXZ", "(L" + VECTOR + ";L" + WE + "patterns/Pattern;DIZ)I");
        AbstractInsnNode[] s = p.single(fill, op(ICONST_1), op(IADD), op(ICONST_0), MATH_MAX);
        p.replace(fill, s[2], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));

        MethodNode drain = p.method("drainArea", "(L" + VECTOR + ";D)I");
        s = p.single(drain, type(NEW, mask), op(DUP), op(ICONST_0));
        p.replace(drain, s[2], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));

        MethodNode fix = p.method("fixLiquid", "(L" + VECTOR + ";DII)I");
        s = p.single(fix, type(NEW, mask), op(DUP), op(ICONST_0));
        p.replace(fix, s[2], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));

        // if (pos.getBlockY() < 0) pos = pos.setY(0);
        MethodNode cyl = p.method("makeCylinder", "(L" + VECTOR + ";L" + WE + "patterns/Pattern;DDIZ)I");
        s = p.single(cyl, call(INVOKEVIRTUAL, VECTOR, "getBlockY", "()I"), op(IFGE));
        p.compareWithFloor(cyl, s[0], (JumpInsnNode) s[1], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));
        s = p.single(cyl, var(ALOAD, 1), op(ICONST_0), call(INVOKEVIRTUAL, VECTOR, "setY", "(I)L" + VECTOR + ";"));
        p.replace(cyl, s[1], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));
    }

    /** The trace used by {@code /jumpto} and tools stops at {@code y <= 0}. */
    static void targetBlock(Patcher p) {
        FieldInsnNode world = p.getField("world", "L" + WE + "LocalWorld;");
        MethodNode m = p.method("getAnyTargetBlock", "()L" + WE + "BlockWorldVector;");
        AbstractInsnNode[] s = p.single(m, call(INVOKEVIRTUAL, WE + "BlockWorldVector", "getBlockY", "()I"), op(IFLE));
        p.compareWithFloor(m, s[0], (JumpInsnNode) s[1], p.floor(new VarInsnNode(ALOAD, 0), copy(world)));
    }

    /** {@code /ascend}, {@code /descend}, {@code /ceil}, {@code /up}, {@code /unstuck} and teleports. */
    static void playerActor(Patcher p) {
        String worldVector = WE + "WorldVector";
        MethodInsnNode thisWorld = new MethodInsnNode(INVOKEVIRTUAL, p.cls.name, "getWorld", "()L" + WORLD + ";", false);
        // max(0, pos.getBlockY()) and max(0, pos.getBlockY() +/- k)
        Patcher.Insn[] plain = { op(ICONST_0), op(ALOAD), GET_BLOCK_Y, MATH_MAX };
        Patcher.Insn[] offset = { op(ICONST_0), op(ALOAD), GET_BLOCK_Y, anyOf(ICONST_1, ICONST_2), anyOf(IADD, ISUB),
            MATH_MAX };

        String vecDesc = "(L" + worldVector + ";)V";
        Patcher.Insn[] worldInLocal2 = { var(ALOAD, 1),
            call(INVOKEVIRTUAL, worldVector, "getWorld", "()L" + WE + "LocalWorld;"), var(ASTORE, 2) };

        MethodNode free = p.method("findFreePosition", vecDesc);
        p.single(free, worldInLocal2);
        p.replace(free, p.single(free, plain)[0], p.floor(new VarInsnNode(ALOAD, 2)));

        MethodNode ground = p.method("setOnGround", vecDesc);
        p.single(ground, worldInLocal2);
        p.replace(ground, p.single(ground, plain)[0], p.floor(new VarInsnNode(ALOAD, 2)));
        AbstractInsnNode[] s = p.single(ground, op(ILOAD), op(IFLT)); // for (...; y >= 0; y--)
        p.compareWithFloor(ground, s[0], (JumpInsnNode) s[1], p.floor(new VarInsnNode(ALOAD, 2)));

        MethodNode ascend = p.method("ascendLevel", "()Z");
        p.replace(ascend, p.single(ascend, plain)[0], p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)));

        MethodNode descend = p.method("descendLevel", "()Z");
        p.replace(descend, p.single(descend, offset)[0], p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)));
        s = p.single(descend, op(ILOAD), op(ICONST_1), op(IF_ICMPLT)); // for (...; y >= 1; y--)
        p.replace(
            descend,
            s[1],
            concat(p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)), new InsnNode(ICONST_1), new InsnNode(IADD)));
        s = p.single(descend, op(ILOAD), op(IFLT)); // while (y >= 0)
        p.compareWithFloor(descend, s[0], (JumpInsnNode) s[1], p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)));

        for (String name : new String[] { "ascendToCeiling", "ascendUpwards" }) {
            MethodNode m = p.method(name, "(IZ)Z");
            p.replace(m, p.single(m, plain)[0], p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)));
            p.replace(m, p.single(m, offset)[0], p.floor(new VarInsnNode(ALOAD, 0), copy(thisWorld)));
        }
    }
}
