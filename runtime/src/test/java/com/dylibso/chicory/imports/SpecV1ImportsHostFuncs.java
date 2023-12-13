package com.dylibso.chicory.imports;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.HostGlobal;
import com.dylibso.chicory.runtime.HostImports;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.types.Value;
import com.dylibso.chicory.wasm.types.ValueType;
import java.util.Arrays;
import java.util.List;

public class SpecV1ImportsHostFuncs {

    private static HostImports base() {
        var printI32 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i32",
                        List.of(ValueType.I32),
                        List.of());
        var printI32_1 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i32_1",
                        List.of(ValueType.I32),
                        List.of());
        var printI32_2 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i32_2",
                        List.of(ValueType.I32),
                        List.of());
        var printF32 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_f32",
                        List.of(ValueType.F32),
                        List.of());
        var printI32F32 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i32_f32",
                        List.of(ValueType.I32, ValueType.F32),
                        List.of());
        var printI64 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i64",
                        List.of(ValueType.I64),
                        List.of());
        var printI64_1 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i64_1",
                        List.of(ValueType.I64),
                        List.of());
        var printI64_2 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_i64_2",
                        List.of(ValueType.I64),
                        List.of());
        var printF64 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_f64",
                        List.of(ValueType.F64),
                        List.of());
        var printF64_1 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_f64_1",
                        List.of(ValueType.F64),
                        List.of());
        var printF64F64 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "spectest",
                        "print_f64_f64",
                        List.of(ValueType.F64, ValueType.F64),
                        List.of());
        return new HostImports(
                new HostFunction[] {
                    printI32,
                    printI32_1,
                    printI32_2,
                    printF32,
                    printI32F32,
                    printI64,
                    printI64_1,
                    printI64_2,
                    printF64,
                    printF64F64
                });
    }

    public static HostImports testModule1() {
        return fallback();
    }

    public static HostImports testModule11() {
        return new HostImports(
                new HostGlobal[] {
                    new HostGlobal("spectest", "global_i32", Value.i32(666)),
                    new HostGlobal("spectest", "global_i32_1", Value.i32(666)),
                    new HostGlobal("spectest", "global_i32_2", Value.i32(666)),
                    new HostGlobal("spectest", "global_i32_3", Value.i32(666)),
                    new HostGlobal("spectest", "global_i64", Value.i64(666)),
                    new HostGlobal("spectest", "global_f32", Value.f32(1)),
                    new HostGlobal("spectest", "global_f64", Value.f64(1)),
                });
    }

    public static HostImports fallback() {
        var testFunc =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return null;
                        },
                        "test",
                        "func",
                        List.of(ValueType.I64),
                        List.of(ValueType.I64));
        var testFuncI64 =
                new HostFunction(
                        (Memory memory, Value... args) -> {
                            return new Value[] {args[0]};
                        },
                        "test",
                        "func-i64->i64",
                        List.of(ValueType.I64),
                        List.of(ValueType.I64));
        var base = base().getFunctions();
        var additional = new HostFunction[] {testFunc, testFuncI64};
        HostFunction[] hostFunctions = Arrays.copyOf(base, base.length + additional.length);
        System.arraycopy(additional, 0, hostFunctions, base.length, additional.length);
        return new HostImports(hostFunctions);
    }
}
