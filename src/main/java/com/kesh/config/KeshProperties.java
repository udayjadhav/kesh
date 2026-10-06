package com.kesh.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kesh")
public class KeshProperties {

    private final Llm llm = new Llm();
    private final Agent agent = new Agent();
    private final Filesystem filesystem = new Filesystem();
    private final Terminal terminal = new Terminal();
    private final Applications applications = new Applications();
    private final Integrations integrations = new Integrations();
    private final Nlp nlp = new Nlp();
    private final Workspace workspace = new Workspace();
    private final Rag rag = new Rag();
    private final Web web = new Web();
    private final JavaExecution javaExecution = new JavaExecution();
    private final Learning learning = new Learning();
    private final ModelRouterSettings modelRouter = new ModelRouterSettings();
    private final Writing writing = new Writing();
    private final Debug debug = new Debug();

    public Llm getLlm() {
        return llm;
    }

    public Agent getAgent() {
        return agent;
    }

    public Filesystem getFilesystem() {
        return filesystem;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Applications getApplications() {
        return applications;
    }

    public Integrations getIntegrations() {
        return integrations;
    }

    public Nlp getNlp() {
        return nlp;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public Rag getRag() {
        return rag;
    }

    public Web getWeb() {
        return web;
    }

    public JavaExecution getJavaExecution() {
        return javaExecution;
    }

    public Learning getLearning() {
        return learning;
    }

    public ModelRouterSettings getModelRouter() {
        return modelRouter;
    }

    public Writing getWriting() {
        return writing;
    }

    public Debug getDebug() {
        return debug;
    }

    public static class Llm {
        /**
         * ollama or docker-model-runner.
         */
        private String provider = "docker-model-runner";
        private String baseUrl = "http://localhost:12434";
        private String model = "docker.io/ai/gemma3:latest";
        private String chatPath = "/engines/v1/chat/completions";
        private int connectTimeoutMs = 10_000;
        private int requestTimeoutMs = 180_000;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getChatPath() {
            return chatPath;
        }

        public void setChatPath(String chatPath) {
            this.chatPath = chatPath;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }

    public static class Nlp {
        private boolean enabled = true;
        private final Classifier classifier = new Classifier();
        private final Context context = new Context();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Classifier getClassifier() {
            return classifier;
        }

        public Context getContext() {
            return context;
        }
    }

    public static class Classifier {
        private String type = "opennlp";
        private String model = "sentence-transformers/all-MiniLM-L6-v2";
        private String endpoint = "http://127.0.0.1:8091";
        private double highConfidence = 0.85;
        private double mediumConfidence = 0.55;
        private boolean fallbackToLlm = true;
        private boolean clarificationEnabled = true;
        private boolean judgeEnabled = true;
        private int judgeMaxRetries = 2;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public double getHighConfidence() {
            return highConfidence;
        }

        public void setHighConfidence(double highConfidence) {
            this.highConfidence = highConfidence;
        }

        public double getMediumConfidence() {
            return mediumConfidence;
        }

        public void setMediumConfidence(double mediumConfidence) {
            this.mediumConfidence = mediumConfidence;
        }

        public boolean isFallbackToLlm() {
            return fallbackToLlm;
        }

        public void setFallbackToLlm(boolean fallbackToLlm) {
            this.fallbackToLlm = fallbackToLlm;
        }

        public boolean isClarificationEnabled() {
            return clarificationEnabled;
        }

        public void setClarificationEnabled(boolean clarificationEnabled) {
            this.clarificationEnabled = clarificationEnabled;
        }

        public boolean isJudgeEnabled() {
            return judgeEnabled;
        }

        public void setJudgeEnabled(boolean judgeEnabled) {
            this.judgeEnabled = judgeEnabled;
        }

        public int getJudgeMaxRetries() {
            return judgeMaxRetries;
        }

        public void setJudgeMaxRetries(int judgeMaxRetries) {
            this.judgeMaxRetries = Math.max(0, judgeMaxRetries);
        }
    }

    public static class Context {
        private int maxTokens = 4_000;
        private int maxDocuments = 8;
        private int maxChunks = 12;
        private int maxToolResults = 8;

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getMaxDocuments() {
            return maxDocuments;
        }

        public void setMaxDocuments(int maxDocuments) {
            this.maxDocuments = maxDocuments;
        }

        public int getMaxChunks() {
            return maxChunks;
        }

        public void setMaxChunks(int maxChunks) {
            this.maxChunks = maxChunks;
        }

        public int getMaxToolResults() {
            return maxToolResults;
        }

        public void setMaxToolResults(int maxToolResults) {
            this.maxToolResults = maxToolResults;
        }
    }

    public static class Workspace {
        private String dataDir = System.getProperty("user.home") + "/.kesh";

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }
    }

    public static class Rag {
        private boolean enabled = true;
        private List<String> knowledgePaths = new ArrayList<>(List.of(
                System.getProperty("user.home") + "/.kesh/knowledge",
                System.getProperty("user.home") + "/Documents/Kesh",
                System.getProperty("user.home") + "/Kesh/README.md",
                System.getProperty("user.home") + "/Kesh/data"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getKnowledgePaths() {
            return knowledgePaths;
        }

        public void setKnowledgePaths(List<String> knowledgePaths) {
            this.knowledgePaths = knowledgePaths == null ? new ArrayList<>() : knowledgePaths;
        }
    }

    public static class Agent {
        private int maxIterations = 10;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }

    public static class Filesystem {
        private List<String> allowedPaths = new ArrayList<>();
        private long maxReadBytes = 524_288;
        private int maxSearchMatches = 50;
        private int maxListEntries = 200;

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }

        private List<String> personalPaths = new ArrayList<>();

        public List<String> getPersonalPaths() {
            return personalPaths;
        }

        public void setPersonalPaths(List<String> personalPaths) {
            this.personalPaths = personalPaths;
        }

        public long getMaxReadBytes() {
            return maxReadBytes;
        }

        public void setMaxReadBytes(long maxReadBytes) {
            this.maxReadBytes = maxReadBytes;
        }

        public int getMaxSearchMatches() {
            return maxSearchMatches;
        }

        public void setMaxSearchMatches(int maxSearchMatches) {
            this.maxSearchMatches = maxSearchMatches;
        }

        public int getMaxListEntries() {
            return maxListEntries;
        }

        public void setMaxListEntries(int maxListEntries) {
            this.maxListEntries = maxListEntries;
        }
    }

    public static class JavaExecution {
        private boolean enabled = true;
        private int timeoutMs = 3000;
        private int startGraceMs = 20_000;
        private String memoryLimit = "256m";
        private String cpuLimit = "0.5";
        private int maxOutputBytes = 32_768;
        private int maxSourceBytes = 16_384;
        private int maxConcurrentExecutions = 2;
        private String jdkVersion = "21";
        private String image = "eclipse-temurin:21-jdk";
        private String runtime = "auto";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getStartGraceMs() {
            return startGraceMs;
        }

        public void setStartGraceMs(int startGraceMs) {
            this.startGraceMs = startGraceMs;
        }

        public String getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        public String getCpuLimit() {
            return cpuLimit;
        }

        public void setCpuLimit(String cpuLimit) {
            this.cpuLimit = cpuLimit;
        }

        public int getMaxOutputBytes() {
            return maxOutputBytes;
        }

        public void setMaxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
        }

        public int getMaxSourceBytes() {
            return maxSourceBytes;
        }

        public void setMaxSourceBytes(int maxSourceBytes) {
            this.maxSourceBytes = maxSourceBytes;
        }

        public int getMaxConcurrentExecutions() {
            return maxConcurrentExecutions;
        }

        public void setMaxConcurrentExecutions(int maxConcurrentExecutions) {
            this.maxConcurrentExecutions = maxConcurrentExecutions;
        }

        public String getJdkVersion() {
            return jdkVersion;
        }

        public void setJdkVersion(String jdkVersion) {
            this.jdkVersion = jdkVersion;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getRuntime() {
            return runtime;
        }

        public void setRuntime(String runtime) {
            this.runtime = runtime;
        }
    }

    public static class Learning {
        private boolean enabled = true;
        private boolean async = true;
        private boolean autoTraining = false;
        private boolean requireApproval = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public boolean isAutoTraining() {
            return autoTraining;
        }

        public void setAutoTraining(boolean autoTraining) {
            this.autoTraining = autoTraining;
        }

        public boolean isRequireApproval() {
            return requireApproval;
        }

        public void setRequireApproval(boolean requireApproval) {
            this.requireApproval = requireApproval;
        }
    }

    public static class ModelRouterSettings {
        private boolean enabled = true;
        private boolean discover = true;
        private boolean refineEnabled = false;
        private boolean syncJudgeLlm = false;
        private String chatModel = "";
        private String codeModel = "";
        private String judgeModel = "";
        private String reasoningModel = "";
        private String refinerModel = "";
        private String writerModel = "";
        private String ollamaBaseUrl = "http://127.0.0.1:11434";
        private String dockerModelRunnerBaseUrl = "http://127.0.0.1:12434";
        private java.util.List<ModelSpec> models = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDiscover() {
            return discover;
        }

        public void setDiscover(boolean discover) {
            this.discover = discover;
        }

        public boolean isRefineEnabled() {
            return refineEnabled;
        }

        public void setRefineEnabled(boolean refineEnabled) {
            this.refineEnabled = refineEnabled;
        }

        public boolean isSyncJudgeLlm() {
            return syncJudgeLlm;
        }

        public void setSyncJudgeLlm(boolean syncJudgeLlm) {
            this.syncJudgeLlm = syncJudgeLlm;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getCodeModel() {
            return codeModel;
        }

        public void setCodeModel(String codeModel) {
            this.codeModel = codeModel;
        }

        public String getJudgeModel() {
            return judgeModel;
        }

        public void setJudgeModel(String judgeModel) {
            this.judgeModel = judgeModel;
        }

        public String getReasoningModel() {
            return reasoningModel;
        }

        public void setReasoningModel(String reasoningModel) {
            this.reasoningModel = reasoningModel;
        }

        public String getRefinerModel() {
            return refinerModel;
        }

        public void setRefinerModel(String refinerModel) {
            this.refinerModel = refinerModel;
        }

        public String getWriterModel() {
            return writerModel;
        }

        public void setWriterModel(String writerModel) {
            this.writerModel = writerModel;
        }

        public String getOllamaBaseUrl() {
            return ollamaBaseUrl;
        }

        public void setOllamaBaseUrl(String ollamaBaseUrl) {
            this.ollamaBaseUrl = ollamaBaseUrl;
        }

        public String getDockerModelRunnerBaseUrl() {
            return dockerModelRunnerBaseUrl;
        }

        public void setDockerModelRunnerBaseUrl(String dockerModelRunnerBaseUrl) {
            this.dockerModelRunnerBaseUrl = dockerModelRunnerBaseUrl;
        }

        public java.util.List<ModelSpec> getModels() {
            return models;
        }

        public void setModels(java.util.List<ModelSpec> models) {
            this.models = models == null ? new ArrayList<>() : models;
        }

        public String resolvedOllamaBaseUrl() {
            return ollamaBaseUrl == null || ollamaBaseUrl.isBlank() ? "http://127.0.0.1:11434" : ollamaBaseUrl;
        }

        public String resolvedDockerModelRunnerBaseUrl() {
            return dockerModelRunnerBaseUrl == null || dockerModelRunnerBaseUrl.isBlank()
                    ? "http://127.0.0.1:12434"
                    : dockerModelRunnerBaseUrl;
        }
    }

    public static class ModelSpec {
        private String id = "";
        private String model = "";
        private String provider = "";
        private String runtime = "";
        private String baseUrl = "";
        private java.util.List<String> roles = new ArrayList<>();
        private int contextSize;
        private int expectedLatencyMs;
        private String memory = "";
        private String parameterSize = "";
        private boolean tools;
        private boolean enabled = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getRuntime() {
            return runtime;
        }

        public void setRuntime(String runtime) {
            this.runtime = runtime;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public java.util.List<String> getRoles() {
            return roles;
        }

        public void setRoles(java.util.List<String> roles) {
            this.roles = roles == null ? new ArrayList<>() : roles;
        }

        public int getContextSize() {
            return contextSize;
        }

        public void setContextSize(int contextSize) {
            this.contextSize = contextSize;
        }

        public int getExpectedLatencyMs() {
            return expectedLatencyMs;
        }

        public void setExpectedLatencyMs(int expectedLatencyMs) {
            this.expectedLatencyMs = expectedLatencyMs;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getParameterSize() {
            return parameterSize;
        }

        public void setParameterSize(String parameterSize) {
            this.parameterSize = parameterSize;
        }

        public boolean isTools() {
            return tools;
        }

        public void setTools(boolean tools) {
            this.tools = tools;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Terminal {
        private boolean enabled = false;
        private int timeoutSeconds = 30;
        private List<String> allowedCommands = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public List<String> getAllowedCommands() {
            return allowedCommands;
        }

        public void setAllowedCommands(List<String> allowedCommands) {
            this.allowedCommands = allowedCommands;
        }
    }

    public static class Applications {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Integrations {
        private final Slack slack = new Slack();
        private final Atlassian jira = new Atlassian();
        private final Atlassian confluence = new Atlassian();
        private final Gmail gmail = new Gmail();
        private final GitHub github = new GitHub();

        public Slack getSlack() {
            return slack;
        }

        public Atlassian getJira() {
            return jira;
        }

        public Atlassian getConfluence() {
            return confluence;
        }

        public Gmail getGmail() {
            return gmail;
        }

        public GitHub getGithub() {
            return github;
        }
    }

    public static class Slack {
        private boolean enabled = false;
        private String botToken = "";
        private String userToken = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getUserToken() {
            return userToken;
        }

        public void setUserToken(String userToken) {
            this.userToken = userToken;
        }

        public boolean isConfigured() {
            return enabled && notBlank(botToken) && notBlank(userToken);
        }
    }

    public static class Atlassian {
        private boolean enabled = false;
        private String baseUrl = "";
        private String email = "";
        private String apiToken = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public boolean isConfigured() {
            return enabled && notBlank(baseUrl) && notBlank(email) && notBlank(apiToken);
        }
    }

    public static class Gmail {
        private boolean enabled = false;
        private String clientId = "";
        private String clientSecret = "";
        private String refreshToken = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public boolean isConfigured() {
            return enabled && notBlank(clientId) && notBlank(clientSecret) && notBlank(refreshToken);
        }
    }

    public static class GitHub {
        private boolean enabled = false;
        private String token = "";
        private String apiUrl = "https://api.github.com";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public boolean isConfigured() {
            return enabled && notBlank(token);
        }
    }

    public static class Web {
        private boolean enabled = true;
        private int maxContentChars = 30_000;
        private String userAgent = "KeshLocalAssistant/0.1";
        private final Search search = new Search();
        private final Fetch fetch = new Fetch();
        private final Crawl crawl = new Crawl();
        private final Cache cache = new Cache();
        private final RateLimit rateLimit = new RateLimit();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxContentChars() {
            return maxContentChars;
        }

        public void setMaxContentChars(int maxContentChars) {
            this.maxContentChars = maxContentChars;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public Search getSearch() {
            return search;
        }

        public Fetch getFetch() {
            return fetch;
        }

        public Crawl getCrawl() {
            return crawl;
        }

        public Cache getCache() {
            return cache;
        }

        public RateLimit getRateLimit() {
            return rateLimit;
        }

        public boolean searchAvailable() {
            return enabled && search.enabled;
        }

        public boolean fetchAvailable() {
            return enabled && fetch.enabled;
        }

        public boolean crawlAvailable() {
            return enabled && crawl.enabled && fetch.enabled;
        }
    }

    public static class Search {
        private boolean enabled = true;
        private String provider = "duckduckgo";
        private String apiKey = "";
        private int maxResults = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class Fetch {
        private boolean enabled = true;
        private int timeoutSeconds = 15;
        private int maxResponseSizeMb = 5;
        private int maxRedirects = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxResponseSizeMb() {
            return maxResponseSizeMb;
        }

        public void setMaxResponseSizeMb(int maxResponseSizeMb) {
            this.maxResponseSizeMb = maxResponseSizeMb;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        public int maxResponseBytes() {
            return Math.max(64_000, maxResponseSizeMb * 1024 * 1024);
        }
    }

    public static class Crawl {
        private boolean enabled = true;
        private int maxPages = 10;
        private int maxDepth = 2;
        private boolean sameDomainOnly = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isSameDomainOnly() {
            return sameDomainOnly;
        }

        public void setSameDomainOnly(boolean sameDomainOnly) {
            this.sameDomainOnly = sameDomainOnly;
        }
    }

    public static class Cache {
        private boolean enabled = true;
        private int ttlMinutes = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class RateLimit {
        private int requestsPerDomainPerMinute = 30;

        public int getRequestsPerDomainPerMinute() {
            return requestsPerDomainPerMinute;
        }

        public void setRequestsPerDomainPerMinute(int requestsPerDomainPerMinute) {
            this.requestsPerDomainPerMinute = requestsPerDomainPerMinute;
        }
    }

    public static class Writing {
        private boolean enabled = true;
        private String language = "en-US";
        private boolean llmVerifyCorrections = false;
        private boolean semanticCheck = true;
        private double semanticThreshold = 0.72;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public boolean isLlmVerifyCorrections() {
            return llmVerifyCorrections;
        }

        public void setLlmVerifyCorrections(boolean llmVerifyCorrections) {
            this.llmVerifyCorrections = llmVerifyCorrections;
        }

        public boolean isSemanticCheck() {
            return semanticCheck;
        }

        public void setSemanticCheck(boolean semanticCheck) {
            this.semanticCheck = semanticCheck;
        }

        public double getSemanticThreshold() {
            return semanticThreshold;
        }

        public void setSemanticThreshold(double semanticThreshold) {
            this.semanticThreshold = semanticThreshold;
        }
    }

    public static class Debug {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
