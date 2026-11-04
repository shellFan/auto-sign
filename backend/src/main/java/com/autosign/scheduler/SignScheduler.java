package com.autosign.scheduler;

import com.autosign.activity.ActivityService;
import com.autosign.adapter.*;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.SignRecord;
import com.autosign.service.AccountService;
import com.autosign.service.SignRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SignScheduler {
    private static final Logger log = LoggerFactory.getLogger(SignScheduler.class);
    @Autowired private AccountService accountService;
    @Autowired private SignRecordService recordService;
    @Autowired private TiebaSignInAdapter tiebaAdapter;
    @Autowired private WeiboSignInAdapter weiboAdapter;
    @Autowired private ActivityService activities;

    @Scheduled(cron = "0 10 8 * * ?")
    public void tiebaSign() {
        log.info("start tieba sign task");
        executeSign("tieba");
    }

    @Scheduled(initialDelayString = "${weibo.activity.initial-delay-ms:10000}",
            fixedDelayString = "${weibo.activity.poll-delay-ms:5000}")
    public void weiboActivities() {
        activities.tick();
    }

    private void executeSign(String platform) {
        List<PlatformAccount> accounts = accountService.listEnabledByPlatform(null, platform);
        if (accounts == null || accounts.isEmpty()) {
            log.info("no enabled {} accounts", platform);
            return;
        }

        for (PlatformAccount account : accounts) {
            try {
                if (recordService.hasExecutedToday(account.getId(), platform)) {
                    log.info("account {} already executed {} sign today", account.getAccountName(), platform);
                    continue;
                }

                LoginCheckResult loginResult;
                if ("tieba".equals(platform)) {
                    loginResult = tiebaAdapter.checkLogin(account);
                } else {
                    loginResult = weiboAdapter.checkLogin(account);
                }

                if (!loginResult.isLoggedIn()) {
                    SignRecord record = recordService.createRecord(account.getUserId(), account.getId(), null, platform);
                    recordService.updateRecordResult(record.getId(), 5, 0, 0, loginResult.getMessage());
                    accountService.updateLoginStatus(account.getId(), 2);
                    continue;
                }

                SignInResult result;
                if ("tieba".equals(platform)) {
                    result = tiebaAdapter.signIn(account);
                } else {
                    result = weiboAdapter.signAllTopics(account);
                }

                SignRecord record = recordService.createRecord(account.getUserId(), account.getId(), null, platform);
                recordService.updateRecordResult(record.getId(), result.getStatus(),
                        result.getSuccessCount(), result.getFailCount(), result.getMessage());

                if (result.getDetails() != null) {
                    for (SignInResult.TargetResult detail : result.getDetails()) {
                        recordService.addDetail(record.getId(), platform,
                                detail.getTargetName(), detail.getTargetUrl(),
                                detail.getStatus(), detail.getMessage());
                    }
                }

                if (result.getStatus() == 2 || result.getStatus() == 3) {
                    accountService.updateSignTime(account.getId());
                }

                log.info("account {} {} sign completed: {}", account.getAccountName(), platform, result.getMessage());
            } catch (Exception e) {
                log.error("account {} {} sign exception", account.getAccountName(), platform, e);
            }
        }
    }

}
