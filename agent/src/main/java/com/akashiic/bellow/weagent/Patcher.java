package com.akashiic.bellow.weagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Small, strict editing layer over an ASM tree. Every edit names the exact instruction sequence it expects and how
 * many times it occurs; anything else throws, and the caller keeps the original class.
 * <p>
 * Edits only replace a constant or insert straight-line code next to a matched instruction, never across a branch
 * target, so the existing stack map frames stay valid and only the maximum stack size is recomputed.
 */
final class Patcher implements Opcodes {

    /** Static helper added to every patched class: the Bellow floor of a WorldEdit world (0 when unknown). */
    static final String HELPER = "akashicbellow$minY";
    static final String WORLD = "com/sk89q/worldedit/world/World";
    static final String HELPER_DESC = "(L" + WORLD + ";)I";
    static final String PROPERTY_PREFIX = "akashicbellow.floor.";

    /** One instruction of a pattern. */
    interface Insn {

        boolean test(AbstractInsnNode n);
    }

    private final ClassReader reader;
    final ClassNode cls;
    private int edits;

    Patcher(byte[] bytes) {
        this.reader = new ClassReader(bytes);
        this.cls = new ClassNode();
        this.reader.accept(this.cls, 0);
        for (MethodNode m : this.cls.methods) {
            if (HELPER.equals(m.name)) throw new IllegalStateException("already patched");
        }
    }

    int edits() {
        return this.edits;
    }

    byte[] finish() {
        if (this.edits == 0) throw new IllegalStateException("no edits");
        this.cls.methods.add(helper());
        ClassWriter writer = new ClassWriter(this.reader, ClassWriter.COMPUTE_MAXS);
        this.cls.accept(writer);
        return writer.toByteArray();
    }

    // ------------------------------------------------------------------ lookup

