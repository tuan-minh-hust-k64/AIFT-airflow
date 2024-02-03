package com.ikame.aift.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TranslationHelper {
    private final static Translate translate;

    static {
        try {
            translate = TranslateOptions.newBuilder().setProjectId("ikame-gem-ai-research")
                    .setCredentials(GoogleCredentials.fromStream(Objects.requireNonNull(TranslationHelper.class.getResourceAsStream("/genai_key.json")))).build().getService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final static String PROJECT_ID = "dino-team-329405";
    private final static String TARGET_LANGUAGE = "en";
    public List<String> translateTexts(List<String> texts) {
        List<Translation> translations = translate.translate(texts);
        return translations.stream().map(Translation::getTranslatedText).collect(Collectors.toList());
    }

}
