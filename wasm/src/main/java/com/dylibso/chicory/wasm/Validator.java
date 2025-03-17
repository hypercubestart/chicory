package com.dylibso.chicory.wasm;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.dylibso.chicory.wasm.types.ActiveDataSegment;
import com.dylibso.chicory.wasm.types.ActiveElement;
import com.dylibso.chicory.wasm.types.AnnotatedInstruction;
import com.dylibso.chicory.wasm.types.CatchOpCode;
import com.dylibso.chicory.wasm.types.DeclarativeElement;
import com.dylibso.chicory.wasm.types.Element;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionBody;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.Global;
import com.dylibso.chicory.wasm.types.GlobalImport;
import com.dylibso.chicory.wasm.types.Instruction;
import com.dylibso.chicory.wasm.types.MutabilityType;
import com.dylibso.chicory.wasm.types.OpCode;
import com.dylibso.chicory.wasm.types.Table;
import com.dylibso.chicory.wasm.types.TableImport;
import com.dylibso.chicory.wasm.types.TagImport;
import com.dylibso.chicory.wasm.types.TagSection;
import com.dylibso.chicory.wasm.types.TagType;
import com.dylibso.chicory.wasm.types.Value;
import com.dylibso.chicory.wasm.types.ValueType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

// Heavily inspired by wazero
// https://github.com/tetratelabs/wazero/blob/5a8a053bff0ae795b264de9672016745cb842070/internal/wasm/func_validation.go
// control flow implementation follows:
// https://webassembly.github.io/spec/core/appendix/algorithm.html
final class Validator {

    private static boolean isNum(ValueType t) {
        return t.isNumeric() || t.equals(ValueType.BOT);
    }

    private static boolean isRef(ValueType t) {
        return t.isReference() || t.equals(ValueType.BOT);
    }

    @SuppressWarnings("PublicField")
    private static class CtrlFrame {
        // OpCode of the current Control Flow instruction
        public final OpCode opCode;
        // params or inputs
        public final List<ValueType> startTypes;
        // returns or outputs
        public final List<ValueType> endTypes;
        // the height of the value stack before entering the current Control Flow instruction
        public final int height;
        // the height of the init stack before entering the current Control Flow
        public final int initHeight;
        // set after unconditional jumps
        public boolean unreachable;
        // if there is no else, we explicit check that the enclosing IF is not returning values
        public boolean hasElse;

        public CtrlFrame(
                OpCode opCode,
                List<ValueType> startTypes,
                List<ValueType> endTypes,
                int height,
                int initHeight,
                boolean unreachable,
                boolean hasElse) {
            this.opCode = opCode;
            this.startTypes = startTypes;
            this.endTypes = endTypes;
            this.initHeight = initHeight;
            this.height = height;
            this.unreachable = unreachable;
            this.hasElse = hasElse;
        }
    }

    private final List<ValueType> valueTypeStack = new ArrayList<>();
    private final List<CtrlFrame> ctrlFrameStack = new ArrayList<>();
    private final List<Integer> initStack = new ArrayList<>();

    private final List<InvalidException> errors = new ArrayList<>();

    private final WasmModule module;
    private final List<ValueType> locals;
    private final List<Boolean> localsInitialized;
    private final List<Global> globalImports;
    private final List<Integer> functionImports;
    private final List<ValueType> tableImports;
    private final List<TagType> tagImports;
    private final int memoryImports;
    private final Set<Integer> declaredFunctions;

    Validator(WasmModule module) {
        this.module = requireNonNull(module);

        this.locals = new ArrayList<>();
        this.localsInitialized = new ArrayList<>();

        this.globalImports =
                module.importSection().stream()
                        .filter(GlobalImport.class::isInstance)
                        .map(GlobalImport.class::cast)
                        .map(i -> new Global(i.type(), i.mutabilityType(), List.of()))
                        .collect(toList());

        this.functionImports =
                module.importSection().stream()
                        .filter(FunctionImport.class::isInstance)
                        .map(FunctionImport.class::cast)
                        .map(FunctionImport::typeIndex)
                        .collect(toList());

        this.tableImports =
                module.importSection().stream()
                        .filter(TableImport.class::isInstance)
                        .map(TableImport.class::cast)
                        .map(TableImport::entryType)
                        .collect(toList());

        this.memoryImports = module.importSection().count(ExternalType.MEMORY);

        this.declaredFunctions =
                module.elementSection().stream()
                        .filter(DeclarativeElement.class::isInstance)
                        .flatMap(element -> element.initializers().stream())
                        .flatMap(this::declaredFunctions)
                        .collect(toSet());

        this.tagImports =
                module.importSection().stream()
                        .filter(TagImport.class::isInstance)
                        .map(TagImport.class::cast)
                        .map(TagImport::tagType)
                        .collect(toList());
    }

    private Stream<Integer> declaredFunctions(List<Instruction> init) {
        if (!init.isEmpty() && init.get(0).opcode() == OpCode.REF_FUNC) {
            int idx = (int) init.get(0).operand(0);
            getFunctionType(idx);
            if (idx >= functionImports.size()) {
                return Stream.of(idx);
            }
        }
        return Stream.empty();
    }

    private void pushVal(ValueType valType) {
        valueTypeStack.add(valType);
    }

    private ValueType popVal() {
        var frame = peekCtrl();
        if (valueTypeStack.size() == frame.height && frame.unreachable) {
            return ValueType.BOT;
        }
        if (valueTypeStack.size() == frame.height) {
            errors.add(
                    new InvalidException(
                            "type mismatch, popVal(), stack reached limit at " + frame.height));
            return ValueType.BOT;
        }
        return valueTypeStack.remove(valueTypeStack.size() - 1);
    }

    private ValueType popVal(ValueType expected) {
        var actual = popVal();
        if (!ValueType.matches(module, actual, expected)
                && !actual.equals(ValueType.BOT)
                && !expected.equals(ValueType.BOT)) {
            errors.add(
                    new InvalidException(
                            "type mismatch: instruction requires ["
                                    + expected.toString().toLowerCase(Locale.ROOT)
                                    + "] but stack has ["
                                    + actual.toString().toLowerCase(Locale.ROOT)
                                    + "]"));
        }
        return actual;
    }

    private ValueType popRef() {
        var actual = popVal();
        if (!isRef(actual)) {
            errors.add(
                    new InvalidException(
                            "type mismatch, popRef(), expected reference type"
                                    + " but got: "
                                    + actual));
        }
        if (actual.equals(ValueType.BOT)) {
            return new ValueType(ValueType.ID.Ref, ValueType.TypeIdxCode.BOT.code());
        }

        return actual;
    }

    private void pushVals(List<ValueType> valTypes) {
        for (var t : valTypes) {
            pushVal(t);
        }
    }

    private List<ValueType> popVals(List<ValueType> valTypes) {
        var popped = new ValueType[valTypes.size()];
        for (int i = 0; i < valTypes.size(); i++) {
            popped[i] = popVal(valTypes.get(valTypes.size() - 1 - i));
        }
        return Arrays.asList(popped);
    }

    private ValueType getLocal(int idx) {
        if (idx >= locals.size()) {
            throw new InvalidException("unknown local " + idx);
        }
        if (!localsInitialized.get(idx)) {
            errors.add(new InvalidException("uninitialized local: index " + idx));
        }
        return getLocalType(idx);
    }

