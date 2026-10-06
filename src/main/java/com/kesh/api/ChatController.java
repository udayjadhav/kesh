package com.kesh.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.kesh.agent.AgentEvent;
import com.kesh.agent.AgentOrchestrator;
import com.kesh.agent.AgentResponse;
import com.kesh.agent.ConversationContext;
import com.kesh.agent.ConversationStore;
import com.kesh.api.dto.ChatRequest;
import com.kesh.api.dto.ConversationSummary;
import com.kesh.api.dto.HealthResponse;
import com.kesh.api.dto.ToolStatusView;
import com.kesh.config.KeshProperties;
import com.kesh.llm.LlmClient;
import com.kesh.nlp.RequestAnalyzer;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.persistence.DeskPreferenceStore;
import com.kesh.tools.ToolRegistry;
import com.kesh.workspace.Workspace;
import com.kesh.workspace.WorkspaceMode;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/{desk:work|personal|professional}/api")
public class ChatController {

    private final AgentOrchestrator orchestrator;
    private final ConversationStore conversationStore;
    private final TaskExecutor keshTaskExecutor;
    private final KeshProperties properties;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final RequestAnalyzer requestAnalyzer;
    private final DeskPreferenceStore preferences;
    private final com.kesh.llm.ModelCatalog modelCatalog;

