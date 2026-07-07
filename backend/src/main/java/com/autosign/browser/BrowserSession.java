package com.autosign.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Data;

@Data
public class BrowserSession {

    private String sessionId;

    private Long accountId;

    private String platform;

    private BrowserContext context;

    private Page page;

    private String profilePath;

    private long createTime;

    private boolean active;
}
