package com.autosign.service;

import com.autosign.entity.SignRecord;
import com.autosign.entity.SignRecordDetail;
import com.autosign.mapper.SignRecordDetailMapper;
import com.autosign.mapper.SignRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SignRecordService {

    @Autowired
    private SignRecordMapper recordMapper;

    @Autowired
    private SignRecordDetailMapper detailMapper;

    public SignRecord createRecord(Long userId, Long accountId, Long taskId, String platform) {
        SignRecord record = new SignRecord();
        record.setUserId(userId);
        record.setAccountId(accountId);
        record.setTaskId(taskId);
        record.setPlatform(platform);
        record.setSignDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        record.setStatus(1);
        record.setSuccessCount(0);
        record.setFailCount(0);
        record.setStartTime(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(record);
        return record;
    }

    public boolean updateRecordResult(Long recordId, Integer status, Integer successCount, Integer failCount, String message) {
        SignRecord record = new SignRecord();
        record.setId(recordId);
        record.setStatus(status);
        record.setSuccessCount(successCount);
        record.setFailCount(failCount);
        record.setMessage(message);
        record.setEndTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return recordMapper.updateById(record) > 0;
    }

    public boolean updateScreenshot(Long recordId, String screenshotPath) {
        SignRecord record = new SignRecord();
        record.setId(recordId);
        record.setScreenshotPath(screenshotPath);
        record.setUpdateTime(LocalDateTime.now());
        return recordMapper.updateById(record) > 0;
    }

    public SignRecord getById(Long id) {
        return recordMapper.selectById(id);
    }

    public Page<SignRecord> pageByUserId(Long userId, String platform, String signDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<SignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignRecord::getUserId, userId);
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(SignRecord::getPlatform, platform);
        }
        if (signDate != null && !signDate.isEmpty()) {
            wrapper.eq(SignRecord::getSignDate, signDate);
        }
        wrapper.orderByDesc(SignRecord::getCreateTime);
        return recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public boolean hasExecutedToday(Long accountId, String platform) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        LambdaQueryWrapper<SignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignRecord::getAccountId, accountId)
                .eq(SignRecord::getPlatform, platform)
                .eq(SignRecord::getSignDate, today)
                .in(SignRecord::getStatus, 2, 3, 4, 5, 6);
        return recordMapper.selectCount(wrapper) > 0;
    }

    public List<SignRecord> getTodayRecords(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        LambdaQueryWrapper<SignRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignRecord::getUserId, userId)
                .eq(SignRecord::getSignDate, today)
                .orderByDesc(SignRecord::getCreateTime);
        return recordMapper.selectList(wrapper);
    }

    public void addDetail(Long recordId, String targetType, String targetName, String targetUrl, Integer status, String message) {
        SignRecordDetail detail = new SignRecordDetail();
        detail.setRecordId(recordId);
        detail.setTargetType(targetType);
        detail.setTargetName(targetName);
        detail.setTargetUrl(targetUrl);
        detail.setStatus(status);
        detail.setMessage(message);
        detail.setCreateTime(LocalDateTime.now());
        detailMapper.insert(detail);
    }

    public void addDetailWithScreenshot(Long recordId, String targetType, String targetName, String targetUrl, Integer status, String message, String screenshotPath) {
        SignRecordDetail detail = new SignRecordDetail();
        detail.setRecordId(recordId);
        detail.setTargetType(targetType);
        detail.setTargetName(targetName);
        detail.setTargetUrl(targetUrl);
        detail.setStatus(status);
        detail.setMessage(message);
        detail.setScreenshotPath(screenshotPath);
        detail.setCreateTime(LocalDateTime.now());
        detailMapper.insert(detail);
    }

    public List<SignRecordDetail> getDetails(Long recordId) {
        LambdaQueryWrapper<SignRecordDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SignRecordDetail::getRecordId, recordId)
                .orderByAsc(SignRecordDetail::getCreateTime);
        return detailMapper.selectList(wrapper);
    }
}
