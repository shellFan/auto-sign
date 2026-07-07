package com.autosign.controller;

import com.autosign.adapter.SignInResult;
import com.autosign.adapter.WeiboPostResult;
import com.autosign.dto.Result;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.AccountService;
import com.autosign.service.WeiboTopicService;
import com.autosign.adapter.WeiboSignInAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weibo/topic")
public class WeiboTopicController {

    @Autowired
    private WeiboTopicService topicService;

    @Autowired
    private WeiboSignInAdapter weiboSignInAdapter;

    @Autowired
    private AccountService accountService;

    @GetMapping("/list")
    public Result<List<WeiboSuperTopic>> list(HttpServletRequest request, Long accountId) {
        Long userId = (Long) request.getAttribute("userId");
        if (accountId != null) {
            return Result.success(topicService.listByAccountId(accountId));
        }
        return Result.success(topicService.listByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<WeiboSuperTopic> getById(@PathVariable Long id) {
        return Result.success(topicService.getById(id));
    }

    @PostMapping("/create")
    public Result<Void> create(HttpServletRequest request, @RequestBody WeiboSuperTopic topic) {
        Long userId = (Long) request.getAttribute("userId");
        topic.setUserId(userId);
        topicService.create(topic);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WeiboSuperTopic topic) {
        topic.setId(id);
        topicService.update(topic);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        topicService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        topicService.disable(id);
        return Result.success();
    }

    @PostMapping("/init-default")
    public Result<Void> initDefault(HttpServletRequest request, Long accountId) {
        Long userId = (Long) request.getAttribute("userId");
        topicService.initDefaultTopics(userId, accountId);
        return Result.success();
    }

    @PostMapping("/{id}/sign")
    public Result<SignInResult> sign(@PathVariable Long id) {
        WeiboSuperTopic topic = topicService.getById(id);
        if (topic == null) {
            return Result.error("超话不存在");
        }
        PlatformAccount account = accountService.getById(topic.getAccountId());
        if (account == null) {
            return Result.error("账号不存在");
        }
        if (account.getLoginStatus() != 1) {
            return Result.error("账号未登录，请先登录");
        }
        SignInResult result = weiboSignInAdapter.signTopic(account, topic);
        // 更新超话签到状态
        if (result.getStatus() == 2) {
            topicService.updateSignResult(id, 1);
        } else if (result.getStatus() == 6) {
            topicService.updateSignResult(id, 4);
        } else {
            topicService.updateSignResult(id, 3);
        }
        return Result.success(result);
    }

    @PostMapping("/{id}/post")
    public Result<WeiboPostResult> post(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            return Result.error("发帖内容不能为空");
        }
        WeiboSuperTopic topic = topicService.getById(id);
        if (topic == null) {
            return Result.error("超话不存在");
        }
        PlatformAccount account = accountService.getById(topic.getAccountId());
        if (account == null) {
            return Result.error("账号不存在");
        }
        if (account.getLoginStatus() != 1) {
            return Result.error("账号未登录，请先登录");
        }
        WeiboPostResult result = weiboSignInAdapter.postInTopic(account, topic, content);
        return Result.success(result);
    }

    @PostMapping("/{id}/reply")
    public Result<WeiboPostResult> reply(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            return Result.error("回复内容不能为空");
        }
        WeiboSuperTopic topic = topicService.getById(id);
        if (topic == null) {
            return Result.error("超话不存在");
        }
        PlatformAccount account = accountService.getById(topic.getAccountId());
        if (account == null) {
            return Result.error("账号不存在");
        }
        if (account.getLoginStatus() != 1) {
            return Result.error("账号未登录，请先登录");
        }
        WeiboPostResult result = weiboSignInAdapter.replyToRandomPost(account, topic, content);
        return Result.success(result);
    }
}
