package com.rollingstone.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "keycloak.password-policy")
public class PasswordPolicyProperties {

    /**
     * The policy string as understood by Keycloak, e.g.:
     * "passwordHistory(5) and maxLength(128) and hashAlgorithm(pbkdf2-sha256) ..."
     */
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

