package com.ikame.aift.service;

import com.google.cloud.bigquery.InsertAllRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ikame.aift.common.valueobject.ReviewStatus;
import com.ikame.aift.gcp.BigqueryHelper;
import com.ikame.aift.gcp.GooglePlayHelper;
import com.ikame.aift.gcp.VertexAiHelper;
import com.ikame.aift.model.Review;
import com.ikame.aift.utils.FunctionHelper;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;

@Slf4j
public class ReviewService {
    private final static Gson GSON = new Gson();
    public static void main(String[] args) {
        List<Review> result = getReviewRealtime();
        log.info("INFO: reviews size {}", result.size());

    }


    public static List<Review> getReviewRealtime() {
        List<Review> reviewListTemp = new java.util.ArrayList<>(List.of());
        String contents;
        try (InputStream inputStream = ReviewService.class.getResourceAsStream("/data.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            contents = reader.lines()
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        List<Map<String, Object>> projectInfos = GSON.fromJson(contents, new TypeToken<List<Map<String, ?>>>() {
        }.getType());
        projectInfos.forEach(projectInfo -> {
            if (projectInfo.get("app_package_name") != null && projectInfo.get("project_id") != null) {
                List<Review> result = GooglePlayHelper.getReviews(projectInfo.get("app_package_name").toString(), projectInfo.get("project_id").toString());
                reviewListTemp.addAll(result);
            }
        });
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss v");
        AtomicReference<String> lastUpdateReview = new AtomicReference<>(ZonedDateTime.now(ZoneId.of("UTC")).format(formatter));
        BigqueryHelper.bigqueryCommand(
                "SELECT  COALESCE(MAX(review_date), TIMESTAMP(\"" + lastUpdateReview.get() + "\")) as review_date FROM `ikame-gem-ai-research.AIFT.reviews_data_center_processed`"
        ).getValues().forEach(row -> lastUpdateReview.set(row.get("review_date").getTimestampInstant().toString()));
        List<Review> reviewList = reviewListTemp.stream().filter(review ->
                ZonedDateTime.parse(review.getReviewDate(), formatter.withZone(ZoneId.of("UTC")))
                        .isAfter(ZonedDateTime.parse(lastUpdateReview.get()))).toList();
        reviewList.forEach(review -> {
            if(review.getTextReview().length() <=5) {
                review.setLabels("others");
                review.setReplyText(null);
            } else {
                review.setLabels(FunctionHelper.predictReviewCustomModel(review.getTextReview()));
                review.setReplyText(VertexAiHelper.generateReplyReviewPrompt(review.getTextReview()).orElse(""));
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("ERROR: sleep");
            }
        });
//        List<Review> reviewsBad = reviewList.stream().filter(item -> item.getStarRating() <= 3 && item.getTextReview().length() >= 6 ).toList();

        List<InsertAllRequest.RowToInsert> dataInsertBigquery = reviewList.stream().map(review -> {
            Map<String, Object> rowContent = new HashMap<>();
            rowContent.put("author_name", review.getAuthorName());
            rowContent.put("review_id", review.getReviewId());
            rowContent.put("text_review", review.getTextReview());
            rowContent.put("star_rating", review.getStarRating());
            rowContent.put("reviewer_language", review.getReviewerLanguage());
            rowContent.put("device", review.getDevice());
            rowContent.put("app_version_code", review.getAppVersionCode());
            rowContent.put("app_version_name", review.getAppVersionName());
            rowContent.put("project_id", review.getProjectId());
            rowContent.put("app_package_name", review.getAppPackageName());
            rowContent.put("device_meta_data", review.getDeviceMetadata());
            rowContent.put("review_date", review.getReviewDate());
            rowContent.put("review_status", review.getReviewStatus().name());
            rowContent.put("reply_text", review.getReplyText());
            rowContent.put("reply_date", review.getReplyDate());
            rowContent.put("labels", review.getLabels());
            return InsertAllRequest.RowToInsert.of(rowContent);
        }).toList();
        BigqueryHelper.tableInsertRows("AIFT", "reviews_data_center_processed", dataInsertBigquery);
        notifReviewSlack(reviewList, projectInfos);
        return reviewList;
    }

    private static void notifReviewSlack(List<Review> reviewsBad, List<Map<String, Object>> projectInfos) {
        Function<Review, String> classificationFunction = Review::getProjectId;
        Supplier<Map<String, List<Review>>> mapSupplier =  TreeMap::new;
        Map<String, List<Review>> reviewsGroupByProject = reviewsBad.stream().collect(Collectors.groupingBy(classificationFunction, mapSupplier, Collectors.toList()));
        reviewsGroupByProject.keySet().forEach(key -> {
            List<LayoutBlock> layoutBlocks = new ArrayList<>(List.of());
            List<Map<String, String>> statisticReviews = new ArrayList<>(List.of());
            List<BlockElement> statisticBlocks = new ArrayList<>(List.of(RichTextSectionElement.builder()
                    .elements(List.of(
                            RichTextSectionElement.Text.builder()
                                    .text("Có " + reviewsGroupByProject.get(key).size() + " review mới cho sản phẩm ")
                                    .build(),
                            RichTextSectionElement.Text.builder()
                                    .text(key + "\n")
                                    .style(RichTextSectionElement.TextStyle.builder().bold(true).build())
                                    .build()
                    ))
                    .build()));
            statisticReviews.addAll(List.of(
                    Map.of("label", "bugs", "count", String.valueOf(reviewsGroupByProject.get(key).stream().filter(item ->item.getLabels().contains("bugs")).count())),
                    Map.of("label", "ads", "count", String.valueOf(reviewsGroupByProject.get(key).stream().filter(item ->item.getLabels().contains("ads")).count())),
                    Map.of("label", "graphic", "count", String.valueOf(reviewsGroupByProject.get(key).stream().filter(item ->item.getLabels().contains("graphic")).count())),
                    Map.of("label", "gameplay", "count", String.valueOf(reviewsGroupByProject.get(key).stream().filter(item ->item.getLabels().contains("gameplay")).count())),
                    Map.of("label", "request", "count", String.valueOf(reviewsGroupByProject.get(key).stream().filter(item ->item.getLabels().contains("request")).count()))
            ));
            List<String> statisticReviewsContent = new ArrayList<>(List.of());
            statisticReviews.forEach(item -> {
                if(!item.get("count").equals("0")) {
                    statisticReviewsContent.add(item.get("count") + " review nói về *" + item.get("label") + "*");
                }
            });

            layoutBlocks.add(header(header -> header.text(new PlainTextObject(key, true))));
            layoutBlocks.add(RichTextBlock.builder()
                            .elements(statisticBlocks)
                    .build());
            if(statisticReviewsContent.size() > 0) {
                layoutBlocks.add(section(section -> section
                        .text(new MarkdownTextObject(String.join("; ", statisticReviewsContent), true))
                ));
            }
            layoutBlocks.add(divider());

            sortReviewByLabel(reviewsGroupByProject.get(key)).forEach(review -> {
                layoutBlocks.add(section(section -> section
                        .text(new MarkdownTextObject("Labels", true))
                        .accessory(CheckboxesElement.builder()
                                .options(
                                        Arrays.stream(review.getLabels().split(",")).map(label -> OptionObject.builder()
                                                .text(new MarkdownTextObject("`"+label.trim()+"`", true))
                                                .value(label)
                                                .build()).toList()
                                ).actionId("labels")
                                .build()).blockId("select_labels")
                ));
                layoutBlocks.add(InputBlock.builder()
                                .label(new PlainTextObject("Add new label (ads, bugs, request, graphic, positive, negative, gameplay, others, neutral)", true))
                                .element(PlainTextInputElement.builder()
                                        .actionId("add_new_label")
                                        .placeholder(new PlainTextObject("label1, label2,...", true))
                                        .build())
                                .blockId("new_label")
                        .build());
                layoutBlocks.add(ActionsBlock.builder()
                                .elements(List.of(
                                        ButtonElement.builder()
                                                .text(new PlainTextObject("Send", true))
                                                .value(review.getReviewId())
                                                .actionId("send_labels")
                                                .build()
                                ))
                                .blockId("send_labels")
                        .build());
                layoutBlocks.add(section(section -> section
                        .text(new MarkdownTextObject(generateReview(review), true))
                ));
                layoutBlocks.add(InputBlock.builder()
                                .blockId(review.getReviewId())
                                .element(PlainTextInputElement.builder()
                                        .actionId(review.getReviewId())
                                        .initialValue(review.getReplyText())
                                        .multiline(true)
                                        .build())
                                .label(new PlainTextObject("Bot reply:", true))
                        .build());
                layoutBlocks.add(ActionsBlock.builder()
                                .elements(List.of(ButtonElement.builder()
                                                .value(review.getReviewId()+"_"+review.getAppPackageName())
                                                .actionId("send_reply_action")
                                                .text(new PlainTextObject("Reply", true))
                                        .build()))
                                .blockId(review.getReviewId() + "_button")
                        .build());
                layoutBlocks.add(divider());
            });
            projectInfos.stream().filter(item -> item.get("project_id").toString().equals(key)).findAny()
                    .ifPresent(projectInfo -> ((List<String>) projectInfo.get("owners")).forEach(owner -> {
                SlackService.sendMessageDM(owner, layoutBlocks);
            }));

        });
    }

    private static List<Review> sortReviewByLabel(List<Review> reviews) {
        reviews.sort(new Comparator<Review>() {
            public int compare(Review s1, Review s2) {
                int s1Priority = getPriority(s1);
                int s2Priority = getPriority(s2);
                return s1Priority - s2Priority;
            }

            private int getPriority(Review s) {
                if (s.getLabels().contains("bugs")) {
                    return 1;
                } else if (s.getLabels().contains("ads")) {
                    return 2;
                } else if (s.getLabels().contains("gameplay")) {
                    return 3;
                }
                return 4;
            }
        });
        return reviews;
    }

    private static String generateReview(Review review) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss v");
        return "Review: *<https://www.google.com|" + review.getTextReview() + ">*\nRating: " +
                generateRatingReview(review.getStarRating()) +
                "\nVersion name: " + review.getAppVersionName() +
                "\nBy: " + review.getAuthorName() + ", on " +
                ZonedDateTime.parse(review.getReviewDate(), formatter.withZone(ZoneId.of("UTC"))).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    private static String generateRatingReview(int starRating) {
        return "★".repeat(Math.max(0, starRating));
    }
}
