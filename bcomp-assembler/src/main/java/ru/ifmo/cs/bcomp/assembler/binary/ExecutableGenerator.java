package ru.ifmo.cs.bcomp.assembler.binary;

import ru.ifmo.cs.bcomp.assembler.AsmNg;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ExecutableGenerator {

    public ExecutableGenerator() {

    }

    public String loadFile(String path) {
        try(var input = new FileInputStream(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Executable compile(String code) {
        var asm = new AsmNg(code);
        var prog = asm.compile();
        if (prog == null)
            throw new RuntimeException("Invalid program");

        return new Executable(prog);
    }

    public ExecutableBinary process(String path) {
        var text = loadFile(path);
        var res = compile(text);
        return new ExecutableBinary(res.build());
    }
}
