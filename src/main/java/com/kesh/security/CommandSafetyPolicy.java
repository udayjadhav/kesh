package com.kesh.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class CommandSafetyPolicy {

    private static final Pattern METACHARACTERS = Pattern.compile("[|&;`$()<>\\n\\r]");
    private static final List<String> DANGEROUS_SUBSTRINGS = List.of(
            "rm -rf",
            "rm -fr",
            "rm -r -f",
            "mkfs",
            "diskutil erase",
            "shutdown",
            "reboot",
            "halt",
            ":(){",
            "dd if=",
            "mkfs.",
            "format c:",
            "fork bomb"
    );
    private static final Set<String> DANGEROUS_GIT = Set.of("push", "reset", "clean", "rebase", "filter-branch");

    private final KeshProperties properties;

    public CommandSafetyPolicy(KeshProperties properties) {
        this.properties = properties;
    }

    public List<String> tokenizeAndValidate(String rawCommand) {
        if (!properties.getTerminal().isEnabled()) {
            throw new SecurityException("Terminal tool is disabled");
        }
        if (rawCommand == null || rawCommand.isBlank()) {
            throw new SecurityException("Command is required");
        }
        String command = rawCommand.trim();
        String lower = command.toLowerCase(Locale.ROOT);
        for (String dangerous : DANGEROUS_SUBSTRINGS) {
            if (lower.contains(dangerous)) {
                throw new SecurityException("Command rejected by safety policy: contains '" + dangerous + "'");
            }
        }
        if (METACHARACTERS.matcher(command).find()) {
            throw new SecurityException("Command rejected: shell metacharacters are not allowed");
        }
        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            throw new SecurityException("Command is required");
        }
        String binary = PathAllowlist.expand(tokens.get(0)).getFileName().toString();
        List<String> allowed = properties.getTerminal().getAllowedCommands();
        if (allowed.stream().noneMatch(item -> item.equalsIgnoreCase(binary))) {
            throw new SecurityException("Command '" + binary + "' is not on the terminal allowlist");
        }
        if ("git".equalsIgnoreCase(binary) && tokens.size() > 1) {
            String sub = tokens.get(1).toLowerCase(Locale.ROOT);
            if (DANGEROUS_GIT.contains(sub)) {
                throw new SecurityException("git " + sub + " is not allowed through run_command");
            }
        }
        return tokens;
    }

    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (inSingle || inDouble) {
            throw new SecurityException("Command rejected: unterminated quotes");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
