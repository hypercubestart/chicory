package com.dylibso.chicory.testing;

import static com.dylibso.chicory.wasm.types.Value.REF_NULL_VALUE;

import com.dylibso.chicory.runtime.ByteBufferMemory;
import com.dylibso.chicory.runtime.GlobalInstance;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportGlobal;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportTable;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.TableInstance;
import com.dylibso.chicory.runtime.WasmFunctionHandle;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.dylibso.chicory.wasm.types.Table;
import com.dylibso.chicory.wasm.types.TableLimits;
import com.dylibso.chicory.wasm.types.Value;
import com.dylibso.chicory.wasm.types.ValueType;
import java.util.List;

// https://github.com/WebAssembly/spec/blob/ee82c8e50c5106e0cedada0a083d4cc4129034a2/interpreter/host/spectest.ml
public final class Spectest {
    private static final WasmFunctionHandle noop = (Instance instance, long... args) -> null;

    private Spectest() {}

    public static ImportValues toImportValues() {
        return ImportValues.builder()
                .addFunction(new HostFunction("spectest", "print", List.of(), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i32", List.of(ValueType.I32), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i32_1", List.of(ValueType.I32), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i32_2", List.of(ValueType.I32), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_f32", List.of(ValueType.F32), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_i32_f32",
                                List.of(ValueType.I32, ValueType.F32),
                                List.of(),
                                noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i64", List.of(ValueType.I64), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i64_1", List.of(ValueType.I64), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_i64_2", List.of(ValueType.I64), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest", "print_f64", List.of(ValueType.F64), List.of(), noop))
                .addFunction(
                        new HostFunction(
                                "spectest",
                                "print_f64_f64",
                                List.of(ValueType.F64, ValueType.F64),
                                List.of(),
                                noop))
                .addGlobal(
                        new ImportGlobal(
                                "spectest", "global_i32", new GlobalInstance(Value.i32(666))))
                .addGlobal(
                        new ImportGlobal(
                                "spectest", "global_i64", new GlobalInstance(Value.i64(666))))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_f32",
                                new GlobalInstance(Value.fromFloat(666.6f))))
                .addGlobal(
                        new ImportGlobal(
                                "spectest",
                                "global_f64",
                                new GlobalInstance(Value.fromDouble(666.6))))
                .addMemory(
                        new ImportMemory(
                                "spectest", "memory", new ByteBufferMemory(new MemoryLimits(1, 2))))
                .addTable(
                        new ImportTable(
                                "spectest",
                                "table",
                                new TableInstance(
                                        new Table(ValueType.FuncRef, new TableLimits(10, 20)),
                                        REF_NULL_VALUE)))
                .build();
    }
}
