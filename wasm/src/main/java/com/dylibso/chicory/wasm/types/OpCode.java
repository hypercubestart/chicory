package com.dylibso.chicory.wasm.types;

import static com.dylibso.chicory.wasm.types.WasmEncoding.BYTE;
import static com.dylibso.chicory.wasm.types.WasmEncoding.FLOAT32;
import static com.dylibso.chicory.wasm.types.WasmEncoding.FLOAT64;
import static com.dylibso.chicory.wasm.types.WasmEncoding.V128;
import static com.dylibso.chicory.wasm.types.WasmEncoding.VARSINT32;
import static com.dylibso.chicory.wasm.types.WasmEncoding.VARSINT64;
import static com.dylibso.chicory.wasm.types.WasmEncoding.VARUINT;
import static com.dylibso.chicory.wasm.types.WasmEncoding.VEC_VARUINT;

import java.util.List;

public enum OpCode {
    UNREACHABLE(0x00),
    NOP(0x01),
    BLOCK(0x02, List.of(VARUINT)),
    LOOP(0x03, List.of(VARUINT)),
    IF(0x04, List.of(VARUINT)),
    ELSE(0x05),
    END(0x0B),
    BR(0x0C, List.of(VARUINT)),
    BR_IF(0x0D, List.of(VARUINT)),
    BR_TABLE(0x0E, List.of(VEC_VARUINT, VARUINT)),
    RETURN(0x0F),
    CALL(0x10, List.of(VARUINT)),
    CALL_INDIRECT(0x11, List.of(VARUINT, VARUINT)),
    RETURN_CALL(0x12, List.of(VARUINT)),
    RETURN_CALL_INDIRECT(0x13, List.of(VARUINT, VARUINT)),
    CALL_REF(0x14, List.of(VARUINT)),
    DROP(0x1A),
    SELECT(0x1B),
    SELECT_T(0x1C, List.of(VEC_VARUINT)),
    LOCAL_GET(0x20, List.of(VARUINT)),
    LOCAL_SET(0x21, List.of(VARUINT)),
    LOCAL_TEE(0x22, List.of(VARUINT)),
    GLOBAL_GET(0x23, List.of(VARUINT)),
    GLOBAL_SET(0x24, List.of(VARUINT)),
    TABLE_GET(0x25, List.of(VARUINT)),
    TABLE_SET(0x26, List.of(VARUINT)),
    I32_LOAD(0x28, List.of(VARUINT, VARUINT)),
    I64_LOAD(0x29, List.of(VARUINT, VARUINT)),
    F32_LOAD(0x2A, List.of(VARUINT, VARUINT)),
    F64_LOAD(0x2B, List.of(VARUINT, VARUINT)),
    I32_LOAD8_S(0x2C, List.of(VARUINT, VARUINT)),
    I32_LOAD8_U(0x2D, List.of(VARUINT, VARUINT)),
    I32_LOAD16_S(0x2E, List.of(VARUINT, VARUINT)),
    I32_LOAD16_U(0x2F, List.of(VARUINT, VARUINT)),
    I64_LOAD8_S(0x30, List.of(VARUINT, VARUINT)),
    I64_LOAD8_U(0x31, List.of(VARUINT, VARUINT)),
    I64_LOAD16_S(0x32, List.of(VARUINT, VARUINT)),
    I64_LOAD16_U(0x33, List.of(VARUINT, VARUINT)),
    I64_LOAD32_S(0x34, List.of(VARUINT, VARUINT)),
    I64_LOAD32_U(0x35, List.of(VARUINT, VARUINT)),
    I32_STORE(0x36, List.of(VARUINT, VARUINT)),
    I64_STORE(0x37, List.of(VARUINT, VARUINT)),
    F32_STORE(0x38, List.of(VARUINT, VARUINT)),
    F64_STORE(0x39, List.of(VARUINT, VARUINT)),
    I32_STORE8(0x3A, List.of(VARUINT, VARUINT)),
    I32_STORE16(0x3B, List.of(VARUINT, VARUINT)),
    I64_STORE8(0x3C, List.of(VARUINT, VARUINT)),
    I64_STORE16(0x3D, List.of(VARUINT, VARUINT)),
    I64_STORE32(0x3E, List.of(VARUINT, VARUINT)),
    MEMORY_SIZE(0x3F),
    MEMORY_GROW(0x40),
    I32_CONST(0x41, List.of(VARSINT32)),
    I64_CONST(0x42, List.of(VARSINT64)),
    F32_CONST(0x43, List.of(FLOAT32)),
    F64_CONST(0x44, List.of(FLOAT64)),
    I32_EQZ(0x45),
    I32_EQ(0x46),
    I32_NE(0x47),
    I32_LT_S(0x48),
    I32_LT_U(0x49),
    I32_GT_S(0x4A),
    I32_GT_U(0x4B),
    I32_LE_S(0x4C),
    I32_LE_U(0x4D),
    I32_GE_S(0x4E),
    I32_GE_U(0x4F),
    I64_EQZ(0x50),
    I64_EQ(0x51),
    I64_NE(0x52),
    I64_LT_S(0x53),
    I64_LT_U(0x54),
    I64_GT_S(0x55),
    I64_GT_U(0x56),
    I64_LE_S(0x57),
    I64_LE_U(0x58),
    I64_GE_S(0x59),
    I64_GE_U(0x5A),
    F32_EQ(0x5B),
    F32_NE(0x5C),
    F32_LT(0x5D),
    F32_GT(0x5E),
    F32_LE(0x5F),
    F32_GE(0x60),
    F64_EQ(0x61),
    F64_NE(0x62),
    F64_LT(0x63),
    F64_GT(0x64),
    F64_LE(0x65),
    F64_GE(0x66),
    I32_CLZ(0x67),
    I32_CTZ(0x68),
    I32_POPCNT(0x69),
    I32_ADD(0x6A),
    I32_SUB(0x6B),
    I32_MUL(0x6C),
    I32_DIV_S(0x6D),
    I32_DIV_U(0x6E),
    I32_REM_S(0x6F),
    I32_REM_U(0x70),
    I32_AND(0x71),
    I32_OR(0x72),
    I32_XOR(0x73),
    I32_SHL(0x74),
    I32_SHR_S(0x75),
    I32_SHR_U(0x76),
    I32_ROTL(0x77),
    I32_ROTR(0x78),
    I64_CLZ(0x79),
    I64_CTZ(0x7A),
    I64_POPCNT(0x7B),
    I64_ADD(0x7C),
    I64_SUB(0x7D),
    I64_MUL(0x7E),
    I64_DIV_S(0x7F),
    I64_DIV_U(0x80),
    I64_REM_S(0x81),
    I64_REM_U(0x82),
    I64_AND(0x83),
    I64_OR(0x84),
    I64_XOR(0x85),
    I64_SHL(0x86),
    I64_SHR_S(0x87),
    I64_SHR_U(0x88),
    I64_ROTL(0x89),
    I64_ROTR(0x8A),
    F32_ABS(0x8B),
    F32_NEG(0x8C),
    F32_CEIL(0x8D),
    F32_FLOOR(0x8E),
    F32_TRUNC(0x8F),
    F32_NEAREST(0x90),
    F32_SQRT(0x91),
    F32_ADD(0x92),
    F32_SUB(0x93),
    F32_MUL(0x94),
    F32_DIV(0x95),
    F32_MIN(0x96),
    F32_MAX(0x97),
    F32_COPYSIGN(0x98),
    F64_ABS(0x99),
    F64_NEG(0x9A),
    F64_CEIL(0x9B),
    F64_FLOOR(0x9C),
    F64_TRUNC(0x9D),
    F64_NEAREST(0x9E),
    F64_SQRT(0x9F),
    F64_ADD(0xA0),
    F64_SUB(0xA1),
    F64_MUL(0xA2),
    F64_DIV(0xA3),
    F64_MIN(0xA4),
    F64_MAX(0xA5),
    F64_COPYSIGN(0xA6),
    I32_WRAP_I64(0xA7),
    I32_TRUNC_F32_S(0xA8),
    I32_TRUNC_F32_U(0xA9),
    I32_TRUNC_F64_S(0xAA),
    I32_TRUNC_F64_U(0xAB),
    I64_EXTEND_I32_S(0xAC),
    I64_EXTEND_I32_U(0xAD),
    I64_TRUNC_F32_S(0xAE),
    I64_TRUNC_F32_U(0xAF),
    I64_TRUNC_F64_S(0xB0),
    I64_TRUNC_F64_U(0xB1),
    F32_CONVERT_I32_S(0xB2),
    F32_CONVERT_I32_U(0xB3),
    F32_CONVERT_I64_S(0xB4),
    F32_CONVERT_I64_U(0xB5),
    F32_DEMOTE_F64(0xB6),
    F64_CONVERT_I32_S(0xB7),
    F64_CONVERT_I32_U(0xB8),
    F64_CONVERT_I64_S(0xB9),
    F64_CONVERT_I64_U(0xBA),
    F64_PROMOTE_F32(0xBB),
    I32_REINTERPRET_F32(0xBC),
    I64_REINTERPRET_F64(0xBD),
    F32_REINTERPRET_I32(0xBE),
    F64_REINTERPRET_I64(0xBF),
    I32_EXTEND_8_S(0xC0),
    I32_EXTEND_16_S(0xC1),
    I64_EXTEND_8_S(0xC2),
    I64_EXTEND_16_S(0xC3),
    I64_EXTEND_32_S(0xC4),
    REF_NULL(0xD0, List.of(VARUINT)),
    REF_IS_NULL(0xD1),
    REF_FUNC(0xD2, List.of(VARUINT)),
    I32_TRUNC_SAT_F32_S(0xFC00),
    I32_TRUNC_SAT_F32_U(0xFC01),
    I32_TRUNC_SAT_F64_S(0xFC02),
    I32_TRUNC_SAT_F64_U(0xFC03),
    I64_TRUNC_SAT_F32_S(0xFC04),
    I64_TRUNC_SAT_F32_U(0xFC05),
    I64_TRUNC_SAT_F64_S(0xFC06),
    I64_TRUNC_SAT_F64_U(0xFC07),
    MEMORY_INIT(0xFC08, List.of(VARUINT, VARUINT)),
    DATA_DROP(0xFC09, List.of(VARUINT)),
    MEMORY_COPY(0xFC0A, List.of(VARUINT, VARUINT)),
    MEMORY_FILL(0xFC0B, List.of(VARUINT)),
    TABLE_INIT(0xFC0C, List.of(VARUINT, VARUINT)),
    ELEM_DROP(0xFC0D, List.of(VARUINT)),
    TABLE_COPY(0xFC0E, List.of(VARUINT, VARUINT)),
    TABLE_GROW(0xFC0F, List.of(VARUINT)),
    TABLE_SIZE(0xFC10, List.of(VARUINT)),
    TABLE_FILL(0xFC11, List.of(VARUINT)),
    V128_LOAD(0xFD00, List.of(VARUINT, VARUINT)),
    V128_LOAD8x8_S(0xFD01, List.of(VARUINT, VARUINT)),
    V128_LOAD8x8_U(0xFD02, List.of(VARUINT, VARUINT)),
    V128_LOAD16x4_S(0xFD03, List.of(VARUINT, VARUINT)),
    V128_LOAD16x4_U(0xFD04, List.of(VARUINT, VARUINT)),
    V128_LOAD32x2_S(0xFD05, List.of(VARUINT, VARUINT)),
    V128_LOAD32x2_U(0xFD06, List.of(VARUINT, VARUINT)),
    V128_LOAD8_SPLAT(0xFD07, List.of(VARUINT, VARUINT)),
    V128_LOAD16_SPLAT(0xFD08, List.of(VARUINT, VARUINT)),
    V128_LOAD32_SPLAT(0xFD09, List.of(VARUINT, VARUINT)),
    V128_LOAD64_SPLAT(0xFD0A, List.of(VARUINT, VARUINT)),
    V128_STORE(0xFD0B, List.of(VARUINT, VARUINT)),
    V128_CONST(0xFD0C, List.of(V128)),
    I8x16_SHUFFLE(0xFD0D, List.of(V128)),
    I8x16_SWIZZLE(0xFD0E),
    I8x16_SPLAT(0xFD0F),
    I16x8_SPLAT(0xFD10),
    I32x4_SPLAT(0xFD11),
    I64x2_SPLAT(0xFD12),
    F32x4_SPLAT(0xFD13),
    F64x2_SPLAT(0xFD14),
    I8x16_EXTRACT_LANE_S(0xFD15, List.of(BYTE)),
    I8x16_EXTRACT_LANE_U(0xFD16, List.of(BYTE)),
    I8x16_REPLACE_LANE(0xFD17, List.of(BYTE)),
    I16x8_EXTRACT_LANE_S(0xFD18, List.of(BYTE)),
    I16x8_EXTRACT_LANE_U(0xFD19, List.of(BYTE)),
    I16x8_REPLACE_LANE(0xFD1A, List.of(BYTE)),
    I32x4_EXTRACT_LANE(0xFD1B, List.of(BYTE)),
    I32x4_REPLACE_LANE(0xFD1C, List.of(BYTE)),
    I64x2_EXTRACT_LANE(0xFD1D, List.of(BYTE)),
    I64x2_REPLACE_LANE(0xFD1E, List.of(BYTE)),
    F32x4_EXTRACT_LANE(0xFD1F, List.of(BYTE)),
    F32x4_REPLACE_LANE(0xFD20, List.of(BYTE)),
    F64x2_EXTRACT_LANE(0xFD21, List.of(BYTE)),
    F64x2_REPLACE_LANE(0xFD22, List.of(BYTE)),
    I8x16_EQ(0xFD23),
    I16x8_EQ(0xFD2D),
    I32x4_EQ(0xFD37),
    F32x4_EQ(0xFD41),
    F64x2_EQ(0xFD47),
    V128_NOT(0xFD4D),
    V128_AND(0xFD4E),
    V128_ANDNOT(0xFD4F),
    V128_OR(0xFD50),
    V128_XOR(0xFD51),
    V128_BITSELECT(0xFD52),
    V128_ANY_TRUE(0xFD53),
    V128_LOAD8_LANE(0xFD54, List.of(VARUINT, VARUINT, VARUINT)),
    V128_LOAD16_LANE(0xFD55, List.of(VARUINT, VARUINT, VARUINT)),
    V128_LOAD32_LANE(0xFD56, List.of(VARUINT, VARUINT, VARUINT)),
    V128_LOAD64_LANE(0xFD57, List.of(VARUINT, VARUINT, VARUINT)),
    V128_STORE8_LANE(0xFD58, List.of(VARUINT, VARUINT, VARUINT)),
    V128_STORE16_LANE(0xFD59, List.of(VARUINT, VARUINT, VARUINT)),
    V128_STORE32_LANE(0xFD5A, List.of(VARUINT, VARUINT, VARUINT)),
    V128_STORE64_LANE(0xFD5B, List.of(VARUINT, VARUINT, VARUINT)),
    V128_LOAD32_ZERO(0xFD5C, List.of(VARUINT, VARUINT)),
    V128_LOAD64_ZERO(0xFD5D, List.of(VARUINT, VARUINT)),
    F32x4_DEMOTE_LOW_F64x2_ZERO(0xFD5E),
    F64x2_PROMOTE_LOW_F32x4(0xFD5F),
    I8x16_ALL_TRUE(0xFD63),
    I8x16_BITMASK(0xFD64),
    I8x16_NARROW_I16x8_S(0xFD65),
    I8x16_NARROW_I16x8_U(0xFD66),
    I8x16_SHL(0xFD6B),
    I8x16_SHR_S(0xFD6C),
    I8x16_SHR_U(0xFD6D),
    I8x16_ADD(0xFD6E),
    I8x16_ADD_SAT_S(0xFD6F),
    I8x16_SUB(0xFD71),
    I8x16_SUB_SAT_U(0xFD73),
    I16x8_ALL_TRUE(0xFD83),
    I16x8_BITMASK(0xFD84),
    I16x8_NARROW_I32x4_S(0xFD85),
    I16x8_NARROW_I32x4_U(0xFD86),
    I16x8_EXTEND_LOW_I8x16_S(0xFD87),
    I16x8_EXTEND_LOW_I8x16_U(0xFD89),
    I16x8_ADD(0xFD8E),
    I16x8_ADD_SAT_S(0xFD8F),
    I16x8_SHL(0xFD8B),
    I16x8_SHR_S(0xFD8C),
    I16x8_SHR_U(0xFD8D),
    I16x8_SUB(0xFD91),
    I16x8_SUB_SAT_U(0xFD93),
    I16x8_MUL(0xFD95),
    I32x4_ALL_TRUE(0xFDA3),
    I32x4_BITMASK(0xFDA4),
    I32x4_EXTEND_LOW_I16x8_S(0xFDA7),
    I32x4_EXTEND_LOW_I16x8_U(0xFDA9),
    I32x4_SHL(0xFDAB),
    I32x4_SHR_S(0xFDAC),
    I32x4_SHR_U(0xFDAD),
    I32x4_ADD(0xFDAE),
    I32x4_SUB(0xFDB1),
    I32x4_MUL(0xFDB5),
    I64x2_ALL_TRUE(0xFDC3),
    I64x2_BITMASK(0xFDC4),
    I64x2_SHL(0xFDCB),
    I64x2_SHR_S(0xFDCC),
    I64x2_SHR_U(0xFDCD),
    I64x2_ADD(0xFDCE),
    I64x2_SUB(0xFDD1),
    I64x2_MUL(0xFDD5),
    F32x4_MUL(0xFDE6),
    F32x4_ABS(0xFDE0),
    F32x4_DIV(0xFDE7),
    F32x4_MIN(0xFDE8),
    F64x2_ADD(0xFDF0),
    F64x2_SUB(0xFDF1),
    F64x2_MUL(0xFDF2),
    I32x4_TRUNC_SAT_F32X4_S(0xFDF8),
    F32x4_CONVERT_I32x4_S(0xFDFA),
    F32x4_CONVERT_I32x4_U(0xFDFB),
    F64x2_CONVERT_LOW_I32x4_S(0xFDFE),
    F64x2_CONVERT_LOW_I32x4_U(0xFDFF),
    ;

    private static final int OP_CODES_SIZE = 0xFF00;

    // trick: the enum constructor cannot access its own static fields
    // but can access another class
    private static final class OpCodes {
        private OpCodes() {}

        private static final OpCode[] byOpCode = new OpCode[OP_CODES_SIZE];
        private static final List<WasmEncoding>[] signatures = new List[OP_CODES_SIZE];
    }

    private final int opcode;

    OpCode(int opcode) {
        this(opcode, List.of());
    }

    OpCode(int opcode, List<WasmEncoding> signature) {
        this.opcode = opcode;
        OpCodes.byOpCode[opcode] = this;
        OpCodes.signatures[opcode] = signature;
    }

    public int opcode() {
        return opcode;
    }

    public static OpCode byOpCode(int opcode) {
        return OpCodes.byOpCode[opcode];
    }

    public static List<WasmEncoding> signature(OpCode opcode) {
        return OpCodes.signatures[opcode.opcode()];
    }
}
