package com.company.hrm.auth.service;
import org.springframework.stereotype.Component;
/** Delivery intentionally disabled until a real mail adapter is configured. Never logs secrets. */
@Component
public class StubNotifier implements Notifier {
    @Override public void passwordReset(String email, String token) {}
}

