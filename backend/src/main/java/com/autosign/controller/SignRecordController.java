package com.autosign.controller;

import com.autosign.dto.Result;
import com.autosign.entity.SignRecord;
import com.autosign.entity.SignRecordDetail;
import com.autosign.service.SignRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/sign/record")
public class SignRecordController {

    @Autowired
    private SignRecordService recordService;

    @GetMapping("/list")
    public Result<Page<SignRecord>> list(HttpServletRequest request,
                                         @RequestParam(required = false) String platform,
                                         @RequestParam(required = false) String signDate,
                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(recordService.pageByUserId(userId, platform, signDate, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<SignRecord> getById(@PathVariable Long id) {
        return Result.success(recordService.getById(id));
    }

    @GetMapping("/{id}/details")
    public Result<List<SignRecordDetail>> getDetails(@PathVariable Long id) {
        return Result.success(recordService.getDetails(id));
    }

    @GetMapping("/today")
    public Result<List<SignRecord>> today(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(recordService.getTodayRecords(userId));
    }
}
