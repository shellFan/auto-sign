package com.autosign.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ActivityTemplateSupport {
    private static final String DEFAULT_POSTS = "今天来{topic}超话打卡，分享一份喜欢。\n关注{topic}的每一天都有新收获。";
    private static final String DEFAULT_REPLIES = "支持{topic}，感谢分享。\n关于{topic}的内容很精彩。\n一起关注{topic}。";

    private ActivityTemplateSupport() {
    }

    public static ActivityTemplates defaults(Long userId, Long topicId) {
        ActivityTemplates templates = new ActivityTemplates();
        templates.setUserId(userId);
        templates.setTopicId(topicId);
        templates.setPostTemplates(DEFAULT_POSTS);
        templates.setReplyTemplates(DEFAULT_REPLIES);
        return templates;
    }

    public static String render(String multiline, String topicName, Random random) {
        List<String> alternatives = alternatives(multiline);
        if (alternatives.isEmpty()) {
            return "";
        }
        String selected = alternatives.get(random.nextInt(alternatives.size()));
        return selected.replace("{topic}", topicName == null ? "当前超话" : topicName);
    }

    public static boolean validate(String multiline) {
        return !alternatives(multiline).isEmpty();
    }

    private static List<String> alternatives(String multiline) {
        List<String> result = new ArrayList<>();
        if (multiline == null) {
            return result;
        }
        for (String line : multiline.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
