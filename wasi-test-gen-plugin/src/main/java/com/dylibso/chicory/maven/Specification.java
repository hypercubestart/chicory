package com.dylibso.chicory.maven;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Specification {
    private final List<String> args;
    private final List<String> dirs;
    private final Map<String, String> env;
    private final int exitCode;
    private final Optional<String> stdout;

    @JsonCreator
    public Specification(
            @JsonProperty("args") List<String> args,
            @JsonProperty("dirs") List<String> dirs,
            @JsonProperty("env") Map<String, String> env,
            @JsonProperty("exit_code") int exitCode,
            @JsonProperty("stdout") String stdout) {
        this.args = requireNonNullElse(args, emptyList());
        this.dirs = requireNonNullElse(dirs, emptyList());
        this.env = requireNonNullElse(env, emptyMap());
        this.exitCode = exitCode;
        this.stdout = Optional.ofNullable(stdout);
    }

    public List<String> args() {
        return args;
    }

    public List<String> dirs() {
        return dirs;
    }

    public Map<String, String> env() {
        return env;
    }

    public int exitCode() {
        return exitCode;
    }

    public Optional<String> stdout() {
        return stdout;
    }

    public static Specification createDefault() {
        return new Specification(emptyList(), emptyList(), emptyMap(), 0, null);
    }
}
