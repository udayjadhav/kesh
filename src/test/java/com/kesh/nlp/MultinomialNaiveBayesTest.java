package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MultinomialNaiveBayesTest {

    @Test
    void predictsTheTrainedLabel() {
        MultinomialNaiveBayes model = new MultinomialNaiveBayes();
        model.train(List.of(
                new LabeledExample("CHAT", "hello how are you"),
                new LabeledExample("CHAT", "what is kafka"),
                new LabeledExample("FILES", "read the file"),
                new LabeledExample("FILES", "list the directory")
        ));
        assertThat(model.predict("hello there").label()).isEqualTo("CHAT");
        assertThat(model.predict("read this file please").label()).isEqualTo("FILES");
        assertThat(model.predict("read this file please").confidence()).isGreaterThan(0.5);
    }
}
