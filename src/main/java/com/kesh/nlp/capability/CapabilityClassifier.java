package com.kesh.nlp.capability;

/**
 * Replaceable local intent model. Implementations must not decide permissions.
 */
public interface CapabilityClassifier {

    ClassificationResult classify(String text, DeskContext context);

    default String name() {
        return getClass().getSimpleName();
    }
}
