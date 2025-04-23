package com.dylibso.chicory.runtime;

import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.NewValueType;
import com.dylibso.chicory.wasm.types.ValueType;
import java.util.ArrayList;
import java.util.List;

public class ImportFunction implements ImportValue {
    private final String module;
    private final String name;
    private final List<NewValueType> paramTypes;
    private final List<NewValueType> returnTypes;
    private final WasmFunctionHandle handle;

    @Deprecated(since = "23/05/2025")
    protected static List<NewValueType> convert(List objs) {
        var result = new ArrayList<NewValueType>(objs.size());
        for (var v : objs) {
            if (v instanceof NewValueType) {
                result.add((NewValueType) v);
            } else if (v instanceof ValueType) {
                result.add(((ValueType) v).toNew());
            } else {
                throw new IllegalArgumentException(
                        "Expected ValueType or NewValueType, but got: "
                                + v.getClass().getCanonicalName());
            }
        }
        return result;
    }

    public ImportFunction(
            String module, String name, FunctionType type, WasmFunctionHandle handle) {
        this.module = module;
        this.name = name;
        this.paramTypes = type.params();
        this.returnTypes = type.returns();
        this.handle = handle;
    }

    @Deprecated(since = "23/05/2025", forRemoval = true)
    public ImportFunction(
            String module,
            String name,
            List paramTypes,
            List returnTypes,
            WasmFunctionHandle handle) {
        this.module = module;
        this.name = name;
        this.paramTypes = convert(paramTypes);
        this.returnTypes = convert(returnTypes);
        this.handle = handle;
    }

    public WasmFunctionHandle handle() {
        return handle;
    }

    @Override
    public String module() {
        return module;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Type type() {
        return Type.FUNCTION;
    }

    public List<NewValueType> paramTypes() {
        return paramTypes;
    }

    public List<NewValueType> returnTypes() {
        return returnTypes;
    }
}
