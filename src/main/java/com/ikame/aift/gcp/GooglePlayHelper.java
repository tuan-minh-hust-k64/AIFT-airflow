package com.ikame.aift.gcp;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.ikame.aift.common.valueobject.ReviewStatus;
import com.ikame.aift.utils.FunctionHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

@Slf4j
public class GooglePlayHelper {
    private final FunctionHelper functionHelper;

    private static final GoogleCredentials credentials;

    static {
        try {
            credentials = GoogleCredentials.fromStream(Objects.requireNonNull(GooglePlayHelper.class.getResourceAsStream("/google_play.json"))).createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
        } catch (IOException e) {
            log.error("ERROR: Read file credential failure, message: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static final AndroidPublisher androidPublisher;

    static {
        try {
            androidPublisher = new AndroidPublisher.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)

            ).build();
        } catch (GeneralSecurityException | IOException e) {
            log.error("ERROR: Init instance android publisher failure, message: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public GooglePlayHelper(FunctionHelper functionHelper) throws GeneralSecurityException, IOException {
        this.functionHelper = functionHelper;
    }

    public static List<com.ikame.aift.model.Review> getReviews(String appPackageName, String projectId) {
        List<Review> reviews = new java.util.ArrayList<>(List.of());
        ReviewsListResponse result = null;
        try {
            result = androidPublisher.reviews().list(appPackageName)
                    .setTranslationLanguage("en")
                    .execute();
            if (result.getReviews() != null) {
                reviews.addAll(result.getReviews());
                while (result.getTokenPagination() != null) {
                    result = androidPublisher.reviews().list(appPackageName)
                            .setToken(result.getTokenPagination().getNextPageToken())
                            .setTranslationLanguage("en")
                            .execute();
                    reviews.addAll(result.getReviews());
                }
                return reviews.stream().map(review -> {
                    UserComment userComment = review.getComments().get(0).getUserComment();
                    DeveloperComment developerComment = review.getComments().size() > 1? review.getComments().get(1).getDeveloperComment():null;
                    return com.ikame.aift.model.Review.builder()
                            .appPackageName(appPackageName)
                            .reviewId(review.getReviewId())
                            .appVersionCode(String.valueOf(userComment.getAppVersionCode()))
                            .device(userComment.getDevice())
                            .appVersionName(userComment.getAppVersionName())
                            .authorName(review.getAuthorName())
                            .reviewDate(FunctionHelper.epochSecondToZoneDateTime(userComment.getLastModified().getSeconds()))
                            .deviceMetadata(String.valueOf(userComment.getDeviceMetadata()))
                            .reviewStatus(developerComment == null ? ReviewStatus.NOT_REPLY : ReviewStatus.REPLIED)
                            .textReview(userComment.getText().replaceAll("[^a-zA-Z\\s]|[\\p{So}\t]+", ""))
                            .reviewerLanguage(userComment.getReviewerLanguage())
                            .replyDate(developerComment == null ? null : FunctionHelper.epochSecondToZoneDateTime(developerComment.getLastModified().getSeconds()))
                            .replyText(developerComment == null ? null : developerComment.getText().replaceAll("[^a-zA-Z\\s]|[\\p{So}\t]+", ""))
                            .starRating(userComment.getStarRating())
                            .projectId(projectId)
                            .build();
                }).toList();
            }
        } catch (IOException e) {
            log.error("Cannot get reviews of project: {}, package: {},\n Error: {}", projectId, appPackageName, e.getMessage());
        }
        return List.of();
    }
    public void replyReview() {
        try {
            ReviewsReplyResponse result = androidPublisher.reviews().reply("com.jura.baby.rope", "4e584b58-76b4-4e62-92e5-370c23c7437c", new ReviewsReplyRequest().setReplyText("Thank you for responding")).execute();
            log.info("Reply text: {}", result.getResult().getReplyText());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
