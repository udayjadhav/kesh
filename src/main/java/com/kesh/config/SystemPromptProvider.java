package com.kesh.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;

public class SystemPromptProvider {

    private final String template;

    public SystemPromptProvider(Resource resource) {
        try {
            this.template = resource.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load system prompt", e);
        }
    }

    public String template() {
        return template;
    }

    public String withTools(String toolsSection) {
        return withTools("", toolsSection);
    }

    public String withTools(String intro, String toolsSection) {
        StringBuilder sb = new StringBuilder();
        if (intro != null && !intro.isBlank()) {
            sb.append(intro.trim()).append("\n\n");
        }
        sb.append(template);
        if (toolsSection != null && !toolsSection.isBlank()) {
            sb.append("\n\n").append(toolsSection.trim());
        }
        return sb.toString();
    }
}
