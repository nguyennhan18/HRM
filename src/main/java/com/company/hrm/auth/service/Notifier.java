package com.company.hrm.auth.service;
public interface Notifier {
    void passwordReset(String email, String token);
    void onboardingInvite(String email, String temporaryPassword);
}

