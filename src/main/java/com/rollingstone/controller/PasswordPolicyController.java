package com.rollingstone.controller;


import com.rollingstone.service.KeycloakPasswordPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password-policy")
public class PasswordPolicyController {

    private final KeycloakPasswordPolicyService passwordPolicyService;

    public PasswordPolicyController(KeycloakPasswordPolicyService passwordPolicyService) {
        this.passwordPolicyService = passwordPolicyService;
    }

    /**
     * GET /api/password-policy
     * Returns the current password policy configured on the master realm.
     */
    @GetMapping
    public ResponseEntity<String> getPasswordPolicy() {
        String policy = passwordPolicyService.getCurrentPasswordPolicy();
        return ResponseEntity.ok(policy == null ? "" : policy);
    }

    /**
     * POST /api/password-policy/update
     * Updates the password policy on the master realm using the value from application.yml
     */
    @PostMapping("/update")
    public ResponseEntity<String> updatePasswordPolicy() {
        String newPolicy = passwordPolicyService.updatePasswordPolicyOnMasterRealm();
        return ResponseEntity.ok("Updated master realm password policy to:\n" + newPolicy);
    }
}

