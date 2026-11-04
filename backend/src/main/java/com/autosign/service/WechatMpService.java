package com.autosign.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class WechatMpService {

    private static final Logger log = LoggerFactory.getLogger(WechatMpService.class);

    @Value("${wechat.mp.appid:}")
    private String appId;

    @Value("${wechat.mp.secret:}")
    private String appSecret;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpireTime = 0;

    /**
     * 获取 access_token
     */
    public String getAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        String urlStr = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String response = reader.lines().collect(java.util.stream.Collectors.joining());
        reader.close();

        JsonNode json = objectMapper.readTree(response);
        if (json.has("access_token")) {
            accessToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L; // 提前5分钟刷新
            log.info("获取微信 access_token 成功");
            return accessToken;
        }

        String errMsg = json.has("errmsg") ? json.get("errmsg").asText() : "unknown error";
        throw new Exception("获取 access_token 失败: " + errMsg);
    }

    /**
     * 上传图文消息素材（用于公众号发布）
     * @return media_id
     */
    public String uploadArticleMaterial(String title, String content, String digest, String thumbMediaId) throws Exception {
        String token = getAccessToken();
        String urlStr = "https://api.weixin.qq.com/cgi-bin/material/add_news?access_token=" + token;

        // 构建图文消息 JSON
        String articleJson = String.format(
            "{\"articles\":[{\"title\":\"%s\",\"author\":\"AutoSign\",\"digest\":\"%s\",\"content\":\"%s\",\"thumb_media_id\":\"%s\",\"need_open_comment\":1,\"only_fans_can_comment\":0}]}",
            escapeJson(title), escapeJson(digest), escapeJson(content), thumbMediaId);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(articleJson.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String response = reader.lines().collect(java.util.stream.Collectors.joining());
        reader.close();

        JsonNode json = objectMapper.readTree(response);
        if (json.has("media_id")) {
            String mediaId = json.get("media_id").asText();
            log.info("上传图文素材成功: media_id={}", mediaId);
            return mediaId;
        }

        String errMsg = json.has("errmsg") ? json.get("errmsg").asText() : "unknown error";
        throw new Exception("上传图文素材失败: " + errMsg);
    }

    /**
     * 发布图文消息到公众号
     * @return publish_id
     */
    public String publishArticle(String mediaId) throws Exception {
        String token = getAccessToken();
        String urlStr = "https://api.weixin.qq.com/cgi-bin/freepublish/submit?access_token=" + token;

        String body = "{\"media_id\":\"" + mediaId + "\"}";

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String response = reader.lines().collect(java.util.stream.Collectors.joining());
        reader.close();

        JsonNode json = objectMapper.readTree(response);
        if (json.has("publish_id")) {
            String publishId = json.get("publish_id").asText();
            log.info("发布文章成功: publish_id={}", publishId);
            return publishId;
        }

        String errMsg = json.has("errmsg") ? json.get("errmsg").asText() : "unknown error";
        throw new Exception("发布文章失败: " + errMsg);
    }

    /**
     * 上传图片素材（用于封面）
     * @return media_id
     */
    public String uploadImage(String imageUrl) throws Exception {
        // TODO: 下载图片并上传到微信
        // 这里简化处理，实际需要下载图片文件然后上传
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