    private void setLocal(int idx) {
        if (idx >= locals.size()) {
            throw new InvalidException("unknown local " + idx);
        }
        if (!localsInitialized.get(idx)) {
            initStack.add(idx);
            localsInitialized.set(idx, true);
        }
    }

    private void resetLocals(int height) {
        while (initStack.size() > height) {
            localsInitialized.set(initStack.remove(initStack.size() - 1), false);
        }
    }

    private void pushCtrl(OpCode opCode, List<ValueType> in, List<ValueType> out) {
        var frame =
                new CtrlFrame(
                        opCode, in, out, valueTypeStack.size(), initStack.size(), false, false);
        pushCtrl(frame);
        pushVals(in);
    }

    private void pushCtrl(CtrlFrame frame) {
        ctrlFrameStack.add(frame);
    }

    private CtrlFrame popCtrl() {
        if (ctrlFrameStack.isEmpty()) {
            errors.add(new InvalidException("type mismatch, control frame stack empty"));
        }
        var frame = peekCtrl();
        popVals(frame.endTypes);
        if (valueTypeStack.size() != frame.height) {
            errors.add(new InvalidException("type mismatch, mismatching stack height"));
        }
        resetLocals(frame.initHeight);
        ctrlFrameStack.remove(ctrlFrameStack.size() - 1);
        return frame;
    }

    private CtrlFrame peekCtrl() {
        return ctrlFrameStack.get(ctrlFrameStack.size() - 1);
    }

    private CtrlFrame getCtrl(int n) {
        return ctrlFrameStack.get(ctrlFrameStack.size() - 1 - n);
    }

    private static List<ValueType> labelTypes(CtrlFrame frame) {
        return (frame.opCode == OpCode.LOOP) ? frame.startTypes : frame.endTypes;
    }

    private void resetAtStackLimit() {
        var frame = peekCtrl();
        while (valueTypeStack.size() > frame.height) {
            valueTypeStack.remove(valueTypeStack.size() - 1);
        }
    }

    private void unreachable() {
        var frame = peekCtrl();
        resetAtStackLimit();
        frame.unreachable = true;
    }

    private void validateMemory(int id) {
        if ((module.memorySection().isEmpty() && memoryImports == 0) || id != 0) {
            throw new InvalidException("unknown memory " + id);
        }
    }

    private void validateLane(int id, int max) {
        if (id < 0 || id >= max) {
            throw new InvalidException("invalid lane index " + id + " max is " + max);
        }
    }

    private void validateDataSegment(int idx) {
        if (idx < 0 || idx >= module.dataSection().dataSegmentCount()) {
            throw new InvalidException("unknown data segment " + idx);
        }
    }

    private List<ValueType> getReturns(AnnotatedInstruction op) {
        var typeId = op.operand(0);
        if (typeId == 0x40) { // epsilon
            return List.of();
        }
        if (ValueType.isValid(typeId)) {
            return List.of(ValueType.forId(typeId));
        }
        return getType((int) typeId).returns();
    }

    private List<ValueType> getParams(AnnotatedInstruction op) {
        var typeId = (int) op.operand(0);
        if (typeId == 0x40) { // epsilon
            return List.of();
        }
        if (ValueType.isValid(typeId)) {
            return List.of();
        }
        if (typeId >= module.typeSection().typeCount()) {
            throw new MalformedException("unexpected end");
        }
        return getType(typeId).params();
    }

    private ValueType getLocalType(int idx) {
        if (idx >= locals.size()) {
            throw new InvalidException("unknown local " + idx);
        }
        return locals.get(idx);
    }

    private FunctionType getType(int idx) {
        if (idx < 0 || idx >= module.typeSection().typeCount()) {
            throw new InvalidException("unknown type " + idx);
        }
        return module.typeSection().getType(idx);
    }

    private Global getGlobal(int idx) {
        if (idx < 0 || idx >= globalImports.size() + module.globalSection().globalCount()) {
            throw new InvalidException("unknown global " + idx);
        }
        if (idx < globalImports.size()) {
            return globalImports.get(idx);
        }
        return module.globalSection().getGlobal(idx - globalImports.size());
    }

    private int getFunctionType(int idx) {
        if (idx < 0 || idx >= functionImports.size() + module.functionSection().functionCount()) {
            throw new InvalidException("unknown function " + idx);
        }
        if (idx < functionImports.size()) {
            return functionImports.get(idx);
        }
        return module.functionSection().getFunctionType(idx - functionImports.size());
    }

    private ValueType getTableType(int idx) {
        if (idx < 0 || idx >= tableImports.size() + module.tableSection().tableCount()) {
            throw new InvalidException("unknown table " + idx);
        }
        if (idx < tableImports.size()) {
            return tableImports.get(idx);
        }
        return module.tableSection().getTable(idx - tableImports.size()).elementType();
    }

    private TagType getTagType(int idx) {
        if (idx < 0 || idx >= tagImports.size() + module.tagSection().get().tagCount()) {
            throw new InvalidException("unknown tag " + idx);
        }
        if (idx < tagImports.size()) {
            return tagImports.get(idx);
        }
        return module.tagSection().get().getTag(idx - tagImports.size());
    }

    private Element getElement(int idx) {
        if (idx < 0 || idx >= module.elementSection().elementCount()) {
            throw new InvalidException("unknown elem segment " + idx);
        }
        return module.elementSection().getElement(idx);
    }

    void validateModule() {
        if (module.functionSection().functionCount() != module.codeSection().functionBodyCount()) {
            throw new MalformedException("function and code section have inconsistent lengths");
        }

        if (module.dataCountSection()
                .map(dcs -> dcs.dataCount() != module.dataSection().dataSegmentCount())
                .orElse(false)) {
            throw new MalformedException("data count and data section have inconsistent lengths");
        }

        if (module.startSection().isPresent()) {
            long index = module.startSection().get().startIndex();
            if (index < 0 || index > Integer.MAX_VALUE) {
                throw new InvalidException("unknown function " + index);
            }
            var type = getType(getFunctionType((int) index));
            if (!type.params().isEmpty() || !type.returns().isEmpty()) {
                throw new InvalidException(
                        "invalid start function, must have empty signature " + type);
            }
        }
    }

    void validateData() {
        // Validate offsets.
        for (var ds : module.dataSection().dataSegments()) {
            if (ds instanceof ActiveDataSegment) {
                var ads = (ActiveDataSegment) ds;
                if (ads.index() != 0) {
                    throw new InvalidException("unknown memory " + ads.index());
                }
                validateConstantExpression(ads.offsetInstructions(), ValueType.I32);
            }
        }
    }

    void validateTypes() {
        var types = module.typeSection().types();
        for (var i = 0; i < types.length; i++) {
            var t = types[i];
            t.params().forEach(this::validateValueType);
            t.returns().forEach(this::validateValueType);

            // a type can only refer to types with idx less than it
            var maxTypeIdx = i - 1;
            Stream.concat(t.params().stream(), t.returns().stream())
                    .forEach(
                            v -> {
                                if (v.isReference()) {
                                    var typeIdx = v.typeIdx();
                                    if (typeIdx > maxTypeIdx) {
                                        throw new InvalidException("unknown type: recursive type");
                                    }
                                }
                            });
        }
    }

    void validateTags() {
        for (var tagType : module.tagSection().map(ts -> ts.types()).orElse(new TagType[0])) {
            var type = module.typeSection().getType(tagType.typeIdx());
            if (type.returns().size() > 0) {
                throw new InvalidException("non-empty tag result type index: " + tagType.typeIdx());
            }
        }
    }

