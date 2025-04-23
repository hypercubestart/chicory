package com.dylibso.chicory.wasm.types;

import org.junit.jupiter.api.Test;

public class NewValueTypeTest {
    @Test
    public void roundtrip() {
        var cases =
                new NewValueType[] {
                    NewValueType.F64,
                    NewValueType.F32,
                    NewValueType.I64,
                    NewValueType.I32,
                    NewValueType.V128,
                    NewValueType.FuncRef,
                    NewValueType.ExternRef,
                    new NewValueType(NewValueType.ID.RefNull, NewValueType.TypeIdxCode.FUNC.code()),
                    new NewValueType(NewValueType.ID.Ref, NewValueType.TypeIdxCode.EXTERN.code()),
                    new NewValueType(NewValueType.ID.Ref, 16),
                };

        for (var vt : cases) {
            assert vt.equals(NewValueType.forId(vt.id())) : "Failed to roundtrip: " + vt;
        }
    }
}
