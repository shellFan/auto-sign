package com.autosign.controller;

import com.autosign.dto.Result;
import com.autosign.entity.PlatformAccount;
import com.autosign.service.AccountService;
import com.autosign.service.BrowserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/browser")
public class BrowserController {

    @Autowired
    private BrowserService browserService;

    @Autowired
    private AccountService accountService;

    @PostMapping("/session/create")
    public Result<Map<String, Object>> createSession(@RequestBody Map<String, Long> request) {
        Long accountId = request.get("accountId");
        PlatformAccount account = accountService.getById(accountId);
        if (account == null) {
            return Result.error("账号不存在");
        }
        return Result.success(browserService.createSession(account));
    }

    @GetMapping("/session/{sessionId}")
    public Result<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return Result.success(browserService.getSession(sessionId));
    }

    @PostMapping("/session/{sessionId}/save")
    public Result<Void> saveSession(@PathVariable String sessionId) {
        browserService.saveSession(sessionId);
        return Result.success();
    }

    @PostMapping("/session/{sessionId}/close")
    public Result<Void> closeSession(@PathVariable String sessionId) {
        browserService.closeSession(sessionId);
        return Result.success();
    }
}
