package com.kesh.javaexec;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Extra source filter. The container is the security boundary; this only rejects obvious escapes.
 */
public final class JavaSnippetSourceGuard {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(Runtime\\.getRuntime|ProcessBuilder|ProcessHandle|java\\.net|"
                    + "javax\\.net|java\\.net\\.http|HttpClient|URLConnection|Socket|ServerSocket|"
                    + "java\\.nio\\.file|new\\s+File\\s*\\(|FileInputStream|FileOutputStream|"
                    + "RandomAccessFile|FileReader|FileWriter|System\\.getenv|System\\.getProperties|"
                    + "System\\.setProperty|System\\.load|System\\.loadLibrary|sun\\.misc\\.Unsafe|"
                    + "jdk\\.internal|Class\\.forName|setAccessible|MethodHandles|"
                    + "Native\\.|JNI|docker\\.sock|/etc/passwd|/Users/|\\.ssh)"
    );

    private JavaSnippetSourceGuard() {
    }

    public static void validate(String source, int maxSourceBytes) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Java source is required.");
        }
        byte[] bytes = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxSourceBytes) {
            throw new IllegalArgumentException("Java source is larger than " + maxSourceBytes + " bytes.");
        }
        if (FORBIDDEN.matcher(source).find()) {
            throw new SecurityException(
                    "This Java snippet is not allowed. The sandbox has no network, host files, "
                            + "environment, or process access. Use Kesh web or file tools instead."
            );
        }
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.contains("native") && lower.contains("library")) {
            throw new SecurityException("Native libraries are not allowed in Java snippets.");
        }
    }
}
