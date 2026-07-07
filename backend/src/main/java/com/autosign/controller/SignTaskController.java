package com.autosign.controller;

import com.autosign.dto.Result;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.SignTask;
import com.autosign.mapper.SignTaskMapper;
import com.autosign.service.AccountService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sign/task")
public class SignTaskController {

    @Autowired
    private SignTaskMapper taskMapper;

    @Autowired
    private AccountService accountService;

    @GetMapping("/list")
    public Result<List<SignTask>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        LambdaQueryWrapper<SignTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignTask::getUserId, userId)
                .orderByDesc(SignTask::getCreateTime);
        return Result.success(taskMapper.selectList(wrapper));
    }

    @PostMapping("/create")
    public Result<Void> create(HttpServletRequest request, @RequestBody SignTask task) {
        Long userId = (Long) request.getAttribute("userId");
        task.setUserId(userId);
        // 从账号获取平台信息
        if (task.getPlatform() == null || task.getPlatform().isEmpty()) {
            PlatformAccount account = accountService.getById(task.getAccountId());
            if (account != null) {
                task.setPlatform(account.getPlatform());
            }
        }
        task.setEnableStatus(1);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SignTask task) {
        task.setId(id);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        SignTask task = new SignTask();
        task.setId(id);
        task.setEnableStatus(1);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        SignTask task = new SignTask();
        task.setId(id);
        task.setEnableStatus(0);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.success();
    }

    @PostMapping("/{id}/run")
    public Result<Void> run(@PathVariable Long id) {
        // 触发任务执行
        SignTask task = taskMapper.selectById(id);
        if (task != null) {
            task.setLastRunTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        return Result.success();
    }
}
