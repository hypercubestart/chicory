package com.dylibso.chicory.wasm.types;

import java.util.List;
import java.util.Objects;

public final class FunctionType {
    private final List<NewValueType> params;
    private final List<NewValueType> returns;
    private final int hashCode;

    private FunctionType(List<NewValueType> params, List<NewValueType> returns) {
        this.params = params;
        this.returns = returns;
        hashCode = Objects.hash(params, returns);
    }

    public List<NewValueType> params() {
        return params;
    }

    public List<NewValueType> returns() {
        return returns;
    }

    public boolean paramsMatch(FunctionType other) {
        return params.equals(other.params);
    }

    public boolean returnsMatch(FunctionType other) {
        return returns.equals(other.returns);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FunctionType && equals((FunctionType) obj);
    }

    public boolean equals(FunctionType other) {
        return hashCode == other.hashCode && paramsMatch(other) && returnsMatch(other);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static final FunctionType empty = new FunctionType(List.of(), List.of());

    public static FunctionType returning(NewValueType newValueType) {
        switch (newValueType.opcode()) {
            case NewValueType.ID.ExnRef:
            case NewValueType.ID.V128:
            case NewValueType.ID.F64:
            case NewValueType.ID.F32:
            case NewValueType.ID.I64:
            case NewValueType.ID.I32:
                return new FunctionType(List.of(), List.of(newValueType));
            case NewValueType.ID.RefNull:
                if (newValueType.equals(NewValueType.ExternRef)
                        || newValueType.equals(NewValueType.FuncRef)) {
                    return new FunctionType(List.of(), List.of(newValueType));
                }
                // fallthrough
            default:
                throw new IllegalArgumentException("invalid NewValueType " + newValueType);
        }
    }

    public static FunctionType accepting(NewValueType newValueType) {
        switch (newValueType.opcode()) {
            case NewValueType.ID.ExnRef:
            case NewValueType.ID.V128:
            case NewValueType.ID.F64:
            case NewValueType.ID.F32:
            case NewValueType.ID.I64:
            case NewValueType.ID.I32:
                return new FunctionType(List.of(newValueType), List.of());
            case NewValueType.ID.RefNull:
                if (newValueType.equals(NewValueType.ExternRef)
                        || newValueType.equals(NewValueType.FuncRef)) {
                    return new FunctionType(List.of(newValueType), List.of());
                }
                // fallthrough
            default:
                throw new IllegalArgumentException("invalid NewValueType " + newValueType);
        }
    }

    public boolean typesMatch(FunctionType other) {
        return paramsMatch(other) && returnsMatch(other);
    }

    public static FunctionType of(List<NewValueType> params, List<NewValueType> returns) {
        if (params.isEmpty()) {
            if (returns.isEmpty()) {
                return empty;
            }
            if (returns.size() == 1) {
                return returning(returns.get(0));
            }
        } else if (returns.isEmpty()) {
            if (params.size() == 1) {
                return accepting(params.get(0));
            }
        }
        return new FunctionType(List.copyOf(params), List.copyOf(returns));
    }

    public static FunctionType of(NewValueType[] params, NewValueType[] returns) {
        return of(List.of(params), List.of(returns));
    }

    public static FunctionType empty() {
        return empty;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append('(');
        var nParams = this.params.size();
        for (var i = 0; i < nParams; i++) {
            builder.append(this.params.get(i).toString());
            if (i < nParams - 1) {
                builder.append(',');
            }
        }
        builder.append(") -> ");
        var nReturns = this.returns.size();
        if (nReturns == 0) {
            builder.append("nil");
        } else {
            builder.append('(');
            for (var i = 0; i < nReturns; i++) {
                builder.append(this.returns.get(i).toString());
                if (i < nReturns - 1) {
                    builder.append(',');
                }
            }
            builder.append(')');
        }
        return builder.toString();
    }
}
