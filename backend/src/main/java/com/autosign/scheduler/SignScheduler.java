package com.autosign.scheduler;

import com.autosign.adapter.LoginCheckResult;
import com.autosign.adapter.SignInResult;
import com.autosign.adapter.TiebaSignInAdapter;
import com.autosign.adapter.WeiboSignInAdapter;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.SignRecord;
import com.autosign.service.AccountService;
import com.autosign.service.SignRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SignScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignScheduler.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private SignRecordService recordService;

    @Autowired
    private TiebaSignInAdapter tiebaAdapter;

    @Autowired
    private WeiboSignInAdapter weiboAdapter;

    /**
     * 百度贴吧签到 - 每天 08:10
     */
    @Scheduled(cron = "0 10 8 * * ?")
    public void tiebaSign() {
        log.info("开始执行百度贴吧签到任务");
        executeSign("tieba");
    }

    /**
     * 微博超话签到 - 每天 08:20
     */
    @Scheduled(cron = "0 20 8 * * ?")
    public void weiboSign() {
        log.info("开始执行微博超话签到任务");
        executeSign("weibo");
    }

    private void executeSign(String platform) {
        // 获取所有启用的账号
        List<PlatformAccount> accounts = accountService.listEnabledByPlatform(null, platform);
        if (accounts == null || accounts.isEmpty()) {
            log.info("没有启用的{}平台账号", platform);
            return;
        }

        for (PlatformAccount account : accounts) {
            try {
                // 检查今天是否已执行
                if (recordService.hasExecutedToday(account.getId(), platform)) {
                    log.info("账号{}今天已执行过{}签到", account.getAccountName(), platform);
                    continue;
                }

                // 检查登录状态
                LoginCheckResult loginResult;
                if ("tieba".equals(platform)) {
                    loginResult = tiebaAdapter.checkLogin(account);
                } else {
                    loginResult = weiboAdapter.checkLogin(account);
                }

                if (!loginResult.isLoggedIn()) {
                    recordService.createRecord(account.getUserId(), account.getId(), null, platform);
                    recordService.updateRecordResult(null, 5, 0, 0, loginResult.getMessage());
                    accountService.updateLoginStatus(account.getId(), 2);
                    continue;
                }

                // 执行签到
                SignInResult result;
                if ("tieba".equals(platform)) {
                    result = tiebaAdapter.signIn(account);
                } else {
                    result = weiboAdapter.signAllTopics(account);
                }

                // 保存记录
                SignRecord record = recordService.createRecord(account.getUserId(), account.getId(), null, platform);
                recordService.updateRecordResult(record.getId(), result.getStatus(),
                        result.getSuccessCount(), result.getFailCount(), result.getMessage());

                // 保存明细
                if (result.getDetails() != null) {
                    for (SignInResult.TargetResult detail : result.getDetails()) {
                        recordService.addDetail(record.getId(), platform,
                                detail.getTargetName(), detail.getTargetUrl(),
                                detail.getStatus(), detail.getMessage());
                    }
                }

                // 更新账号最后签到时间
                if (result.getStatus() == 2 || result.getStatus() == 3) {
                    accountService.updateSignTime(account.getId());
                }

                log.info("账号{} {}签到完成: {}", account.getAccountName(), platform, result.getMessage());
            } catch (Exception e) {
                log.error("账号{} {}签到异常", account.getAccountName(), platform, e);
            }
        }
    }
}
