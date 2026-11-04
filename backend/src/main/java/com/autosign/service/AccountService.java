package com.autosign.service;

import com.autosign.entity.PlatformAccount;
import com.autosign.mapper.PlatformAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    @Autowired
    private PlatformAccountMapper accountMapper;

    public List<PlatformAccount> listByUserId(Long userId) {
        LambdaQueryWrapper<PlatformAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformAccount::getUserId, userId)
                .orderByDesc(PlatformAccount::getCreateTime);
        return accountMapper.selectList(wrapper);
    }

    public Page<PlatformAccount> pageByUserId(Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<PlatformAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformAccount::getUserId, userId)
                .orderByDesc(PlatformAccount::getCreateTime);
        return accountMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public PlatformAccount getById(Long id) {
        return accountMapper.selectById(id);
    }

    public boolean create(PlatformAccount account) {
        account.setLoginStatus(0);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        return accountMapper.insert(account) > 0;
    }

    public boolean update(PlatformAccount account) {
        account.setUpdateTime(LocalDateTime.now());
        return accountMapper.updateById(account) > 0;
    }

    public boolean delete(Long id) {
        return accountMapper.deleteById(id) > 0;
    }

    public boolean updateLoginStatus(Long id, Integer status) {
        PlatformAccount account = new PlatformAccount();
        account.setId(id);
        account.setLoginStatus(status);
        account.setUpdateTime(LocalDateTime.now());
        if (status == 1) {
            account.setLastLoginTime(LocalDateTime.now());
        }
        return accountMapper.updateById(account) > 0;
    }

    public boolean updateSignTime(Long id) {
        PlatformAccount account = new PlatformAccount();
        account.setId(id);
        account.setLastSignTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        return accountMapper.updateById(account) > 0;
    }

    public boolean enable(Long id) {
        PlatformAccount account = new PlatformAccount();
        account.setId(id);
        account.setEnableStatus(1);
        account.setUpdateTime(LocalDateTime.now());
        return accountMapper.updateById(account) > 0;
    }

    public boolean disable(Long id) {
        PlatformAccount account = new PlatformAccount();
        account.setId(id);
        account.setEnableStatus(0);
        account.setUpdateTime(LocalDateTime.now());
        return accountMapper.updateById(account) > 0;
    }

    public List<PlatformAccount> listEnabledByPlatform(Long userId, String platform) {
        LambdaQueryWrapper<PlatformAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, PlatformAccount::getUserId, userId)
                .eq(PlatformAccount::getPlatform, platform)
                .eq(PlatformAccount::getEnableStatus, 1)
                .eq(PlatformAccount::getLoginStatus, 1);
        return accountMapper.selectList(wrapper);
    }
}
