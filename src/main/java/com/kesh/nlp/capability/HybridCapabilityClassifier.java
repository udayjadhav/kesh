package com.kesh.nlp.capability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.DiscourseActs;

/**
 * Local ML first. Gemma structured JSON when confidence is not high.
 * Never decides allow/deny. Does not use keyword lexicons to pick a capability.
 */
@Component
@Primary
public class HybridCapabilityClassifier implements CapabilityClassifier {

    private static final Logger log = LoggerFactory.getLogger(HybridCapabilityClassifier.class);

    private final KeshProperties properties;
    private final OpenNlpCapabilityClassifier openNlp;
    private final ObjectProvider<RemoteSetFitClassifier> setFit;
    private final ObjectProvider<LlmIntentClassifierFallback> gemma;

    public HybridCapabilityClassifier(
            KeshProperties properties,
            OpenNlpCapabilityClassifier openNlp
    ) {
        this(properties, openNlp, unused(), unused());
    }

    @Autowired
    public HybridCapabilityClassifier(
            KeshProperties properties,
            OpenNlpCapabilityClassifier openNlp,
            ObjectProvider<RemoteSetFitClassifier> setFit,
            ObjectProvider<LlmIntentClassifierFallback> gemma
    ) {
        this.properties = properties;
        this.openNlp = openNlp;
        this.setFit = setFit;
        this.gemma = gemma;
    }

    private static <T> ObjectProvider<T> unused() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    @Override
    public String name() {
        return "hybrid";
    }

    @Override
    public ClassificationResult classify(String text, DeskContext context) {
        ClassificationResult ml = localClassify(text, context);
        double high = properties.getNlp().getClassifier().getHighConfidence();
        double medium = properties.getNlp().getClassifier().getMediumConfidence();
        int tokens = OpenNlpCapabilityClassifier.tokens(text).length;
        ClassificationResult merged = ml;
        boolean shortUtterance = tokens <= 2;
        if (!shortUtterance
                && properties.getNlp().getClassifier().isFallbackToLlm()
                && ml.confidence() < high) {
            LlmIntentClassifierFallback llm = gemma.getIfAvailable();
            if (llm != null) {
                ClassificationResult gemmaResult = llm.classify(text, context);
                if (ml.domain() == CapabilityDomain.LLM
                        || ml.domain() == CapabilityDomain.CHAT
                        || ml.domain() == CapabilityDomain.WRITING) {
                    merged = ClassificationResult.prefer(ml, gemmaResult);
                } else {
                    merged = ClassificationResult.merge(ml, gemmaResult);
                }
            }
        }
        if (tokens <= 2 && shouldLookupUnknown(text, merged)) {
            merged = ClassificationResult.single(
                    CapabilityIntent.WEB_SEARCH,
                    Math.max(merged.confidence(), 0.72),
                    OpenNlpCapabilityClassifier.entitiesFor(text, CapabilityIntent.WEB_SEARCH),
                    "short-lookup"
            );
        }
        if (properties.getNlp().getClassifier().isClarificationEnabled()
                && tokens > 2
                && competingCapabilities(merged)
                && merged.confidence() < medium) {
            ClassificationResult unclear = merged.asClarification(
                    "I am not sure what you want. Do you want a local machine check, a file/project look-up, a web search, or just an explanation?"
            );
            log.info("CLASSIFIER clarification domain={} intent={} confidence={}",
                    unclear.domain(), unclear.intent(), round(unclear.confidence()));
            return unclear;
        }
        log.info("CLASSIFIER source={} domain={} intent={} confidence={} intents={}",
                merged.source(), merged.domain(), merged.intent(), round(merged.confidence()), merged.intents().size());
        return merged;
    }

    private ClassificationResult localClassify(String text, DeskContext context) {
        String type = properties.getNlp().getClassifier().getType();
        if (type != null && "setfit".equalsIgnoreCase(type.trim())) {
            RemoteSetFitClassifier remote = setFit.getIfAvailable();
            if (remote != null) {
                try {
                    return remote.classify(text, context);
                } catch (RuntimeException e) {
                    log.info("SetFit sidecar unavailable, using OpenNLP: {}", e.getMessage());
                }
            }
        }
        return openNlp.classify(text, context);
    }

    /**
     * Ask only when two real capabilities are close. Short math and greetings are not a fork.
     */
    static boolean competingCapabilities(ClassificationResult result) {
        if (result == null || result.intents().size() < 2) {
            return false;
        }
        var first = result.intents().get(0);
        var second = result.intents().get(1);
        if (first.domain() == CapabilityDomain.CHAT || first.domain() == CapabilityDomain.LLM) {
            return false;
        }
        if (first.domain() == CapabilityDomain.WRITING || second.domain() == CapabilityDomain.WRITING) {
            return false;
        }
        if (first.domain() == second.domain()) {
            return false;
        }
        return Math.abs(first.confidence() - second.confidence()) < 0.18;
    }

    private static boolean shouldLookupUnknown(String text, ClassificationResult merged) {
        if (merged == null) {
            return false;
        }
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase().replaceAll("[^a-z\\s]", " ").strip().replaceAll("\\s+", " ");
        if (Set.of(
                "hi", "hello", "hey", "thanks", "thank you", "ok", "okay", "yo", "sup",
                "good morning", "good evening", "nice", "bye", "goodbye"
        ).contains(normalized) || DiscourseActs.isConfirmation(text)) {
            return false;
        }
        CapabilityDomain domain = merged.domain();
        if (domain == CapabilityDomain.WORK) {
            return OpenNlpCapabilityClassifier.tokens(text).length == 1;
        }
        if (domain == CapabilityDomain.WRITING || domain == CapabilityDomain.LLM) {
            return false;
        }
        return domain == CapabilityDomain.CHAT || domain == CapabilityDomain.RAG;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
