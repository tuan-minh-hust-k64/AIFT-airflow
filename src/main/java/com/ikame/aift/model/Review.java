package com.ikame.aift.model;

import com.ikame.aift.common.valueobject.ReviewStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class Review {
    private final String authorName;
    private final String reviewId;
    private final String textReview;
    private final int starRating;
    private final String reviewerLanguage;
    private final String device;
    private final String appVersionCode;
    private final String appVersionName;
    private String projectId;
    private String appPackageName;
    private final String deviceMetadata;
    private String reviewDate;
    private ReviewStatus reviewStatus;
    private String replyText;
    private String replyDate;
    private String labels;

    @Override
    public String toString() {
        return "Review{" +
                "authorName='" + authorName + '\'' +
                ", reviewId='" + reviewId + '\'' +
                ", textReview='" + textReview + '\'' +
                ", starRating=" + starRating +
                ", reviewerLanguage='" + reviewerLanguage + '\'' +
                ", device='" + device + '\'' +
                ", appVersionCode='" + appVersionCode + '\'' +
                ", appVersionName='" + appVersionName + '\'' +
                ", projectId='" + projectId + '\'' +
                ", appPackageName='" + appPackageName + '\'' +
                ", deviceMetadata='" + deviceMetadata + '\'' +
                ", reviewDate='" + reviewDate + '\'' +
                ", reviewStatus=" + reviewStatus +
                ", replyText='" + replyText + '\'' +
                ", replyDate='" + replyDate + '\'' +
                ", labels='" + labels + '\'' +
                '}';
    }
}
