package com.autosign.controller;

import com.autosign.dto.Result;
import com.autosign.entity.Article;
import com.autosign.service.ArticleService;
import com.autosign.service.WechatMpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/article")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private WechatMpService wechatMpService;

    @GetMapping("/list")
    public Result<List<Article>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(articleService.listByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<Article> getById(@PathVariable Long id) {
        Article article = articleService.getById(id);
        if (article == null) {
            return Result.error("文章不存在");
        }
        return Result.success(article);
    }

    @PostMapping("/create")
    public Result<Article> create(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = (Long) request.getAttribute("userId");
        String title = body.get("title");
        String content = body.get("content");
        String summary = body.get("summary");

        if (title == null || title.isEmpty()) {
            return Result.error("标题不能为空");
        }

        Article article = articleService.create(userId, title, content, summary);
        return Result.success(article);
    }

    @PutMapping("/{id}")
    public Result<Article> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        String summary = body.get("summary");

        Article article = articleService.updateContent(id, title, content, summary);
        if (article == null) {
            return Result.error("文章不存在");
        }
        return Result.success(article);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<Article> publish(@PathVariable Long id) {
        Article article = articleService.publish(id);
        if (article == null) {
            return Result.error("文章不存在");
        }
        return Result.success(article);
    }

    /**
     * 推送文章到微信公众号
     */
    @PostMapping("/{id}/push-wechat")
    public Result<Map<String, String>> pushToWechat(@PathVariable Long id) {
        try {
            Article article = articleService.getById(id);
            if (article == null) {
                return Result.error("文章不存在");
            }

            // 上传图文素材
            String mediaId = wechatMpService.uploadArticleMaterial(
                article.getTitle(),
                article.getContent(),
                article.getSummary() != null ? article.getSummary() : article.getTitle(),
                null // thumb_media_id，暂不支持封面图
            );

            // 发布到公众号
            String publishId = wechatMpService.publishArticle(mediaId);

            // 更新文章状态
            articleService.markPushedToWechat(id, mediaId);

            Map<String, String> result = new HashMap<>();
            result.put("mediaId", mediaId);
            result.put("publishId", publishId);
            result.put("message", "已推送到微信公众号");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("推送失败: " + e.getMessage());
        }
    }
}
