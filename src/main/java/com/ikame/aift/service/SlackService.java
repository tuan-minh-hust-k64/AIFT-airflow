package com.ikame.aift.service;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.PlainTextInputElement;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class SlackService {
    private static final String userOauthToken = "xoxp-4127893326450-5988206214897-6494242975744-d46ffb39e477a97f68d49ed65045d82d";
    private static final String botUserOauthToken = "xoxb-4127893326450-6464823064502-MjCSNrJcswiZQaJ7gOKsH9bk";
    private static final Slack slack = Slack.getInstance();
    public static void main(String[] args) {
        sendMessageDM("minhvt@ikameglobal.com", List.of());
    }
    public static void sendMessageDM(String email, List<LayoutBlock> content){
        String userSlackId = getUserSlackIdByEmail(email);
        log.info(email);
        try {
            ChatPostMessageResponse response = slack.methods(botUserOauthToken).chatPostMessage(req -> req
                    .channel(userSlackId)
                    .blocks(content)
            );
        } catch (IOException | SlackApiException e) {
            log.error("Send DM Slack error with userSlackId: {}, content: {}, error: {}", userSlackId, content, e.getMessage());
        }
    }
    public static String getUserSlackIdByEmail(String email) {
        try {
            UsersLookupByEmailResponse response = slack.methods(botUserOauthToken).usersLookupByEmail(UsersLookupByEmailRequest.builder()
                    .email(email)
                    .build());
            return response.getUser().getId();
        } catch (IOException | SlackApiException e) {
            log.error("Get user slack id by email: {}, error: {}", email, e.getMessage());
        }
        return null;
    }
}