    MethodNode method(String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : this.cls.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                if (found != null) throw new IllegalStateException("duplicate method " + name + desc);
                found = m;
            }
        }
        if (found == null) throw new IllegalStateException("missing method " + name + desc);
        return found;
    }

    /**
     * Every occurrence of {@code pattern} in {@code m}, as the matched instructions. Line numbers and frames are
     * skipped between pattern elements; a branch or exception-handler target inside a match rejects it.
     */
    List<AbstractInsnNode[]> find(MethodNode m, int expected, Insn... pattern) {
        List<AbstractInsnNode[]> out = new ArrayList<>();
        Set<LabelNode> targets = targets(m);
        for (AbstractInsnNode start = m.instructions.getFirst(); start != null; start = start.getNext()) {
            if (start.getOpcode() < 0 || !pattern[0].test(start)) continue;
            AbstractInsnNode[] hit = new AbstractInsnNode[pattern.length];
            hit[0] = start;
            AbstractInsnNode n = start;
            int i = 1;
            for (; i < pattern.length; i++) {
                n = next(n, targets);
                if (n == null || !pattern[i].test(n)) break;
                hit[i] = n;
            }
            if (i == pattern.length) out.add(hit);
        }
        if (out.size() != expected) {
            throw new IllegalStateException(
                "expected " + expected + " sites in " + m.name + m.desc + ", found " + out.size());
        }
        return out;
    }

    AbstractInsnNode[] single(MethodNode m, Insn... pattern) {
        return find(m, 1, pattern).get(0);
    }

    /** Next real instruction, or null if a jump target or the end comes first. */
    private static AbstractInsnNode next(AbstractInsnNode n, Set<LabelNode> targets) {
        for (n = n.getNext(); n != null; n = n.getNext()) {
            if (n instanceof LabelNode) {
                if (targets.contains(n)) return null;
                continue;
            }
            if (n instanceof LineNumberNode || n instanceof FrameNode) continue;
            return n;
        }
        return null;
    }

    /** Labels that control can reach other than by falling through: jump, switch and handler targets. */
    private static Set<LabelNode> targets(MethodNode m) {
        Set<LabelNode> out = new HashSet<>();
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof JumpInsnNode) out.add(((JumpInsnNode) n).label);
            else if (n instanceof TableSwitchInsnNode) {
                out.add(((TableSwitchInsnNode) n).dflt);
                out.addAll(((TableSwitchInsnNode) n).labels);
            } else if (n instanceof LookupSwitchInsnNode) {
                out.add(((LookupSwitchInsnNode) n).dflt);
                out.addAll(((LookupSwitchInsnNode) n).labels);
            }
        }
        for (TryCatchBlockNode t : m.tryCatchBlocks) {
            out.add(t.start);
            out.add(t.end);
            out.add(t.handler);
        }
        return out;
    }

    /** A copy of the first {@code GETFIELD name:desc} in the class (the field may be declared by a superclass). */
    FieldInsnNode getField(String name, String desc) {
        for (MethodNode m : this.cls.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() == GETFIELD) {
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (f.name.equals(name) && f.desc.equals(desc)) return new FieldInsnNode(GETFIELD, f.owner, name, desc);
                }
            }
        }
        throw new IllegalStateException("no GETFIELD " + name + ":" + desc);
    }

    // ------------------------------------------------------------------- edits

    /** The world's floor: {@code worldLoad} (leaving a World on the stack) followed by the helper call. */
    InsnList floor(AbstractInsnNode... worldLoad) {
        InsnList list = new InsnList();
        for (AbstractInsnNode n : worldLoad) list.add(n);
        list.add(new MethodInsnNode(INVOKESTATIC, this.cls.name, HELPER, HELPER_DESC, false));
        return list;
    }

    void replace(MethodNode m, AbstractInsnNode old, InsnList with) {
        m.instructions.insertBefore(old, with);
        m.instructions.remove(old);
        this.edits++;
    }

    void insertAfter(MethodNode m, AbstractInsnNode at, InsnList list) {
        m.instructions.insert(at, list);
        this.edits++;
    }

    void insertBefore(MethodNode m, AbstractInsnNode at, InsnList list) {
        m.instructions.insertBefore(at, list);
        this.edits++;
    }

    /** {@code x <op> 0} becomes {@code x <op> floor}: the floor is pushed after {@code x} and the jump compares two ints. */
    void compareWithFloor(MethodNode m, AbstractInsnNode value, JumpInsnNode jump, InsnList floor) {
        int op;
        switch (jump.getOpcode()) {
            case IFLT:
                op = IF_ICMPLT;
                break;
            case IFLE:
                op = IF_ICMPLE;
                break;
            case IFGE:
                op = IF_ICMPGE;
                break;
            case IFGT:
                op = IF_ICMPGT;
                break;
            default:
                throw new IllegalStateException("unexpected jump " + jump.getOpcode());
        }
        insertAfter(m, value, floor);
        jump.setOpcode(op);
    }

    static InsnList list(AbstractInsnNode... nodes) {
        InsnList list = new InsnList();
        for (AbstractInsnNode n : nodes) list.add(n);
        return list;
    }

    static InsnList concat(InsnList a, AbstractInsnNode... tail) {
        for (AbstractInsnNode n : tail) a.add(n);
        return a;
    }

    static AbstractInsnNode copy(AbstractInsnNode n) {
        return n.clone(Collections.<LabelNode, LabelNode>emptyMap());
    }

    // ---------------------------------------------------------------- patterns

    static Insn op(final int opcode) {
        return n -> n.getOpcode() == opcode;
    }

    static Insn anyOf(final int... opcodes) {
        return n -> {
            for (int o : opcodes) if (n.getOpcode() == o) return true;
            return false;
        };
    }

    static Insn var(final int opcode, final int index) {
        return n -> n.getOpcode() == opcode && ((VarInsnNode) n).var == index;
    }

    static Insn field(final int opcode, final String... names) {
        return n -> {
            if (n.getOpcode() != opcode) return false;
            for (String s : names) if (((FieldInsnNode) n).name.equals(s)) return true;
            return false;
        };
    }

    /** A method call; {@code owner} null matches any owner. */
    static Insn call(final int opcode, final String owner, final String name, final String desc) {
        return n -> {
            if (n.getOpcode() != opcode) return false;
            MethodInsnNode c = (MethodInsnNode) n;
            return (owner == null || c.owner.equals(owner)) && c.name.equals(name) && c.desc.equals(desc);
        };
    }

    static Insn type(final int opcode, final String desc) {
        return n -> n.getOpcode() == opcode && ((TypeInsnNode) n).desc.equals(desc);
    }

    static Insn ldc(final Object value) {
        return n -> n.getOpcode() == LDC && value.equals(((LdcInsnNode) n).cst);
    }

    static Insn push(final int value) {
        return n -> (n.getOpcode() == BIPUSH || n.getOpcode() == SIPUSH) && ((IntInsnNode) n).operand == value;
    }

    static final Insn MATH_MAX = call(INVOKESTATIC, "java/lang/Math", "max", "(II)I");
    static final Insn GET_BLOCK_Y = n -> n.getOpcode() == INVOKEVIRTUAL && ((MethodInsnNode) n).name.equals("getBlockY")
        && ((MethodInsnNode) n).desc.equals("()I");

    // ------------------------------------------------------------------ helper

    /**
     * <pre>
     * private static synthetic int akashicbellow$minY(World world) {
     *     if (world == null) return 0;
     *     String name = world.getName();
     *     if (name == null) return 0;
     *     return Math.min(0, Integer.getInteger("akashicbellow.floor." + name, 0));
     * }
     * </pre>
     */
    private static MethodNode helper() {
        MethodNode m = new MethodNode(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, HELPER, HELPER_DESC, null, null);
        InsnList c = m.instructions;
        LabelNode named = new LabelNode(), build = new LabelNode();
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new JumpInsnNode(IFNONNULL, named));
        c.add(new InsnNode(ICONST_0));
        c.add(new InsnNode(IRETURN));
        c.add(named);
        c.add(new FrameNode(F_SAME, 0, null, 0, null));
        c.add(new VarInsnNode(ALOAD, 0));
        c.add(new MethodInsnNode(INVOKEINTERFACE, WORLD, "getName", "()Ljava/lang/String;", true));
        c.add(new VarInsnNode(ASTORE, 1));
        c.add(new VarInsnNode(ALOAD, 1));
        c.add(new JumpInsnNode(IFNONNULL, build));
        c.add(new InsnNode(ICONST_0));
        c.add(new InsnNode(IRETURN));
        c.add(build);
        c.add(new FrameNode(F_APPEND, 1, new Object[] { "java/lang/String" }, 0, null));
        c.add(new InsnNode(ICONST_0));
        c.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
        c.add(new InsnNode(DUP));
        c.add(new LdcInsnNode(PROPERTY_PREFIX));
        c.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false));
        c.add(new VarInsnNode(ALOAD, 1));
        c.add(
            new MethodInsnNode(
                INVOKEVIRTUAL,
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                false));
        c.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        c.add(new InsnNode(ICONST_0));
        c.add(
            new MethodInsnNode(
                INVOKESTATIC,
                "java/lang/Integer",
                "getInteger",
                "(Ljava/lang/String;I)Ljava/lang/Integer;",
                false));
        c.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
        c.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
        c.add(new InsnNode(IRETURN));
        return m;
    }
}
