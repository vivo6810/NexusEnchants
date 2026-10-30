package com.nexus.enchants.config;

import java.util.Objects;

/**
 * A single configuration finding produced while loading or validating.
 *
 * @param severity how serious the finding is
 * @param file     configuration file the finding belongs to
 * @param path     YAML path of the offending value
 * @param message  human readable explanation with the expected value
 */
public record ConfigIssue(Severity severity, String file, String path, String message) {

    /**
     * Validates the issue.
     *
     * @param severity how serious the finding is
     * @param file     configuration file name
     * @param path     YAML path
     * @param message  explanation
     */
    public ConfigIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Formats the issue for console output.
     *
     * @return a single line description
     */
    public String format() {
        return "[" + severity + "] " + file + " -> " + path + ": " + message;
    }

    /**
     * Severity of a configuration finding.
     */
    public enum Severity {
        /**
         * The affected feature cannot run and falls back to a safe default.
         */
        ERROR,
        /**
         * The value was accepted but behaves differently than the administrator may expect.
         */
        WARNING
    }
}
