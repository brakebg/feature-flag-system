package com.example.featureflags.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Writes one JSON line per finished test with its tags and result to {@code
 * target/junit-tags.jsonl}. Gate 14 (spec 11.4) reads it to map AC and ERR IDs to tests. Registered
 * through {@code META-INF/services}, so it runs for Surefire and Failsafe.
 */
public class TagRecordingListener implements TestExecutionListener {

  private static final Path OUT = Path.of("target", "junit-tags.jsonl");

  @Override
  public void executionFinished(TestIdentifier id, TestExecutionResult result) {
    // PIT runs the tests again against mutants; those results are not test results (gate 14).
    if (Boolean.getBoolean("ff.tags.off")
        || !id.isTest()
        || !(id.getSource().orElse(null) instanceof MethodSource source)) {
      return;
    }
    String tags =
        id.getTags().stream()
            .map(TestTag::getName)
            .map(TagRecordingListener::quote)
            .collect(Collectors.joining(","));
    String line =
        "{\"className\":%s,\"method\":%s,\"displayName\":%s,\"tags\":[%s],\"status\":%s}%n"
            .formatted(
                quote(source.getClassName()),
                quote(source.getMethodName()),
                quote(id.getDisplayName()),
                tags,
                quote(result.getStatus().name()));
    append(line);
  }

  private static synchronized void append(String line) {
    try {
      Files.createDirectories(OUT.getParent());
      Files.writeString(
          OUT, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new IllegalStateException("cannot write " + OUT, e);
    }
  }

  private static String quote(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
        }
      }
    }
    return b.append('"').toString();
  }
}
