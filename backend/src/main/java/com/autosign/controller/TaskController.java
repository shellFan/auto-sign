package com.autosign.controller;

import com.autosign.activity.ActivityService;
import com.autosign.activity.ActivityTask;
import com.autosign.activity.ActivityTemplates;
import com.autosign.dto.Result;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/task")
public class TaskController {
    private final ActivityService activities;

    public TaskController(ActivityService activities) { this.activities = activities; }

    /** Legacy buttons only ensure the same immutable daily plan. */
    @PostMapping({"/all", "/sign", "/post", "/reply", "/forward", "/view"})
    public Result<Map<String, Object>> ensure(HttpServletRequest request) {
        return result(() -> activities.ensureToday(userId(request)));
    }

    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today(HttpServletRequest request) {
        return result(() -> activities.today(userId(request)));
    }

    @GetMapping("/history")
    public Result<List<ActivityTask>> history(HttpServletRequest request) {
        return result(() -> activities.history(userId(request)));
    }

    @GetMapping("/accounts")
    public Result<List<Map<String, Object>>> accounts(HttpServletRequest request) {
        return result(() -> activities.accountStates(userId(request)));
    }

    @GetMapping("/templates/{topicId}")
    public Result<ActivityTemplates> templates(HttpServletRequest request, @PathVariable Long topicId) {
        return result(() -> activities.templates(userId(request), topicId));
    }

    @PutMapping("/templates/{topicId}")
    public Result<Map<String, Object>> saveTemplates(HttpServletRequest request, @PathVariable Long topicId,
                                                    @RequestBody ActivityTemplates templates) {
        return result(() -> {
            activities.saveTemplates(userId(request), topicId, templates);
            return java.util.Collections.singletonMap("message", "模板已保存，将用于尚未执行的任务");
        });
    }

    @PostMapping("/account/{accountId}/resume")
    public Result<Map<String, Object>> resume(HttpServletRequest request, @PathVariable Long accountId) {
        return result(() -> activities.resume(userId(request), accountId));
    }

    private Long userId(HttpServletRequest request) { return (Long) request.getAttribute("userId"); }

    private <T> Result<T> result(Supplier<T> operation) {
        try { return Result.success(operation.get()); }
        catch (IllegalArgumentException e) { return Result.error(e.getMessage()); }
    }
}
