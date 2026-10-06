package com.kesh.nlp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;

public final class TrainingSetLoader {

    private TrainingSetLoader() {
    }

    public static List<LabeledExample> load(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            List<LabeledExample> examples = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) {
                    continue;
                }
                examples.add(new LabeledExample(line.substring(0, tab).trim(), line.substring(tab + 1).trim()));
            }
            if (examples.isEmpty()) {
                throw new IllegalStateException("No training rows in " + resource.getFilename());
            }
            return List.copyOf(examples);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load NLP training set " + resource, e);
        }
    }
}
