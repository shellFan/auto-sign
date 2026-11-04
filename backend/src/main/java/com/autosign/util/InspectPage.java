package com.autosign.util;

import com.microsoft.playwright.*;
import java.util.List;

public class InspectPage {
    public static void main(String[] args) throws Exception {
        Playwright playwright = Playwright.create();
        try {
            System.out.println("Connecting to Chrome via CDP...");
            Browser browser = playwright.chromium().connectOverCDP("http://localhost:9222");
            List<BrowserContext> contexts = browser.contexts();
            if (contexts.isEmpty()) { System.out.println("No contexts"); return; }
            
            BrowserContext context = contexts.get(0);
            List<Page> pages = context.pages();
            if (pages.isEmpty()) { System.out.println("No pages"); return; }
            
            Page page = pages.get(0);
            System.out.println("Title: " + page.title());
            System.out.println("URL: " + page.url());
            page.waitForTimeout(3000);
            
            // Find comment icon
            System.out.println("\n=== Comment icons ===");
            List<Locator> allIcons = page.locator("i[title]").all();
            for (Locator icon : allIcons) {
                try {
                    String title = icon.evaluate("el => el.title").toString();
                    if (title.contains("comment") || title.contains("ping")) {
                        System.out.println("Found: title=" + title);
                        icon.click();
                        page.waitForTimeout(3000);
                        break;
                    }
                } catch (Exception e) {}
            }
            
            // Find inputs
            System.out.println("\n=== Inputs ===");
            List<Locator> inputs = page.locator("textarea, [contenteditable='true'], input[type='text']").all();
            for (Locator input : inputs) {
                try {
                    String tag = input.evaluate("el => el.tagName").toString();
                    String cls = input.evaluate("el => el.className || ''").toString();
                    String ph = input.evaluate("el => el.placeholder || ''").toString();
                    boolean vis = (boolean) input.evaluate("el => el.offsetParent !== null");
                    System.out.println("tag=" + tag + " class=" + cls.substring(0, Math.min(60, cls.length())) + " ph=" + ph + " vis=" + vis);
                } catch (Exception e) {}
            }
            
            // Find visible buttons
            System.out.println("\n=== Visible buttons ===");
            List<Locator> btns = page.locator("button, [role='button']").all();
            for (Locator btn : btns) {
                try {
                    boolean vis = (boolean) btn.evaluate("el => el.offsetParent !== null");
                    if (vis) {
                        String text = btn.innerText().trim();
                        String cls = btn.evaluate("el => el.className || ''").toString();
                        if (text.length() > 0 && text.length() < 20) {
                            System.out.println("text='" + text + "' class=" + cls.substring(0, Math.min(50, cls.length())));
                        }
                    }
                } catch (Exception e) {}
            }
            
            // Search for send/reply via JS
            System.out.println("\n=== JS search for send/reply ===");
            Object result = page.evaluate("() => {" +
                "  const all = document.querySelectorAll('*');" +
                "  let r = '';" +
                "  for (const el of all) {" +
                "    if (el.offsetParent !== null && el.children.length === 0) {" +
                "      const t = (el.textContent || '').trim();" +
                "      if (t === '\u53d1\u9001' || t === '\u53d1\u5e03' || t === '\u56de\u590d' || t === 'Send' || t === 'Reply') {" +
                "        r += 'Found: tag=' + el.tagName + ' class=' + el.className + ' text=' + t + '\\n';" +
                "        r += 'Parent: tag=' + el.parentElement.tagName + ' class=' + el.parentElement.className + '\\n';" +
                "        r += 'HTML: ' + el.outerHTML.substring(0, 300) + '\\n\\n';" +
                "      }" +
                "    }" +
                "  }" +
                "  return r || 'Not found';" +
                "}");
            System.out.println(result);
            
            // Screenshot
            page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get("D:\\git\\fan\\aixm\\qiandao\\auto-sign\\backend\\inspect.png")));
            System.out.println("\nScreenshot saved");
            
        } finally {
            playwright.close();
        }
    }
}
