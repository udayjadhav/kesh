package com.kesh.javaexec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a snippet or a class into a single Main.java compilation unit.
 */
public final class JavaSourcePreparer {

    private static final Pattern CLASS = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");

    private JavaSourcePreparer() {
    }

    public static String toMainClass(String source) {
        String body = source == null ? "" : source.strip();
        if (body.contains("class ") && body.contains("main(")) {
            Matcher matcher = CLASS.matcher(body);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (!"Main".equals(name)) {
                    body = body.replaceFirst("class\\s+" + Pattern.quote(name), "class Main");
                }
            }
            return body;
        }
        return """
                public final class Main {
                    public static void main(String[] args) {
                %s
                    }
                }
                """.formatted(indent(body));
    }

    private static String indent(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\\R", -1)) {
            sb.append("        ").append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
