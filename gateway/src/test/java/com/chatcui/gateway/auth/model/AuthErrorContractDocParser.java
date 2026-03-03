package com.chatcui.gateway.auth.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AuthErrorContractDocParser {
    private static final Pattern AUTH_CODE_PATTERN = Pattern.compile("`(AUTH_V1_[A-Z_]+)`");
    private static final Pattern REQUIRED_FIELD_PATTERN = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|[^|]*\\|\\s*yes\\s*\\|");

    private AuthErrorContractDocParser() {
    }

    static DocumentContract parse(Path repositoryRoot) {
        Path docPath = repositoryRoot.resolve("docs").resolve("auth").resolve("auth-error-contract.md");
        String markdown;
        try {
            markdown = Files.readString(docPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read auth error contract doc: " + docPath, e);
        }

        Set<String> codes = extractCodes(markdown);
        Set<String> requiredFields = extractRequiredEnvelopeFields(markdown);
        return new DocumentContract(codes, requiredFields, docPath);
    }

    private static Set<String> extractCodes(String markdown) {
        Set<String> codes = new LinkedHashSet<>();
        String section = section(markdown, "## 2. Code Registry", "## 3. HTTP and WebSocket Mapping");
        Matcher matcher = AUTH_CODE_PATTERN.matcher(section);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static Set<String> extractRequiredEnvelopeFields(String markdown) {
        Set<String> fields = new LinkedHashSet<>();
        String section = section(markdown, "## 1. Envelope", "## 2. Code Registry");
        String[] lines = section.split("\\R");
        for (String line : lines) {
            Matcher matcher = REQUIRED_FIELD_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                fields.add(matcher.group(1));
            }
        }
        return fields;
    }

    private static String section(String markdown, String startHeading, String endHeading) {
        int start = markdown.indexOf(startHeading);
        if (start < 0) {
            return "";
        }
        int end = markdown.indexOf(endHeading, start);
        if (end < 0) {
            end = markdown.length();
        }
        return markdown.substring(start, end);
    }

    record DocumentContract(Set<String> codes, Set<String> requiredFields, Path sourcePath) {
    }
}
