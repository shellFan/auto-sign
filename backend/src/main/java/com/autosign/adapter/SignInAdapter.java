package com.autosign.adapter;

import com.autosign.entity.PlatformAccount;

public interface SignInAdapter {

    String platform();

    LoginCheckResult checkLogin(PlatformAccount account);

    SignInResult signIn(PlatformAccount account);
}
