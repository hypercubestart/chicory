package com.dylibso.chicory.wasm.types;

import com.dylibso.chicory.wasm.MalformedException;
import com.dylibso.chicory.wasm.WasmModule;
import java.util.List;
import java.util.stream.Stream;

/**
 * The possible WASM value types.
 */
public final class ValueType {
    private static final int NULL_TYPEIDX = 0;
    private static final long OPCODE_MASK = 0xFFFFFFFFL;
    private static final long TYPEIDX_SHIFT = 32;

    public static ValueType BOT = new ValueType(ID.BOT);
    public static ValueType F64 = new ValueType(ID.F64);

    public static ValueType F32 = new ValueType(ID.F32);
    public static ValueType I64 = new ValueType(ID.I64);

    public static ValueType I32 = new ValueType(ID.I32);

    public static ValueType V128 = new ValueType(ID.V128);
    public static ValueType FuncRef = new ValueType(ID.FuncRef);
    public static ValueType ExternRef = new ValueType(ID.ExternRef);
    public static ValueType ExnRef = new ValueType(ID.ExnRef);

    private final long id;

    public ValueType(int opcode) {
        this(opcode, NULL_TYPEIDX);
    }

    public ValueType(int opcode, int typeIdx) {
        // Conveniently, all value types we want to represent can fit inside a Java long.
        // We store the typeIdx (of reference types) in the upper 4 bytes and the opcode in the
        // lower 4 bytes.
        if (opcode == ID.FuncRef) {
            typeIdx = TypeIdxCode.FUNC.code();
            opcode = ID.RefNull;
        } else if (opcode == ID.ExternRef) {
            typeIdx = TypeIdxCode.EXTERN.code();
            opcode = ID.RefNull;
        }

        long id = ((long) typeIdx) << TYPEIDX_SHIFT | (opcode & OPCODE_MASK);
        this.id = id;
    }

    private ValueType(long id) {
        this.id = id;
    }

    /**
     * @return id of this ValueType
     */
    public long id() {
        return this.id;
    }

    public int opcode() {
        return (int) (id & OPCODE_MASK);
    }

    /**
     * @return the size of this type in memory
     * @throws IllegalStateException if the type cannot be stored in memory
     */
    public int size() {
        switch (this.opcode()) {
            case ID.F64:
            case ID.I64:
                return 8;
            case ID.F32:
            case ID.I32:
                return 4;
            case ID.V128:
                return 16;
            default:
                throw new IllegalStateException("Type does not have size");
        }
    }

