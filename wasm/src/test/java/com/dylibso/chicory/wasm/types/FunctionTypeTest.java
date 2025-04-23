package com.dylibso.chicory.wasm.types;

import static com.dylibso.chicory.wasm.types.NewValueType.F32;
import static com.dylibso.chicory.wasm.types.NewValueType.I32;
import static com.dylibso.chicory.wasm.types.NewValueType.I64;
import static com.dylibso.chicory.wasm.types.NewValueType.V128;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionTypeTest {
    @Test
    public void toStringContract() {
        var emptyToNil = FunctionType.empty();
        assertEquals("() -> nil", emptyToNil.toString());

        var i32I64ToF32 = FunctionType.of(List.of(I32, I64), List.of(F32));
        assertEquals("(I32,I64) -> (F32)", i32I64ToF32.toString());

        var v128ToI32I32 = FunctionType.of(List.of(V128), List.of(I32, I32));
        assertEquals("(V128) -> (I32,I32)", v128ToI32I32.toString());
    }
}
