//package com.rollingstone.service;
//
//
//import com.rollingstone.config.PasswordPolicyProperties;
//import jakarta.ws.rs.core.Response;
//import org.keycloak.admin.client.Keycloak;
//import org.keycloak.admin.client.resource.RealmResource;
//import org.keycloak.representations.idm.RealmRepresentation;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//@Service
//public class KeycloakPasswordPolicyService {
//
//    private static final Logger LOG = LoggerFactory.getLogger(KeycloakPasswordPolicyService.class);
//
//    private final Keycloak keycloak;
//    private final PasswordPolicyProperties passwordPolicyProperties;
//
//    public KeycloakPasswordPolicyService(Keycloak keycloak,
//                                         PasswordPolicyProperties passwordPolicyProperties) {
//        this.keycloak = keycloak;
//        this.passwordPolicyProperties = passwordPolicyProperties;
//    }
//
//    public String updatePasswordPolicyOnMasterRealm() {
//        String policyString = passwordPolicyProperties.getValue();
//        if (policyString == null || policyString.isBlank()) {
//            throw new IllegalStateException("Password policy string is empty. Configure 'keycloak.password-policy.value'.");
//        }
//
//        LOG.info("Updating password policy on realm 'master' to: {}", policyString);
//
//        // Get the realm resource (master)
//        RealmResource realmResource = keycloak.realm("master");
//        RealmRepresentation realmRep = realmResource.toRepresentation();
//
//        // Set the new password policy
//        realmRep.setPasswordPolicy(policyString);
//
//        // Update realm
//        Response response = null;
//        try {
//            realmResource.update(realmRep);
//            LOG.info("Password policy updated successfully for realm 'master'.");
//        } catch (Exception ex) {
//            LOG.error("Failed to update password policy on realm 'master'.", ex);
//            throw ex;
//        }
//
//        return policyString;
//    }
//
//    public String getCurrentPasswordPolicy() {
//        RealmResource realmResource = keycloak.realm("master");
//        RealmRepresentation realmRep = realmResource.toRepresentation();
//        return realmRep.getPasswordPolicy();
//    }
//}
//

package com.rollingstone.service;

import com.rollingstone.config.PasswordPolicyProperties;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KeycloakPasswordPolicyService {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakPasswordPolicyService.class);

    private final Keycloak keycloak;
    private final PasswordPolicyProperties passwordPolicyProperties;

    public KeycloakPasswordPolicyService(Keycloak keycloak,
                                         PasswordPolicyProperties passwordPolicyProperties) {
        this.keycloak = keycloak;
        this.passwordPolicyProperties = passwordPolicyProperties;
    }

    public String updatePasswordPolicyOnMasterRealm() {
        String policyString = passwordPolicyProperties.getValue();
        if (policyString == null || policyString.isBlank()) {
            throw new IllegalStateException("Password policy string is empty. Configure 'keycloak.password-policy.value'.");
        }

        LOG.info("Updating password policy on realm 'master' to: {}", policyString);

        RealmResource realmResource = keycloak.realm("master");
        RealmRepresentation realmRep = realmResource.toRepresentation();
        realmRep.setPasswordPolicy(policyString);

        try {
            realmResource.update(realmRep);
            LOG.info("Password policy updated successfully for realm 'master'.");
        } catch (InternalServerErrorException ex) {
            Response resp = ex.getResponse();
            String body = null;
            try {
                body = resp.readEntity(String.class);
            } catch (Exception ignored) { }

            LOG.error("Keycloak returned 500 when updating realm 'master'. " +
                            "Status={}, Info={}, Body={}",
                    resp.getStatus(),
                    resp.getStatusInfo(),
                    body,
                    ex);

            throw new RuntimeException("Keycloak 500 updating password policy: " + body, ex);
        } catch (Exception ex) {
            LOG.error("Failed to update password policy on realm 'master'.", ex);
            throw ex;
        }

        return policyString;
    }

    public String getCurrentPasswordPolicy() {
        RealmResource realmResource = keycloak.realm("master");
        RealmRepresentation realmRep = realmResource.toRepresentation();
        return realmRep.getPasswordPolicy();
    }
}