    public ChatController(
            AgentOrchestrator orchestrator,
            ConversationStore conversationStore,
            TaskExecutor keshTaskExecutor,
            KeshProperties properties,
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            RequestAnalyzer requestAnalyzer,
            DeskPreferenceStore preferences,
            com.kesh.llm.ModelCatalog modelCatalog
    ) {
        this.orchestrator = orchestrator;
        this.conversationStore = conversationStore;
        this.keshTaskExecutor = keshTaskExecutor;
        this.properties = properties;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.requestAnalyzer = requestAnalyzer;
        this.preferences = preferences;
        this.modelCatalog = modelCatalog;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@PathVariable String desk, @Valid @RequestBody ChatRequest request) {
        WorkspaceMode mode = mode(desk);
        AgentResponse response = orchestrator.handle(mode, request.conversationId(), request.message(), null);
        return response.toClientMap();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable String desk, @Valid @RequestBody ChatRequest request) {
        WorkspaceMode mode = mode(desk);
        SseEmitter emitter = new SseEmitter(300_000L);
        keshTaskExecutor.execute(() -> {
            Workspace.set(mode);
            try {
                orchestrator.handle(mode, request.conversationId(), request.message(), event -> send(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, AgentEvent.error(e.getMessage()));
                emitter.completeWithError(e);
            } finally {
                Workspace.clear();
            }
        });
        return emitter;
    }

    @PostMapping(value = "/approvals/{id}/approve/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter approveStream(@PathVariable String desk, @PathVariable String id) {
        WorkspaceMode mode = mode(desk);
        SseEmitter emitter = new SseEmitter(300_000L);
        keshTaskExecutor.execute(() -> {
            Workspace.set(mode);
            try {
                orchestrator.approve(mode, id, event -> send(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, AgentEvent.error(e.getMessage()));
                emitter.completeWithError(e);
            } finally {
                Workspace.clear();
            }
        });
        return emitter;
    }

    @PostMapping(value = "/approvals/{id}/reject/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rejectStream(@PathVariable String desk, @PathVariable String id) {
        WorkspaceMode mode = mode(desk);
        SseEmitter emitter = new SseEmitter(300_000L);
        keshTaskExecutor.execute(() -> {
            Workspace.set(mode);
            try {
                orchestrator.reject(mode, id, event -> send(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                send(emitter, AgentEvent.error(e.getMessage()));
                emitter.completeWithError(e);
            } finally {
                Workspace.clear();
            }
        });
        return emitter;
    }

    @PostMapping("/approvals/{id}/approve")
    public Map<String, Object> approve(@PathVariable String desk, @PathVariable String id) {
        return orchestrator.approve(mode(desk), id, null).toClientMap();
    }

    @PostMapping("/approvals/{id}/reject")
    public Map<String, Object> reject(@PathVariable String desk, @PathVariable String id) {
        return orchestrator.reject(mode(desk), id, null).toClientMap();
    }

    @GetMapping("/health")
    public HealthResponse health(@PathVariable String desk) {
        mode(desk);
        com.kesh.llm.RegisteredModel active = modelCatalog == null ? null : modelCatalog.firstEnabled();
        return new HealthResponse(
                "ok",
                active == null ? llmClient.providerName() : active.provider(),
                active == null ? properties.getLlm().getModel() : active.model(),
                active == null ? properties.getLlm().getBaseUrl() : active.baseUrl()
        );
    }

    @GetMapping("/models")
    public Map<String, Object> models(@PathVariable String desk) {
        mode(desk);
        modelCatalog.refresh();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("defaultModel", properties.getLlm().getModel());
        map.put("defaultProvider", properties.getLlm().getProvider());
        map.put("routerEnabled", properties.getModelRouter().isEnabled());
        map.put("discover", properties.getModelRouter().isDiscover());
        map.put("refineEnabled", properties.getModelRouter().isRefineEnabled());
        map.put("models", modelCatalog.models().stream().map(com.kesh.llm.RegisteredModel::toMap).toList());
        return map;
    }

    @GetMapping("/config")
    public Map<String, Object> config(@PathVariable String desk) {
        WorkspaceMode mode = mode(desk);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("desk", mode.path());
        map.put("deskLabel", mode.label());
        map.put("provider", properties.getLlm().getProvider());
        map.put("model", properties.getLlm().getModel());
        map.put("baseUrl", properties.getLlm().getBaseUrl());
        map.put("maxIterations", properties.getAgent().getMaxIterations());
        map.put("terminalEnabled", properties.getTerminal().isEnabled());
        map.put("nlpEnabled", properties.getNlp().isEnabled());
        map.put("ragEnabled", properties.getRag().isEnabled());
        map.put("debugEnabled", properties.getDebug().isEnabled());
        return map;
    }

    @GetMapping("/preference")
    public Map<String, Object> preference(@PathVariable String desk) {
        WorkspaceMode mode = mode(desk);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("desk", mode.path());
        map.put("note", preferences.note(mode));
        return map;
    }

    @PutMapping("/preference")
    public Map<String, Object> savePreference(@PathVariable String desk, @RequestBody Map<String, Object> body) {
        WorkspaceMode mode = mode(desk);
        Object note = body == null ? null : body.get("note");
        preferences.save(mode, note == null ? "" : note.toString());
        return preference(desk);
    }

    @PostMapping("/nlp/check")
    public Map<String, Object> nlpCheck(@PathVariable String desk, @Valid @RequestBody ChatRequest request) {
        WorkspaceMode mode = mode(desk);
        DeskContext deskContext = DeskContext.of(mode);
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            ConversationContext context = conversationStore.find(mode, request.conversationId());
            if (context != null) {
                deskContext = DeskContext.of(
                        mode,
                        context.lastUserMessage(),
                        context.lastAssistantMessage(),
                        context.conversationState()
                );
            }
        }
        return requestAnalyzer.toClientMap(
                requestAnalyzer.analyze(mode, request.message(), toolRegistry.forMode(mode), deskContext)
        );
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> conversations(@PathVariable String desk) {
        return conversationStore.recent(mode(desk));
    }

    @GetMapping("/conversations/{id}")
    public Map<String, Object> conversation(@PathVariable String desk, @PathVariable String id) {
        ConversationContext context = conversationStore.find(mode(desk), id);
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown conversation");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", context.id());
        map.put("title", context.title());
        map.put("desk", context.mode().path());
        map.put("messages", context.transcript());
        return map;
    }

    @GetMapping("/tools")
    public List<ToolStatusView> tools(@PathVariable String desk) {
        WorkspaceMode mode = mode(desk);
        List<ToolStatusView> views = new ArrayList<>();
        views.add(new ToolStatusView("Desk", "on", mode.label() + " — chats and files stay here"));
        views.add(new ToolStatusView("Files", "on", "list, read, search inside this desk's folders"));
        views.add(new ToolStatusView(
                "Applications",
                properties.getApplications().isEnabled() ? "on" : "off",
                "macOS open / quit / focus"
        ));
        views.add(new ToolStatusView(
                "Terminal",
                properties.getTerminal().isEnabled() ? "on" : "off",
                "disabled by default"
        ));
        views.add(new ToolStatusView(
                "Java snippets",
                properties.getJavaExecution().isEnabled() ? "on" : "off",
                "isolated JVM Gemma may use for exact math, ranking, or formatting; approval required"
        ));
        views.add(new ToolStatusView(
                "Runtime versions",
                registered(mode, "inspect_runtime") ? "on" : "off",
                "java -version, node -v, Cursor app, macOS sw_vers"
        ));
        views.add(new ToolStatusView(
                "Web Search",
                on(properties.getWeb().searchAvailable() && registered(mode, "web_search")),
                "public search, read-only"
        ));
        views.add(new ToolStatusView(
                "Web Fetch",
                on(properties.getWeb().fetchAvailable() && registered(mode, "web_fetch")),
                "fetch one public page, strip HTML"
        ));
        views.add(new ToolStatusView(
                "Web Crawl",
                on(properties.getWeb().crawlAvailable() && registered(mode, "web_crawl")),
                "same-domain, max pages and depth, robots.txt"
        ));
        views.add(new ToolStatusView("Wikipedia", registered(mode, "wikipedia_search") ? "on" : "off", "lookup facts"));
        views.add(new ToolStatusView(
                "Dictionary",
                registered(mode, "dictionary_lookup") ? "on" : "off",
                "word lookup"
        ));
        views.add(new ToolStatusView(
                "Stack Exchange",
                registered(mode, "stackexchange_search") ? "on" : "off",
                "programming Q&A search"
        ));
        views.add(new ToolStatusView(
                "Slack",
                configured(properties.getIntegrations().getSlack().isConfigured()),
                "needs Slack app bot token + user token"
        ));
        views.add(new ToolStatusView(
                "Jira",
                configured(properties.getIntegrations().getJira().isConfigured()),
                "needs site URL + Atlassian email + API token"
        ));
        views.add(new ToolStatusView(
                "Gmail",
                configured(properties.getIntegrations().getGmail().isConfigured()),
                "needs Google OAuth client id, secret, refresh token"
        ));
        views.add(new ToolStatusView(
                "Confluence",
                configured(properties.getIntegrations().getConfluence().isConfigured()),
                "needs site URL + Atlassian email + API token"
        ));
        views.add(new ToolStatusView(
                "GitHub",
                configured(properties.getIntegrations().getGithub().isConfigured()),
                "needs GitHub personal access token"
        ));
        views.add(new ToolStatusView(
                "Saved chats",
                properties.getRag().isEnabled() ? "on" : "off",
                "recent chats on this desk only"
        ));
        views.add(new ToolStatusView(
                "Request classifiers",
                properties.getNlp().isEnabled() ? "on" : "off",
                "local Naive Bayes intent + risk check"
        ));
        views.add(new ToolStatusView(
                "Registered tools",
                "on",
                toolRegistry.forMode(mode).size() + " tools on this desk"
        ));
        return views;
    }

    private static WorkspaceMode mode(String desk) {
        try {
            return WorkspaceMode.fromPath(desk);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static String configured(boolean ready) {
        return ready ? "on" : "off";
    }

    private static String on(boolean ready) {
        return ready ? "on" : "off";
    }

    private boolean registered(WorkspaceMode mode, String toolName) {
        return toolRegistry.forMode(mode).stream().anyMatch(tool -> toolName.equals(tool.name()));
    }

    private static void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
