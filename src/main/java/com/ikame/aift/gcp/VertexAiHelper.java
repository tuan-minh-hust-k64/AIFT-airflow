package com.ikame.aift.gcp;

import autovalue.shaded.com.google.common.collect.Lists;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1beta1.EndpointName;
import com.google.cloud.aiplatform.v1beta1.PredictResponse;
import com.google.cloud.aiplatform.v1beta1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1beta1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseStream;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class VertexAiHelper {
    public static Optional<String> generateReplyReviewPrompt(String reviewText) {
        String instance = "{ \"prompt\": " + "\"Write me a response to the following review maximum 25 words: \n" +
                "Review: " + reviewText +
                "\"}";
        String parameters =
                "{\n"
                        + "  \"temperature\": 0.9,\n"
                        + "  \"maxOutputTokens\": 256,\n"
                        + "  \"topP\": 0.8,\n"
                        + "  \"topK\": 40,\n"
                        + "  \"candidate_count\": 1"
                        + "}";
        String project = "763889829809";
        String location = "us-central1";
        String publisher = "google";
        String model = "1027605766342705152";

        try {
            return Optional.of(generateReplyReview(instance, parameters, project, location, publisher, model));
        } catch (IOException e) {
            log.error("Error: Cannot generate reply with review: {}, message: {}", reviewText, e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<String> predictMultiReview(String review) {
        String instance =
                "{ \"prompt\": " + "\"Define the categories for the text below?\n" +
                        "Options:\n" +
                        "- ads\n" +
                        "- bugs\n" +
                        "- graphic\n" +
                        "-gameplay\n" +
                        "-request\n" +
                        "-positive\n" +
                        "-negative\n" +
                        "-others\n" +
                        "-neutral\n" +

                        "\n" +
                        "Text: Pretty good game, the swing movement is flexible, not stiff and looks very cool. I really love the swinging movement. Battle movement animation still needs a retouch, the movement kinda stiff. I really hope this game be better from time to time, anyway it's a great game\n" +
                        "Categories: gameplay, graphic, positive, request\n" +
                        "\n" +
                        "Text: I liked this game, it's very cool, but the truth is, when you grab an ad to grab a power, it doesn't give you the power.\n" +
                        "Categories: gameplay, positive, bugs, ads, negative\n" +
                        "\n" +
                        "Text: In my opinion, this is the best game, only one minus is that the building is very small, if you fix it then there will be no questions about it, so the game is a tramp\n" +
                        "Categories: gameplay, positive, request\n" +
                        "\n" +
                        "Text: I hope they make a black viper game and when you fall you can choose the pose and add the spider web wings. Oh, remove the stupid missions and please fix the bugs with the poses and the slingshot because I can't do that\n" +
                        "Categories: gameplay, request, bugs, neutral\n" +
                        "\n" +
                        "Text: The game is cool, but: -make the map bigger -add a normal spider-man skin -make it possible to change the music -add more moves -make the enemies stronger\n" +
                        "Categories: gameplay, request, neutral\n" +
                        "\n" +
                        "Text: that game is good\n" +
                        "Categories: others, positive\n" +
                        "\n" +
                        "Text: I love this game\n" +
                        "Categories: others, positive\n" +
                        "\n" +
                        "Text: hello everyone\n" +
                        "Categories: others, neutral\n" +
                        "\n" +
                        "Text: " + review +
                        "Categories:\"}";
        String parameters =
                "{\n"
                        + "  \"temperature\": 0.2,\n"
                        + "  \"maxOutputTokens\": 1024,\n"
                        + "  \"topP\": 0.8,\n"
                        + "  \"topK\": 40,\n"
                        + "  \"candidate_count\": 1"
                        + "}";
        String project = "ikame-gem-ai-research";
        String location = "us-central1";
        String publisher = "google";
        String model = "text-bison@001";

        try {
            return Optional.of(predictTextPrompt(instance, parameters, project, location, publisher, model));
        } catch (IOException e) {
            log.error("Error: Cannot predict with review: {}, message: {}", review, e.getMessage());
            return Optional.empty();
        }
    }

    // Get a text prompt from a supported text model
    public static String predictTextPrompt(
            String instance,
            String parameters,
            String project,
            String location,
            String publisher,
            String model)
            throws IOException {
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        PredictionServiceSettings predictionServiceSettings =
                PredictionServiceSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider
                        .create(GoogleCredentials.fromStream(Objects.requireNonNull(
                                VertexAiHelper.class.getResourceAsStream("/genai_key.json"))).createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"))))
                        .setEndpoint(endpoint).build();

        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests.
        try {
            try (PredictionServiceClient predictionServiceClient =
                         PredictionServiceClient.create(predictionServiceSettings)) {
                final EndpointName endpointName =
                        EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, model);

                // Initialize client that will be used to send requests. This client only needs to be created
                // once, and can be reused for multiple requests.
                Value.Builder instanceValue = Value.newBuilder();
                JsonFormat.parser().merge(instance, instanceValue);
                List<Value> instances = new ArrayList<>();
                instances.add(instanceValue.build());

                // Use Value.Builder to convert instance to a dynamically typed value that can be
                // processed by the service.
                Value.Builder parameterValueBuilder = Value.newBuilder();
                JsonFormat.parser().merge(parameters, parameterValueBuilder);
                Value parameterValue = parameterValueBuilder.build();

                PredictResponse predictResponse =
                        predictionServiceClient.predict(endpointName, instances, parameterValue);
                return predictResponse.getPredictionsList().get(0).getStructValue()
                        .getFieldsMap().get("content").getStringValue().trim();
            }
        } catch (StatusRuntimeException | DeadlineExceededException e) {
            log.error("ERROR: DeadlineExceededException");
            return "";
        }
    }

    public static String generateReplyReview(
            String instance,
            String parameters,
            String project,
            String location,
            String publisher,
            String model)
            throws IOException {
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        PredictionServiceSettings predictionServiceSettings =
                PredictionServiceSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider
                                .create(GoogleCredentials.fromStream(Objects.requireNonNull(
                                        VertexAiHelper.class.getResourceAsStream("/genai_key.json"))).createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"))))
                        .setEndpoint(endpoint).build();

        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests.
        try {
            try (PredictionServiceClient predictionServiceClient =
                     PredictionServiceClient.create(predictionServiceSettings)) {
            final EndpointName endpointName =
                    EndpointName.ofProjectLocationEndpointName(project, location, model);

            // Initialize client that will be used to send requests. This client only needs to be created
            // once, and can be reused for multiple requests.
            Value.Builder instanceValue = Value.newBuilder();
            JsonFormat.parser().merge(instance, instanceValue);
            List<Value> instances = new ArrayList<>();
            instances.add(instanceValue.build());

            // Use Value.Builder to convert instance to a dynamically typed value that can be
            // processed by the service.
            Value.Builder parameterValueBuilder = Value.newBuilder();
            JsonFormat.parser().merge(parameters, parameterValueBuilder);
            Value parameterValue = parameterValueBuilder.build();

            PredictResponse predictResponse =
                    predictionServiceClient.predict(endpointName, instances, parameterValue);
            return predictResponse.getPredictionsList().get(0).getStructValue()
                    .getFieldsMap().get("content").getStringValue().trim();
        }
        } catch (StatusRuntimeException | DeadlineExceededException e) {
            log.error("ERROR: DeadlineExceededException");
            return "";
        }
    }
    public static void main(String[] args) throws IOException {
        try (VertexAI vertexAi = new VertexAI("ikame-gem-ai-research", "us-central1", GoogleCredentials.fromStream(Objects.requireNonNull(
                VertexAiHelper.class.getResourceAsStream("/genai_key.json"))).createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"))); ) {
            GenerationConfig generationConfig =
                    GenerationConfig.newBuilder()
                            .setMaxOutputTokens(2048)
                            .setTemperature(0.9F)
                            .setTopP(1)
                            .build();
            GenerativeModel model = new GenerativeModel("gemini-pro", generationConfig, vertexAi);

            List<Content> contents = new ArrayList<>();
            contents.add(Content.newBuilder().setRole("user").addParts(Part.newBuilder().setText("Viết chương trình bằng java đếm từ của 1 đoạn văn bản")).build());

            ResponseStream<GenerateContentResponse> responseStream = model.generateContentStream(contents);
            // Do something with the response
            responseStream.stream().forEach(item -> item.getCandidatesList().forEach(x -> x.getContent().getPartsList().forEach(y -> System.out.println(y.getText()))));
        }
    }

}