    void validateTables() {
        for (int i = 0; i < module.tableSection().tableCount(); i++) {
            Table t = module.tableSection().getTable(i);
            validateConstantExpression(t.initialize(), t.elementType());
        }
    }

    void validateElements() {
        // Validate offsets.
        var totalFunctions =
                module.functionSection().functionCount()
                        + module.importSection().stream()
                                .filter(i -> i.importType() == ExternalType.FUNCTION)
                                .count();
        for (Element el : module.elementSection().elements()) {
            validateValueType(el.type());
            if (el instanceof ActiveElement) {
                var ae = (ActiveElement) el;
                if (!ValueType.matches(module, ae.type(), getTableType(ae.tableIndex()))) {
                    throw new InvalidException(
                            "type mismatch, active element doesn't match table type");
                }
                validateConstantExpression(ae.offset(), ValueType.I32);
                for (int i = 0; i < ae.initializers().size(); i++) {
                    var initializers = ae.initializers().get(i);
                    if (initializers.stream().filter(x -> x.opcode() != OpCode.END).count() != 1) {
                        // TODO: this indicates that error messages should be concatenated
                        // space for further refactoring
                        throw new InvalidException("type mismatch, constant expression required");
                    }
                    for (var init : initializers) {
                        if (init.opcode() == OpCode.REF_FUNC) {
                            var idx = init.operands()[0];
                            if (idx < 0 || idx >= totalFunctions) {
                                throw new InvalidException("unknown function " + idx);
                            }
                        }
                    }
                    validateConstantExpression(
                            ae.initializers().get(i), getTableType(ae.tableIndex()));
                }
            } else if (el instanceof DeclarativeElement) {
                for (var init : el.initializers()) {
                    if (init.stream().filter(x -> x.opcode() != OpCode.END).count() != 1) {
                        throw new InvalidException("type mismatch, constant expression required");
                    }
                }
            }
        }
    }

    void validateGlobals() {
        for (Global g : module.globalSection().globals()) {
            validateConstantExpression(g.initInstructions(), g.valueType());
            if (g.mutabilityType() == MutabilityType.Const && g.initInstructions().size() > 1) {
                throw new InvalidException("constant expression required");
            }
        }
    }

    private void validateConstantExpression(
            List<? extends Instruction> expr, ValueType expectedType) {
        validateValueType(expectedType);
        int allFuncCount = this.functionImports.size() + module.functionSection().functionCount();
        int constInstrCount = 0;
        for (var instruction : expr) {
            ValueType exprType = null;

            switch (instruction.opcode()) {
                case I32_CONST:
                    exprType = ValueType.I32;
                    constInstrCount++;
                    break;
                case I64_CONST:
                    exprType = ValueType.I64;
                    constInstrCount++;
                    break;
                case F32_CONST:
                    exprType = ValueType.F32;
                    constInstrCount++;
                    break;
                case F64_CONST:
                    exprType = ValueType.F64;
                    constInstrCount++;
                    break;
                case V128_CONST:
                    exprType = ValueType.V128;
                    constInstrCount++;
                    break;
                case REF_NULL:
                    {
                        int operand = (int) instruction.operand(0);
                        exprType = new ValueType(ValueType.ID.RefNull, operand);
                        constInstrCount++;
                        break;
                    }
                case REF_FUNC:
                    {
                        constInstrCount++;
                        int idx = (int) instruction.operand(0);
                        exprType = new ValueType(ValueType.ID.Ref, getFunctionType(idx));

                        if (idx < 0 || idx > allFuncCount) {
                            throw new InvalidException("unknown function " + idx);
                        }

                        break;
                    }
                case GLOBAL_GET:
                    {
                        var idx = (int) instruction.operand(0);
                        if (idx < globalImports.size()) {
                            var global = globalImports.get(idx);
                            if (global.mutabilityType() != MutabilityType.Const) {
                                throw new InvalidException(
                                        "constant expression required, initializer expression"
                                                + " cannot reference a mutable global");
                            }
                            exprType = global.valueType();
                        } else {
                            throw new InvalidException(
                                    "unknown global "
                                            + idx
                                            + ", initializer expression can only reference"
                                            + " an imported global");
                        }
                        constInstrCount++;
                        break;
                    }
                case END:
                    break;
                default:
                    throw new InvalidException(
                            "constant expression required, but non-constant instruction"
                                    + " encountered: "
                                    + instruction);
            }

            if (exprType != null && !ValueType.matches(module, exprType, expectedType)) {
                throw new InvalidException("type mismatch");
            }

            // There must be at most one constant instruction.
            if (constInstrCount > 1) {
                throw new InvalidException("type mismatch, multiple constant expressions found");
            }
        }
        if (constInstrCount <= 0) {
            throw new InvalidException("type mismatch, no constant expressions found");
        }
    }

    void validateFunctions() {
        for (var i = 0; i < module.codeSection().functionBodyCount(); i++) {
            var body = module.codeSection().getFunctionBody(i);
            var idx = functionImports.size() + i;
            var type = getType(getFunctionType(idx));
            validateFunction(idx, body, type);
        }
    }

    private static int[] typesWithDefaultValue =
            new int[] {
                ValueType.ID.F64,
                ValueType.ID.F64,
                ValueType.ID.F32,
                ValueType.ID.I64,
                ValueType.ID.I32,
                ValueType.ID.V128,
                ValueType.ID.RefNull
            };

    private static boolean hasDefaultValue(ValueType t) {
        for (var t2 : typesWithDefaultValue) {
            if (t.opcode() == t2) {
                return true;
            }
        }

        return false;
    }

    private void validateValueType(ValueType valueType) {
        if (valueType.isReference() && valueType.typeIdx() >= 0) {
            int idx = valueType.typeIdx();
            if (idx >= module.typeSection().typeCount()) {
                throw new InvalidException("unknown type " + idx);
            }
        }
    }

