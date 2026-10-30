package com.nexus.enchants.commands;

import java.util.List;

/**
 * Outcome of a configuration reload triggered by a command.
 *
 * @param success  whether the reload completed
 * @param errors   number of configuration errors found
 * @param warnings number of configuration warnings found
 * @param problems the error messages, for command output
 */
public record ReloadReport(boolean success, int errors, int warnings, List<String> problems) {

    /**
     * Validates the report.
     *
     * @param success  whether the reload completed
     * @param errors   error count
     * @param warnings warning count
     * @param problems error messages
     */
    public ReloadReport {
        problems = List.copyOf(problems);
    }
}