    /**
     * @return {@code true} if the type is a numeric type, or {@code false} otherwise
     */
    public boolean isNumeric() {
        switch (this.opcode()) {
            case ID.F64:
            case ID.F32:
            case ID.I64:
            case ID.I32:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return {@code true} if the type is an integer type, or {@code false} otherwise
     */
    public boolean isInteger() {
        switch (this.opcode()) {
            case ID.I64:
            case ID.I32:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return {@code true} if the type is a floating-point type, or {@code false} otherwise
     */
    public boolean isFloatingPoint() {
        switch (this.opcode()) {
            case ID.F64:
            case ID.F32:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return {@code true} if the type is a reference type, or {@code false} otherwise
     */
    public boolean isReference() {
        switch (this.opcode()) {
            case ID.Ref:
            case ID.ExnRef:
            case ID.RefNull:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return {@code true} if the given type ID is a valid value type ID, or {@code false} if it is not
     */
    public static boolean isValid(long typeId) {
        ValueType res = forId(typeId);
        switch (res.opcode()) {
            case ID.RefNull:
            case ID.Ref:
            case ID.ExnRef:
            case ID.V128:
            case ID.I32:
            case ID.I64:
            case ID.F32:
            case ID.F64:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return the {@code ValueType} for the given ID value
     * @throws IllegalArgumentException if the ID value does not correspond to a valid value type
     */
    public static ValueType forId(long id) {
        return new ValueType(id);
    }

    /**
     * @return the reference-typed {@code ValueType} for the given ID value
     * @throws IllegalArgumentException if the ID value does not correspond to a valid reference type
     */
    public static ValueType refTypeForId(long id) {
        ValueType res = forId(id);
        switch (res.opcode()) {
            case ID.RefNull:
            case ID.Ref:
            case ID.ExnRef:
                return res;
            default:
                throw new MalformedException("malformed reference type " + id);
        }
    }

    public static int sizeOf(List<ValueType> args) {
        int total = 0;
        for (var a : args) {
            if (a.opcode() == ID.V128) {
                total += 2;
            } else {
                total += 1;
            }
        }
        return total;
    }

    private static boolean eq_def(WasmModule context, int typeIdx1, int typeIdx2) {
        var funcType1 = context.typeSection().getType(typeIdx1);
        var funcType2 = context.typeSection().getType(typeIdx2);

        // substitute any type indexes when comparing equality
        if (funcType1.params().size() != funcType2.params().size()
                || funcType1.returns().size() != funcType2.returns().size()) {
            return false;
        }

        ValueType[] types1 =
                Stream.concat(funcType1.params().stream(), funcType1.returns().stream())
                        .toArray(ValueType[]::new);
        ValueType[] types2 =
                Stream.concat(funcType2.params().stream(), funcType2.returns().stream())
                        .toArray(ValueType[]::new);

        for (int i = 0; i < types1.length; i++) {
            var type1 = types1[i];
            var type2 = types2[i];

            if (type1.isReference()
                    && type2.isReference()
                    && type1.typeIdx() >= 0
                    && type2.typeIdx() >= 0) {
                // both are defined function types, substitute again!
                if (!eq_def(context, type1.typeIdx(), type2.typeIdx())) {
                    return false;
                }
            } else if (!type1.equals(type2)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matches_null(boolean null1, boolean null2) {
        return null1 == null2 || null2;
    }

    private static boolean matches_heap(WasmModule context, int heapType1, int heapType2) {
        if (heapType1 >= 0 && heapType2 >= 0) {
            return eq_def(context, heapType1, heapType2);
        } else if (heapType1 >= 0 && heapType2 == TypeIdxCode.FUNC.code()) {
            return true;
        } else if (heapType1 == TypeIdxCode.BOT.code()) {
            return true;
        } else {
            return heapType1 == heapType2;
        }
    }

    public static boolean matches_ref(WasmModule context, ValueType t1, ValueType t2) {
        return matches_heap(context, t1.typeIdx(), t2.typeIdx())
                && matches_null(t1.isNullable(), t2.isNullable());
    }

    public static boolean matches(WasmModule context, ValueType t1, ValueType t2) {
        if (t1.isReference() && t2.isReference()) {
            return matches_ref(context, t1, t2);
        } else if (t1.opcode() == ID.BOT) {
            return true;
        } else {
            return t1.id() == t2.id();
        }
    }

    public boolean isNullable() {
        switch (opcode()) {
            case ID.Ref:
                return false;
            case ID.RefNull:
                return true;
            default:
                throw new IllegalArgumentException(
                        "got non-reference type to isNullable(): " + this);
        }
    }

    public int typeIdx() {
        return (int) (id >>> TYPEIDX_SHIFT);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ValueType)) {
            return false;
        }
        ValueType that = (ValueType) other;
        return this.id == that.id;
    }

    @Override
    public String toString() {
        switch (opcode()) {
            case ID.Ref:
            case ID.RefNull:
                return ID.toName(opcode()) + "[" + typeIdx() + "]";
            default:
                return ID.toName(opcode());
        }
    }

    public enum TypeIdxCode {
        // heap type
        EXTERN(-17), // 0x6F
        FUNC(-16), // 0x70
        BOT(-1);

        private final int code;

        TypeIdxCode(int code) {
            this.code = code;
        }

        public int code() {
            return this.code;
        }
    }

    /**
     * A separate holder class for ID constants.
     * This is necessary because enum constants are initialized before normal fields, so any reference to an ID constant
     * in the same class would be considered an invalid forward reference.
     */
    public static final class ID {
        private ID() {}

        public static final int BOT = -1;
        public static final int RefNull = 0x63;
        public static final int Ref = 0x64;
        public static final int ExternRef = 0x6f;
        // From the Exception Handling proposal
        static final int ExnRef = 0x69; // -0x17
        public static final int FuncRef = 0x70;
        public static final int V128 = 0x7b;
        public static final int F64 = 0x7c;
        public static final int F32 = 0x7d;
        public static final int I64 = 0x7e;
        public static final int I32 = 0x7f;

        public static String toName(int opcode) {
            switch (opcode) {
                case BOT:
                    return "Bot";
                case RefNull:
                    return "RefNull";
                case Ref:
                    return "Ref";
                case ExnRef:
                    return "ExnRef";
                case V128:
                    return "V128";
                case F64:
                    return "F64";
                case F32:
                    return "F32";
                case I64:
                    return "I64";
                case I32:
                    return "I32";
            }

            throw new IllegalArgumentException("got invalid opcode in ValueType.toName: " + opcode);
        }

        public static boolean isValidOpcode(int opcode) {
            return (opcode == RefNull
                    || opcode == Ref
                    || opcode == ExternRef
                    || opcode == FuncRef
                    || opcode == ExnRef
                    || opcode == V128
                    || opcode == F64
                    || opcode == F32
                    || opcode == I64
                    || opcode == I32);
        }
    }
}
