package com.autosign.service;

import com.autosign.entity.Article;
import com.autosign.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArticleService extends ServiceImpl<ArticleMapper, Article> {

    public List<Article> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .orderByDesc(Article::getUpdateTime));
    }

    public List<Article> listByStatus(Long userId, Integer status) {
        return list(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .eq(Article::getStatus, status)
                .orderByDesc(Article::getUpdateTime));
    }

    public Article create(Long userId, String title, String content, String summary) {
        Article article = new Article();
        article.setUserId(userId);
        article.setTitle(title);
        article.setContent(content);
        article.setSummary(summary);
        article.setStatus(0); // 草稿
        save(article);
        return article;
    }

    public Article updateContent(Long id, String title, String content, String summary) {
        Article article = getById(id);
        if (article == null) return null;
        article.setTitle(title);
        article.setContent(content);
        article.setSummary(summary);
        updateById(article);
        return article;
    }

    public Article publish(Long id) {
        Article article = getById(id);
        if (article == null) return null;
        article.setStatus(1);
        article.setPublishTime(LocalDateTime.now());
        updateById(article);
        return article;
    }

    public Article markPushedToWechat(Long id, String mediaId) {
        Article article = getById(id);
        if (article == null) return null;
        article.setStatus(2);
        article.setWechatMediaId(mediaId);
        updateById(article);
        return article;
    }
}
