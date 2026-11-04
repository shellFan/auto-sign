package com.autosign.controller;

import com.autosign.adapter.LoginCheckResult;
import com.autosign.adapter.TiebaSignInAdapter;
import com.autosign.adapter.WeiboSignInAdapter;
import com.autosign.dto.Result;
import com.autosign.entity.PlatformAccount;
import com.autosign.service.AccountService;
import com.autosign.service.BrowserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private BrowserService browserService;

    @Autowired
    private TiebaSignInAdapter tiebaSignInAdapter;

    @Autowired
    private WeiboSignInAdapter weiboSignInAdapter;

    @GetMapping("/list")
    public Result<List<PlatformAccount>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(accountService.listByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<PlatformAccount> getById(@PathVariable Long id) {
        return Result.success(accountService.getById(id));
    }

    @PostMapping("/create")
    public Result<Void> create(HttpServletRequest request, @RequestBody PlatformAccount account) {
        Long userId = (Long) request.getAttribute("userId");
        account.setUserId(userId);
        accountService.create(account);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PlatformAccount account) {
        account.setId(id);
        accountService.update(account);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        accountService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        accountService.disable(id);
        return Result.success();
    }

    @PostMapping("/{id}/check-login")
    public Result<Void> checkLogin(@PathVariable Long id) {
        PlatformAccount account = accountService.getById(id);
        if (account == null) {
            return Result.error("账号不存在");
        }
        LoginCheckResult loginResult;
        if ("weibo".equals(account.getPlatform())) {
            loginResult = weiboSignInAdapter.checkLogin(account);
        } else if ("tieba".equals(account.getPlatform())) {
            loginResult = tiebaSignInAdapter.checkLogin(account);
        } else {
            return Result.error("不支持的平台");
        }

        accountService.updateLoginStatus(id, loginResult.isLoggedIn() ? 1 : 2);
        if (!loginResult.isLoggedIn()) {
            return Result.error(loginResult.getMessage());
        }
        return Result.success();
    }

    /**
     * 直接使用 Cookie 保存登录态
     * 用户可以从浏览器开发者工具中复制 Cookie
     */
    @PostMapping("/{id}/save-cookie")
    public Result<Void> saveCookie(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String cookie = request.get("cookie");
        if (cookie == null || cookie.isEmpty()) {
            return Result.error("Cookie 不能为空");
        }

        PlatformAccount account = accountService.getById(id);
        if (account == null) {
            return Result.error("账号不存在");
        }

        try {
            // 保存 Cookie 到文件
            browserService.saveCookie(account, cookie);
            // 更新账号状态为已登录
            accountService.updateLoginStatus(id, 1);
            return Result.success();
        } catch (Exception e) {
            return Result.error("保存 Cookie 失败: " + e.getMessage());
        }
    }
}
