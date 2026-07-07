package com.autosign.controller;

import com.autosign.config.JwtConfig;
import com.autosign.dto.LoginRequest;
import com.autosign.dto.LoginResponse;
import com.autosign.dto.Result;
import com.autosign.entity.SysUser;
import com.autosign.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtConfig jwtConfig;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = userService.findByUsername(request.getUsername());
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            return Result.error(403, "账号已被禁用");
        }
        if (!userService.checkPassword(request.getPassword(), user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        String token = jwtConfig.generateToken(user.getId(), user.getUsername());
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
        return Result.success(response);
    }

    @GetMapping("/me")
    public Result<SysUser> me(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return Result.error(401, "未登录");
        }
        token = token.substring(7);
        if (!jwtConfig.validateToken(token)) {
            return Result.error(401, "token已过期");
        }
        Long userId = jwtConfig.getUserIdFromToken(token);
        SysUser user = userService.getById(userId);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(HttpServletRequest request, @RequestBody ChangePasswordRequest req) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return Result.error(401, "未登录");
        }
        token = token.substring(7);
        Long userId = jwtConfig.getUserIdFromToken(token);
        SysUser user = userService.getById(userId);
        if (!userService.checkPassword(req.getOldPassword(), user.getPassword())) {
            return Result.error("旧密码错误");
        }
        userService.updatePassword(userId, req.getNewPassword());
        return Result.success();
    }

    @lombok.Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
