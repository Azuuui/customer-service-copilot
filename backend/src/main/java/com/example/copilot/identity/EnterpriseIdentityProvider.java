package com.example.copilot.identity;

public interface EnterpriseIdentityProvider {
    EnterpriseIdentity exchange(String oneTimeCode);
}