    @SuppressWarnings("UnnecessaryCodeBlock")
    void validateFunction(int funcIdx, FunctionBody body, FunctionType functionType) {
        valueTypeStack.clear();
        locals.clear();
        localsInitialized.clear();

        functionType
                .params()
                .forEach(
                        t -> {
                            validateValueType(t);
                            locals.add(t);
                            localsInitialized.add(true);
                        });

        body.localTypes()
                .forEach(
                        t -> {
                            validateValueType(t);
                            locals.add(t);
                            localsInitialized.add(hasDefaultValue(t));
                        });

        functionType.returns().forEach(this::validateValueType);

        pushCtrl(null, new ArrayList<>(), functionType.returns());

        for (var i = 0; i < body.instructions().size(); i++) {
            var op = body.instructions().get(i);

            // control flow instructions handling
            switch (op.opcode()) {
                case UNREACHABLE:
                    unreachable();
                    break;
                case TRY_TABLE:
                    {
                        var t1 = getParams(op);
                        var t2 = getReturns(op);
                        popVals(t1);
                        // and now the catches
                        var catches = CatchOpCode.decode(op.operands());

                        for (int idx = 0; idx < catches.size(); idx++) {
                            var currentCatch = catches.get(idx);
                            if (ctrlFrameStack.size() < currentCatch.label()) {
                                throw new InvalidException("something something");
                            }
                            // push_ctrl(catch, [], label_types(ctrls[handler.label]))
                            // using THROW instead of CATCH ... doesn't matter as it's removed right
                            // after
                            pushCtrl(
                                    OpCode.THROW,
                                    List.of(),
                                    labelTypes(getCtrl(currentCatch.label())));
                            switch (currentCatch.opcode()) {
                                case CATCH:
                                    {
                                        var tagType =
                                                module.typeSection()
                                                        .getType(
                                                                getTagType(currentCatch.tag())
                                                                        .typeIdx());
                                        pushVals(tagType.params());
                                        break;
                                    }
                                case CATCH_REF:
                                    {
                                        var tagType =
                                                module.typeSection()
                                                        .getType(
                                                                getTagType(currentCatch.tag())
                                                                        .typeIdx());
                                        pushVals(tagType.params());
                                        pushVal(ValueType.ExnRef);
                                        break;
                                    }
                                case CATCH_ALL:
                                    break;
                                case CATCH_ALL_REF:
                                    pushVal(ValueType.ExnRef);
                                    break;
                            }
                            popCtrl();
                        }
                        pushCtrl(op.opcode(), t1, t2);
                        break;
                    }
                case THROW:
                    {
                        var tagNumber = (int) op.operand(0);
                        if ((tagImports.size()
                                        + module.tagSection().map(TagSection::tagCount).orElse(0))
                                <= tagNumber) {
                            throw new InvalidException("unknown tag " + tagNumber);
                        }
                        var type = module.typeSection().getType(getTagType(tagNumber).typeIdx());
                        popVals(type.params());
                        assert (type.returns().size() == 0);
                        unreachable();
                        break;
                    }
                case THROW_REF:
                    {
                        popVal(ValueType.ExnRef);
                        unreachable();
                        break;
                    }
                case IF:
                    popVal(ValueType.I32);
                    // fallthrough
                case LOOP:
                    // t1* -> t2*
                    // fallthrough
                case BLOCK:
                    {
                        var t1 = getParams(op);
                        var t2 = getReturns(op);
                        t2.forEach(this::validateValueType);
                        popVals(t1);
                        pushCtrl(op.opcode(), t1, t2);
                        break;
                    }
                case END:
                    {
                        var frame = popCtrl();
                        if (frame.opCode == OpCode.IF
                                && !frame.hasElse
                                && frame.startTypes.size() != frame.endTypes.size()) {
                            throw new InvalidException("type mismatch, unbalanced if branches");
                        }
                        pushVals(frame.endTypes);
                        break;
                    }
                case ELSE:
                    {
                        var frame = popCtrl();
                        if (frame.opCode != OpCode.IF) {
                            throw new InvalidException("else doesn't belong to if");
                        }
                        pushCtrl(op.opcode(), frame.startTypes, frame.endTypes);
                        peekCtrl().hasElse = true;
                        break;
                    }
                case BR:
                    {
                        var n = (int) op.operand(0);
                        popVals(labelTypes(getCtrl(n)));
                        unreachable();
                        break;
                    }
                case BR_IF:
                    {
                        popVal(ValueType.I32);
                        var n = (int) op.operand(0);
                        var labelTypes = labelTypes(getCtrl(n));
                        popVals(labelTypes);
                        pushVals(labelTypes);
                        break;
                    }
                case BR_TABLE:
                    {
                        popVal(ValueType.I32);
                        var m = (int) op.operand(op.operandCount() - 1);
                        if ((ctrlFrameStack.size() - 1 - m) < 0) {
                            throw new InvalidException("unknown label " + m);
                        }
                        var defaultBranchLabelTypes = labelTypes(getCtrl(m));
                        var arity = defaultBranchLabelTypes.size();
                        for (var idx = 0; idx < op.operandCount() - 1; idx++) {
                            var n = (int) op.operand(idx);
                            CtrlFrame ctrlFrame;
                            try {
                                ctrlFrame = getCtrl(n);
                            } catch (IndexOutOfBoundsException e) {
                                throw new InvalidException("unknown label", e);
                            }
                            var labelTypes = labelTypes(ctrlFrame);
                            if (!ctrlFrame.unreachable) {
                                if (labelTypes.size() != arity) {
                                    throw new InvalidException(
                                            "type mismatch, mismatched arity in BR_TABLE for label "
                                                    + n);
                                }
                                for (var t = 0; t < arity; t++) {
                                    if (!labelTypes.get(t).equals(defaultBranchLabelTypes.get(t))) {
                                        throw new InvalidException(
                                                "type mismatch, br_table labels have inconsistent"
                                                        + " types: expected: "
                                                        + defaultBranchLabelTypes.get(t)
                                                        + ", got: "
                                                        + labelTypes.get(t));
                                    }
                                }
                            }
                            pushVals(popVals(labelTypes));
                        }
                        var reversed = new ArrayList<>(defaultBranchLabelTypes);
                        Collections.reverse(reversed);
                        popVals(reversed);
                        unreachable();
                        break;
                    }
                case BR_ON_NULL:
                    {
                        var n = (int) op.operand(0);
                        var rt = popRef();
                        var labelTypes = labelTypes(getCtrl(n));
                        popVals(labelTypes);
                        pushVals(labelTypes);
                        pushVal(new ValueType(ValueType.ID.Ref, rt.typeIdx()));
                        break;
                    }
                case BR_ON_NON_NULL:
                    {
                        var n = (int) op.operand(0);
                        var rt = popRef();
                        pushVal(new ValueType(ValueType.ID.Ref, rt.typeIdx()));
                        var labelTypes = labelTypes(getCtrl(n));
                        popVals(labelTypes);
                        pushVals(labelTypes);
                        popVal();
                        break;
                    }
                case RETURN:
                    VALIDATE_RETURN();
                    break;
                case RETURN_CALL:
                    VALIDATE_CALL((int) op.operand(0), true);
                    VALIDATE_RETURN();
                    break;
                case RETURN_CALL_INDIRECT:
                    VALIDATE_CALL_INDIRECT((int) op.operand(0), (int) op.operand(1), true);
                    VALIDATE_RETURN();
                    break;
                case RETURN_CALL_REF:
                    VALIDATE_CALL_REF((int) op.operand(0), true);
                    VALIDATE_RETURN();
                    break;
                default:
                    break;
            }

            switch (op.opcode()) {
                case MEMORY_COPY:
                    validateMemory((int) op.operand(0));
                    validateMemory((int) op.operand(1));
                    break;
                case MEMORY_FILL:
                    validateMemory((int) op.operand(0));
                    break;
                case MEMORY_INIT:
                    validateMemory((int) op.operand(1));
                    validateDataSegment((int) op.operand(0));
                    break;
                case MEMORY_SIZE:
                case MEMORY_GROW:
                case I32_LOAD:
                case I32_LOAD8_U:
                case I32_LOAD8_S:
                case I32_LOAD16_U:
                case I32_LOAD16_S:
                case I64_LOAD:
                case I64_LOAD8_S:
                case I64_LOAD8_U:
                case I64_LOAD16_S:
                case I64_LOAD16_U:
                case I64_LOAD32_S:
                case I64_LOAD32_U:
                case F32_LOAD:
                case F64_LOAD:
                case I32_STORE:
                case I32_STORE8:
                case I32_STORE16:
                case I64_STORE:
                case I64_STORE8:
                case I64_STORE16:
                case I64_STORE32:
                case F32_STORE:
                case F64_STORE:
                case V128_STORE:
                    validateMemory(0);
                    break;
                default:
                    break;
            }

            switch (op.opcode()) {
                case V128_LOAD8_LANE:
                case V128_STORE8_LANE:
                    validateLane((int) op.operand(2), 16);
                    break;
                case V128_LOAD16_LANE:
                case V128_STORE16_LANE:
                    validateLane((int) op.operand(2), 8);
                    break;
                case V128_LOAD32_LANE:
                case V128_STORE32_LANE:
                    validateLane((int) op.operand(2), 4);
                    break;
                case V128_LOAD64_LANE:
                case V128_STORE64_LANE:
                    validateLane((int) op.operand(2), 2);
                    break;
                case I8x16_REPLACE_LANE:
                case I8x16_EXTRACT_LANE_S:
                case I8x16_EXTRACT_LANE_U:
                    validateLane((int) op.operand(0), 16);
                    break;
                case I16x8_REPLACE_LANE:
                case I16x8_EXTRACT_LANE_S:
                case I16x8_EXTRACT_LANE_U:
                    validateLane((int) op.operand(0), 8);
                    break;
                case I32x4_REPLACE_LANE:
                case F32x4_REPLACE_LANE:
                case I32x4_EXTRACT_LANE:
                case F32x4_EXTRACT_LANE:
                    validateLane((int) op.operand(0), 4);
                    break;
                case I64x2_REPLACE_LANE:
                case F64x2_REPLACE_LANE:
                case I64x2_EXTRACT_LANE:
                case F64x2_EXTRACT_LANE:
                    validateLane((int) op.operand(0), 2);
                    break;
                case I8x16_SHUFFLE:
                    var operands = Value.vecTo8(new long[] {op.operand(0), op.operand(1)});
                    for (int j = 0; j < 16; j++) {
                        validateLane(operands[j], 32);
                    }
                    break;
                default:
                    break;
            }

            switch (op.opcode()) {
                case NOP:
                case UNREACHABLE:
                case THROW:
                case THROW_REF:
                case TRY_TABLE:
                case LOOP:
                case BLOCK:
                case IF:
                case ELSE:
                case RETURN:
                case RETURN_CALL:
                case RETURN_CALL_INDIRECT:
                case RETURN_CALL_REF:
                case BR_IF:
                case BR_TABLE:
                case BR:
                case BR_ON_NULL:
                case BR_ON_NON_NULL:
                case END:
                    break;
                case DATA_DROP:
                    {
                        validateDataSegment((int) op.operand(0));
                        break;
                    }
                case DROP:
                    {
                        popVal();
                        break;
                    }
                case I32_STORE:
                case I32_STORE8:
                case I32_STORE16:
                    {
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        break;
                    }
                case I32_LOAD:
                case I32_LOAD8_U:
                case I32_LOAD8_S:
                case I32_LOAD16_U:
                case I32_LOAD16_S:
                case I32_CLZ:
                case I32_CTZ:
                case I32_POPCNT:
                case I32_EXTEND_8_S:
                case I32_EXTEND_16_S:
                case I32_EQZ:
                case MEMORY_GROW:
                    {
                        popVal(ValueType.I32);
                        pushVal(ValueType.I32);
                        break;
                    }
                case TABLE_SIZE:
                case I32_CONST:
                case MEMORY_SIZE:
                    {
                        pushVal(ValueType.I32);
                        break;
                    }
                case I32_ADD:
                case I32_SUB:
                case I32_MUL:
                case I32_DIV_S:
                case I32_DIV_U:
                case I32_REM_S:
                case I32_REM_U:
                case I32_AND:
                case I32_OR:
                case I32_XOR:
                case I32_EQ:
                case I32_NE:
                case I32_LT_S:
                case I32_LT_U:
                case I32_LE_S:
                case I32_LE_U:
                case I32_GT_S:
                case I32_GT_U:
                case I32_GE_S:
                case I32_GE_U:
                case I32_SHL:
                case I32_SHR_U:
                case I32_SHR_S:
                case I32_ROTL:
                case I32_ROTR:
                    {
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        pushVal(ValueType.I32);
                        break;
                    }
                case I32_WRAP_I64:
                case I64_EQZ:
                    {
                        popVal(ValueType.I64);
                        pushVal(ValueType.I32);
                        break;
                    }
                case I32_TRUNC_F32_S:
                case I32_TRUNC_F32_U:
                case I32_TRUNC_SAT_F32_S:
                case I32_TRUNC_SAT_F32_U:
                case I32_REINTERPRET_F32:
                    {
                        popVal(ValueType.F32);
                        pushVal(ValueType.I32);
                        break;
                    }
                case I32_TRUNC_F64_S:
                case I32_TRUNC_F64_U:
                case I32_TRUNC_SAT_F64_S:
                case I32_TRUNC_SAT_F64_U:
                    {
                        popVal(ValueType.F64);
                        pushVal(ValueType.I32);
                        break;
                    }
                case I64_LOAD:
                case I64_LOAD8_S:
                case I64_LOAD8_U:
                case I64_LOAD16_S:
                case I64_LOAD16_U:
                case I64_LOAD32_S:
                case I64_LOAD32_U:
                case I64_EXTEND_I32_U:
                case I64_EXTEND_I32_S:
                    {
                        popVal(ValueType.I32);
                        pushVal(ValueType.I64);
                        break;
                    }
                case I64_CONST:
                    {
                        pushVal(ValueType.I64);
                        break;
                    }
                case I64_STORE:
                case I64_STORE8:
                case I64_STORE16:
                case I64_STORE32:
                    {
                        popVal(ValueType.I64);
                        popVal(ValueType.I32);
                        break;
                    }
                case I64_ADD:
                case I64_SUB:
                case I64_MUL:
                case I64_DIV_S:
                case I64_DIV_U:
                case I64_REM_S:
                case I64_REM_U:
                case I64_AND:
                case I64_OR:
                case I64_XOR:
                case I64_SHL:
                case I64_SHR_U:
                case I64_SHR_S:
                case I64_ROTL:
                case I64_ROTR:
                    {
                        popVal(ValueType.I64);
                        popVal(ValueType.I64);
                        pushVal(ValueType.I64);
                        break;
                    }
                case I64_EQ:
                case I64_NE:
                case I64_LT_S:
                case I64_LT_U:
                case I64_LE_S:
                case I64_LE_U:
                case I64_GT_S:
                case I64_GT_U:
                case I64_GE_S:
                case I64_GE_U:
                    {
                        popVal(ValueType.I64);
                        popVal(ValueType.I64);
                        pushVal(ValueType.I32);
                        break;
                    }
                case I64_CLZ:
                case I64_CTZ:
                case I64_POPCNT:
                case I64_EXTEND_8_S:
                case I64_EXTEND_16_S:
                case I64_EXTEND_32_S:
                    {
                        popVal(ValueType.I64);
                        pushVal(ValueType.I64);
                        break;
                    }
                case I64_REINTERPRET_F64:
                case I64_TRUNC_F64_S:
                case I64_TRUNC_F64_U:
                case I64_TRUNC_SAT_F64_S:
                case I64_TRUNC_SAT_F64_U:
                    {
                        popVal(ValueType.F64);
                        pushVal(ValueType.I64);
                        break;
                    }
                case I64_TRUNC_F32_S:
                case I64_TRUNC_F32_U:
                case I64_TRUNC_SAT_F32_S:
                case I64_TRUNC_SAT_F32_U:
                    {
                        popVal(ValueType.F32);
                        pushVal(ValueType.I64);
                        break;
                    }
                case F32_STORE:
                    {
                        popVal(ValueType.F32);
                        popVal(ValueType.I32);
                        break;
                    }
                case F32_CONST:
                    {
                        pushVal(ValueType.F32);
                        break;
                    }
                case F32_LOAD:
                case F32_CONVERT_I32_S:
                case F32_CONVERT_I32_U:
                case F32_REINTERPRET_I32:
                    {
                        popVal(ValueType.I32);
                        pushVal(ValueType.F32);
                        break;
                    }
                case F32_CONVERT_I64_S:
                case F32_CONVERT_I64_U:
                    {
                        popVal(ValueType.I64);
                        pushVal(ValueType.F32);
                        break;
                    }
                case F64_LOAD:
                case F64_CONVERT_I32_S:
                case F64_CONVERT_I32_U:
                    {
                        popVal(ValueType.I32);
                        pushVal(ValueType.F64);
                        break;
                    }
                case F64_CONVERT_I64_S:
                case F64_CONVERT_I64_U:
                case F64_REINTERPRET_I64:
                    {
                        popVal(ValueType.I64);
                        pushVal(ValueType.F64);
                        break;
                    }
                case F64_PROMOTE_F32:
                    {
                        popVal(ValueType.F32);
                        pushVal(ValueType.F64);
                        break;
                    }
                case F32_DEMOTE_F64:
                    {
                        popVal(ValueType.F64);
                        pushVal(ValueType.F32);
                        break;
                    }
                case F32_SQRT:
                case F32_ABS:
                case F32_NEG:
                case F32_CEIL:
                case F32_FLOOR:
                case F32_TRUNC:
                case F32_NEAREST:
                    {
                        popVal(ValueType.F32);
                        pushVal(ValueType.F32);
                        break;
                    }
                case F32_ADD:
                case F32_SUB:
                case F32_MUL:
                case F32_DIV:
                case F32_MIN:
                case F32_MAX:
                case F32_COPYSIGN:
                    {
                        popVal(ValueType.F32);
                        popVal(ValueType.F32);
                        pushVal(ValueType.F32);
                        break;
                    }
                case F32_EQ:
                case F32_NE:
                case F32_LT:
                case F32_LE:
                case F32_GT:
                case F32_GE:
                    {
                        popVal(ValueType.F32);
                        popVal(ValueType.F32);
                        pushVal(ValueType.I32);
                        break;
                    }
                case F64_STORE:
                    {
                        popVal(ValueType.F64);
                        popVal(ValueType.I32);
                        break;
                    }
                case F64_CONST:
                    {
                        pushVal(ValueType.F64);
                        break;
                    }
                case F64_SQRT:
                case F64_ABS:
                case F64_NEG:
                case F64_CEIL:
                case F64_FLOOR:
                case F64_TRUNC:
                case F64_NEAREST:
                    {
                        popVal(ValueType.F64);
                        pushVal(ValueType.F64);
                        break;
                    }
                case F64_ADD:
                case F64_SUB:
                case F64_MUL:
                case F64_DIV:
                case F64_MIN:
                case F64_MAX:
                case F64_COPYSIGN:
                    {
                        popVal(ValueType.F64);
                        popVal(ValueType.F64);
                        pushVal(ValueType.F64);
                        break;
                    }
                case F64_EQ:
                case F64_NE:
                case F64_LT:
                case F64_LE:
                case F64_GT:
                case F64_GE:
                    {
                        popVal(ValueType.F64);
                        popVal(ValueType.F64);
                        pushVal(ValueType.I32);
                        break;
                    }
                case LOCAL_SET:
                    {
                        var index = (int) op.operand(0);
                        popVal(getLocalType(index));
                        setLocal(index);
                        break;
                    }
                case LOCAL_GET:
                    {
                        var index = (int) op.operand(0);
                        getLocal(index);
                        pushVal(getLocalType(index));
                        break;
                    }
                case LOCAL_TEE:
                    {
                        var index = (int) op.operand(0);
                        ValueType actualType = popVal();
                        setLocal(index);
                        ValueType localType = getLocalType(index);
                        if (!ValueType.matches(module, actualType, localType)) {
                            throw new InvalidException(
                                    "type mismatch: local_tee: " + actualType + " " + localType);
                        }
                        pushVal(localType);
                        break;
                    }
                case GLOBAL_GET:
                    {
                        var global = getGlobal((int) op.operand(0));
                        pushVal(global.valueType());
                        break;
                    }
                case GLOBAL_SET:
                    {
                        var global = getGlobal((int) op.operand(0));
                        if (global.mutabilityType() == MutabilityType.Const) {
                            // global.wast in the origin spec and function references
                            // have exact same test that exact two different errors
                            // TOOD: figure out which one
                            throw new InvalidException("global is immutable, immutable global");
                        }
                        popVal(global.valueType());
                        break;
                    }
                case CALL:
                    VALIDATE_CALL((int) op.operand(0), false);
                    break;
                case CALL_INDIRECT:
                    VALIDATE_CALL_INDIRECT(op.operand(0), (int) op.operand(1), false);
                    break;
                case CALL_REF:
                    VALIDATE_CALL_REF((int) op.operand(0), false);
                    break;
                case REF_NULL:
                    {
                        int operand = (int) op.operand(0);
                        ValueType type = new ValueType(ValueType.ID.RefNull, operand);
                        pushVal(type);
                        break;
                    }
                case REF_IS_NULL:
                    {
                        popRef();
                        pushVal(ValueType.I32);
                        break;
                    }
                case REF_AS_NON_NULL:
                    {
                        var rt = popRef();
                        pushVal(new ValueType(ValueType.ID.Ref, rt.typeIdx()));
                        break;
                    }
                case REF_FUNC:
                    {
                        var idx = (int) op.operand(0);
                        if (idx == funcIdx // reference to self
                                && !declaredFunctions.contains(idx)) {
                            throw new InvalidException("undeclared function reference");
                        }
                        pushVal(new ValueType(ValueType.ID.Ref, getFunctionType(idx)));
                        break;
                    }
                case SELECT:
                    {
                        popVal(ValueType.I32);
                        var t1 = popVal();
                        var t2 = popVal();
                        if (!(isNum(t1) && isNum(t2))) {
                            throw new InvalidException(
                                    "type mismatch: select should have numeric arguments");
                        }
                        if (!t1.equals(t2)
                                && !t1.equals(ValueType.BOT)
                                && !t2.equals(ValueType.BOT)) {
                            throw new InvalidException(
                                    "type mismatch, in SELECT t1: " + t1 + ", t2: " + t2);
                        }
                        if (t1.equals(ValueType.BOT)) {
                            pushVal(t2);
                        } else {
                            pushVal(t1);
                        }
                        break;
                    }
                case SELECT_T:
                    {
                        popVal(ValueType.I32);
                        if (op.operands().length <= 0 || op.operands().length > 1) {
                            throw new InvalidException("invalid result arity");
                        }
                        var t = ValueType.forId(op.operand(0));
                        validateValueType(t);
                        popVal(t);
                        popVal(t);
                        pushVal(t);
                        break;
                    }
                case TABLE_COPY:
                    {
                        var table1 = getTableType((int) op.operand(1));
                        var table2 = getTableType((int) op.operand(0));

                        if (!ValueType.matches(module, table1, table2)) {
                            throw new InvalidException(
                                    "type mismatch, table 1 type: "
                                            + table1
                                            + ", table 2 type: "
                                            + table2);
                        }

                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        break;
                    }
                case TABLE_INIT:
                    {
                        var table = getTableType((int) op.operand(1));
                        var elemIdx = (int) op.operand(0);
                        var elem = getElement(elemIdx);

                        if (!ValueType.matches(module, elem.type(), table)) {
                            throw new InvalidException(
                                    "type mismatch, table type: "
                                            + table
                                            + ", elem type: "
                                            + elem.type());
                        }

                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        break;
                    }
                case MEMORY_COPY:
                case MEMORY_FILL:
                case MEMORY_INIT:
                    {
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        popVal(ValueType.I32);
                        break;
                    }
                case TABLE_FILL:
                    {
                        popVal(ValueType.I32);
                        popVal(getTableType((int) op.operand(0)));
                        popVal(ValueType.I32);
                        break;
                    }
                case TABLE_GET:
                    {
                        popVal(ValueType.I32);
                        pushVal(getTableType((int) op.operand(0)));
                        break;
                    }
                case TABLE_SET:
                    {
                        popVal(getTableType((int) op.operand(0)));
                        popVal(ValueType.I32);
                        break;
                    }
                case TABLE_GROW:
                    {
                        popVal(ValueType.I32);
                        popVal(getTableType((int) op.operand(0)));
                        pushVal(ValueType.I32);
                        break;
                    }
                case ELEM_DROP:
                    {
                        var index = (int) op.operand(0);
                        getElement(index);
                        break;
                    }
                case V128_LOAD:
                case V128_LOAD8x8_S:
                case V128_LOAD8x8_U:
                case V128_LOAD16x4_S:
                case V128_LOAD16x4_U:
                case V128_LOAD32x2_S:
                case V128_LOAD32x2_U:
                case V128_LOAD8_SPLAT:
                case V128_LOAD16_SPLAT:
                case V128_LOAD32_SPLAT:
                case V128_LOAD64_SPLAT:
                case V128_LOAD32_ZERO:
                case V128_LOAD64_ZERO:
                case I8x16_SPLAT:
                case I16x8_SPLAT:
                case I32x4_SPLAT:
                    {
                        popVal(ValueType.I32);
                        pushVal(ValueType.V128);
                        break;
                    }
                case F32x4_SPLAT:
                    {
                        popVal(ValueType.F32);
                        pushVal(ValueType.V128);
                        break;
                    }
                case I64x2_SPLAT:
                    {
                        popVal(ValueType.I64);
                        pushVal(ValueType.V128);
                        break;
                    }
                case F64x2_SPLAT:
                    {
                        popVal(ValueType.F64);
                        pushVal(ValueType.V128);
                        break;
                    }
                case V128_CONST:
                    {
                        pushVal(ValueType.V128);
                        break;
                    }
                case I8x16_REPLACE_LANE:
                case I16x8_REPLACE_LANE:
                case I32x4_REPLACE_LANE:
                case I8x16_SHL:
                case I8x16_SHR_S:
                case I8x16_SHR_U:
                case I16x8_SHL:
                case I16x8_SHR_S:
                case I16x8_SHR_U:
                case I32x4_SHL:
                case I32x4_SHR_S:
                case I32x4_SHR_U:
                case I64x2_SHL:
                case I64x2_SHR_S:
                case I64x2_SHR_U:
                    {
                        popVal(ValueType.I32);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case I64x2_REPLACE_LANE:
                    {
                        popVal(ValueType.I64);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case F32x4_REPLACE_LANE:
                    {
                        popVal(ValueType.F32);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case F64x2_REPLACE_LANE:
                    {
                        popVal(ValueType.F64);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case I8x16_ALL_TRUE:
                case I8x16_BITMASK:
                case I16x8_ALL_TRUE:
                case I16x8_BITMASK:
                case I32x4_ALL_TRUE:
                case I32x4_BITMASK:
                case I64x2_ALL_TRUE:
                case I64x2_BITMASK:
                case I8x16_EXTRACT_LANE_S:
                case I8x16_EXTRACT_LANE_U:
                case I16x8_EXTRACT_LANE_S:
                case I16x8_EXTRACT_LANE_U:
                case I32x4_EXTRACT_LANE:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.I32);
                        break;
                    }
                case F32x4_EXTRACT_LANE:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.F32);
                        break;
                    }
                case I64x2_EXTRACT_LANE:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.I64);
                        break;
                    }
                case F64x2_EXTRACT_LANE:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.F64);
                        break;
                    }
                case I8x16_SHUFFLE:
                case I8x16_SWIZZLE:
                case I8x16_EQ:
                case I8x16_NE:
                case I8x16_LT_S:
                case I8x16_LT_U:
                case I8x16_GT_S:
                case I8x16_GT_U:
                case I8x16_LE_S:
                case I8x16_LE_U:
                case I8x16_GE_S:
                case I8x16_GE_U:
                case I8x16_MIN_S:
                case I8x16_MIN_U:
                case I8x16_MAX_S:
                case I8x16_MAX_U:
                case I8x16_AVGR_U:
                case I8x16_SUB:
                case I8x16_SUB_SAT_S:
                case I8x16_SUB_SAT_U:
                case I8x16_ADD:
                case I8x16_ADD_SAT_S:
                case I8x16_ADD_SAT_U:
                case I8x16_NARROW_I16x8_S:
                case I8x16_NARROW_I16x8_U:
                case I16x8_NE:
                case I16x8_EQ:
                case I16x8_ADD:
                case I16x8_ADD_SAT_S:
                case I16x8_ADD_SAT_U:
                case I16x8_SUB:
                case I16x8_SUB_SAT_S:
                case I16x8_SUB_SAT_U:
                case I16x8_MUL:
                case I16x8_LT_S:
                case I16x8_LT_U:
                case I16x8_GT_S:
                case I16x8_GT_U:
                case I16x8_LE_S:
                case I16x8_LE_U:
                case I16x8_GE_S:
                case I16x8_GE_U:
                case I16x8_MIN_S:
                case I16x8_MIN_U:
                case I16x8_MAX_S:
                case I16x8_MAX_U:
                case I16x8_AVGR_U:
                case I16x8_NARROW_I32x4_S:
                case I16x8_NARROW_I32x4_U:
                case I16x8_Q15MULR_SAT_S:
                case I16x8_EXTMUL_LOW_I8x16_S:
                case I16x8_EXTMUL_HIGH_I8x16_S:
                case I16x8_EXTMUL_LOW_I8x16_U:
                case I16x8_EXTMUL_HIGH_I8x16_U:
                case F32x4_NE:
                case F32x4_LT:
                case F32x4_GT:
                case F32x4_LE:
                case F32x4_GE:
                case F32x4_EQ:
                case F32x4_MUL:
                case F32x4_MIN:
                case F32x4_MAX:
                case F32x4_PMIN:
                case F32x4_PMAX:
                case F32x4_DIV:
                case F32x4_ADD:
                case F32x4_SUB:
                case I32x4_NE:
                case I32x4_EQ:
                case I32x4_ADD:
                case I32x4_SUB:
                case I32x4_MUL:
                case I32x4_MIN_S:
                case I32x4_MIN_U:
                case I32x4_MAX_S:
                case I32x4_MAX_U:
                case I32x4_LT_S:
                case I32x4_LT_U:
                case I32x4_LE_S:
                case I32x4_LE_U:
                case I32x4_GE_S:
                case I32x4_GE_U:
                case I32x4_GT_S:
                case I32x4_GT_U:
                case I32x4_DOT_I16x8_S:
                case I32x4_EXTMUL_LOW_I16x8_S:
                case I32x4_EXTMUL_HIGH_I16x8_S:
                case I32x4_EXTMUL_LOW_I16x8_U:
                case I32x4_EXTMUL_HIGH_I16x8_U:
                case I64x2_NE:
                case I64x2_EQ:
                case I64x2_LT_S:
                case I64x2_LE_S:
                case I64x2_GT_S:
                case I64x2_GE_S:
                case I64x2_ADD:
                case I64x2_SUB:
                case I64x2_MUL:
                case I64x2_EXTMUL_LOW_I32x4_S:
                case I64x2_EXTMUL_HIGH_I32x4_S:
                case I64x2_EXTMUL_LOW_I32x4_U:
                case I64x2_EXTMUL_HIGH_I32x4_U:
                case F64x2_NE:
                case F64x2_LT:
                case F64x2_GT:
                case F64x2_LE:
                case F64x2_GE:
                case F64x2_DIV:
                case F64x2_MAX:
                case F64x2_MIN:
                case F64x2_PMAX:
                case F64x2_PMIN:
                case F64x2_EQ:
                case F64x2_ADD:
                case F64x2_SUB:
                case F64x2_MUL:
                case V128_AND:
                case V128_ANDNOT:
                case V128_OR:
                case V128_XOR:
                    {
                        popVal(ValueType.V128);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case I8x16_NEG:
                case I8x16_ABS:
                case I8x16_POPCNT:
                case I16x8_EXTADD_PAIRWISE_I8x16_S:
                case I16x8_EXTADD_PAIRWISE_I8x16_U:
                case I16x8_NEG:
                case I16x8_ABS:
                case I16x8_EXTEND_LOW_I8x16_S:
                case I16x8_EXTEND_HIGH_I8x16_S:
                case I16x8_EXTEND_LOW_I8x16_U:
                case I16x8_EXTEND_HIGH_I8x16_U:
                case I32x4_NEG:
                case I32x4_ABS:
                case I32x4_EXTEND_LOW_I16x8_S:
                case I32x4_EXTEND_HIGH_I16x8_S:
                case I32x4_EXTEND_LOW_I16x8_U:
                case I32x4_EXTEND_HIGH_I16x8_U:
                case I32x4_EXTADD_PAIRWISE_I16x8_S:
                case I32x4_EXTADD_PAIRWISE_I16x8_U:
                case I64x2_EXTEND_LOW_I32x4_S:
                case I64x2_EXTEND_HIGH_I32x4_S:
                case I64x2_EXTEND_LOW_I32x4_U:
                case I64x2_EXTEND_HIGH_I32x4_U:
                case F32x4_NEG:
                case F32x4_ABS:
                case F32x4_SQRT:
                case I32x4_TRUNC_SAT_F32X4_S:
                case I32x4_TRUNC_SAT_F32X4_U:
                case I32x4_TRUNC_SAT_F64x2_S_ZERO:
                case I32x4_TRUNC_SAT_F64x2_U_ZERO:
                case F32x4_CONVERT_I32x4_S:
                case F32x4_CONVERT_I32x4_U:
                case F32x4_CEIL:
                case F32x4_FLOOR:
                case F32x4_TRUNC:
                case F32x4_NEAREST:
                case F64x2_ABS:
                case F64x2_NEG:
                case F64x2_SQRT:
                case F64x2_CEIL:
                case F64x2_FLOOR:
                case F64x2_NEAREST:
                case F64x2_TRUNC:
                case F64x2_CONVERT_LOW_I32x4_S:
                case F64x2_CONVERT_LOW_I32x4_U:
                case F64x2_PROMOTE_LOW_F32x4:
                case F32x4_DEMOTE_LOW_F64x2_ZERO:
                case I64x2_NEG:
                case I64x2_ABS:
                case V128_NOT:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case V128_BITSELECT:
                    {
                        popVal(ValueType.V128);
                        popVal(ValueType.V128);
                        popVal(ValueType.V128);
                        pushVal(ValueType.V128);
                        break;
                    }
                case V128_ANY_TRUE:
                    {
                        popVal(ValueType.V128);
                        pushVal(ValueType.I32);
                        break;
                    }
                case V128_STORE:
                case V128_STORE8_LANE:
                case V128_STORE16_LANE:
                case V128_STORE32_LANE:
                case V128_STORE64_LANE:
                    {
                        popVal(ValueType.V128);
                        popVal(ValueType.I32);
                        break;
                    }
                case V128_LOAD8_LANE:
                case V128_LOAD16_LANE:
                case V128_LOAD32_LANE:
                case V128_LOAD64_LANE:
                    {
                        popVal(ValueType.V128);
                        popVal(ValueType.I32);
                        pushVal(ValueType.V128);
                        break;
                    }
                default:
                    throw new IllegalArgumentException(
                            "Missing type validation opcode handling for " + op.opcode());
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidException(
                    errors.stream().map(Throwable::getMessage).collect(joining(" - ")));
        }

        // to satisfy the check mentioned in the NOTE
        // https://webassembly.github.io/spec/core/binary/modules.html#data-count-section
        if (module.codeSection().isRequiresDataCount() && module.dataCountSection().isEmpty()) {
            throw new MalformedException("data count section required");
        }
    }

    private void validateTailCall(List<ValueType> funcReturnType) {
        var expected = labelTypes(ctrlFrameStack.get(0));

        if (funcReturnType.size() != expected.size()) {
            throw new InvalidException("type mismatch: return arity");
        }

        for (int i = 0; i < funcReturnType.size(); i++) {
            if (!ValueType.matches(module, funcReturnType.get(i), expected.get(i))) {
                throw new InvalidException(
                        "type mismatch: tail call doesn't match frame type at index " + i);
            }
        }
    }

    private void VALIDATE_CALL(int funcId, boolean isReturn) {
        int typeId = getFunctionType(funcId);
        var types = getType(typeId);
        for (int j = types.params().size() - 1; j >= 0; j--) {
            popVal(types.params().get(j));
        }
        pushVals(types.returns());
        if (isReturn) {
            validateTailCall(types.returns());
        }
    }

    private void VALIDATE_CALL_INDIRECT(long typeId, int tableId, boolean isReturn) {
        popVal(ValueType.I32);
        var tableType = getTableType(tableId);
        if (!tableType.equals(ValueType.FuncRef)) {
            throw new InvalidException(
                    "type mismatch expected a table of FuncRefs buf found " + tableType);
        }
        var types = getType((int) typeId);
        for (int j = types.params().size() - 1; j >= 0; j--) {
            popVal(types.params().get(j));
        }
        pushVals(types.returns());
        if (isReturn) {
            validateTailCall(types.returns());
        }
    }

    private void VALIDATE_CALL_REF(int typeId, boolean isReturn) {
        var rt = popRef();
        var funcType = getType(typeId);
        popVals(funcType.params());
        pushVals(funcType.returns());

        if (isReturn) {
            validateTailCall(funcType.returns());
        }

        if (rt.typeIdx() != ValueType.TypeIdxCode.BOT.code()) {
            int idx = rt.typeIdx();
            if (idx < 0) {
                // error
                throw new InvalidException(
                        "type mismatch: call_ref should be called on a defined"
                                + " reference type, got operand: "
                                + idx);
            }
        }
    }

    private void VALIDATE_RETURN() {
        popVals(labelTypes(ctrlFrameStack.get(0)));
        unreachable();
    }
}
