Great setup, this is *exactly* the place where you want to wire in the blacklist.

I’ll break it into two parts:

1. **What your Quarkus / Keycloak initializer is doing**
2. **Concrete Gradle + filesystem steps to get `2025-199_most_used_passwords.txt` into `/opt/keycloak/data/password-blacklists` and usable in a password policy**

---

## 1. What your `KeycloakAppInitializer` is doing (relevant bits)

You’ve essentially built a **Keycloak bootstrapper** that runs *inside* the Keycloak Quarkus runtime:

* `@Startup @ApplicationScoped` + `@PostConstruct` → Quarkus runs `init()` when the app starts.
* `@Inject KeycloakSession session;` → You get a live `KeycloakSession` from the server runtime (this is the internal SPI, not the Admin REST client).

Inside `init()`:

```
@PostConstruct
public void init() {
    try {
        session.getTransactionManager().begin();

        samlRealmClientSecret = config.getProperty(...);

        createMasterRealmAdminUser(session);   // creates master admin if missing
        createMaactiveRealm(session);          // creates/initializes maactive realm
        if (sciRealmEnabled) {
            createSCIRealm(session, sciRealmName);
        }
        if (samlRealmSetupFlag) {
            createSamlRealm(session, samlRealmName);
        }
    } finally {
        session.close();
    }
}
```

Key points:

* You’re doing **all realm creation + admin creation + custom flows + user federation** in **one transaction**.
* `createRealm(...)` uses `RealmManager.importRealm(realmRep)` to create a new realm from `RealmRepresentation`.
* That means if you want a password policy (including blacklist), the **cleanest place** is either:

    * In `createRealm(...)` when you create the RealmRepresentation, or
    * Immediately after you fetch the realm (when it already exists) using `realm.setPasswordPolicy(...)`.

Right now, `createRealm(...)` builds this:

```
RealmRepresentation realmRep = new RealmRepresentation();
realmRep.setRealm(realmName);
...
realmManager.importRealm(realmRep);
realm = session.realms().getRealmByName(realmName);
```

So if you want **MA Active** to have a blacklist-based policy, you can do something like:

```
realmRep.setPasswordPolicy(
    "length(12) and hashIterations(27500) and hashAlgorithm(pbkdf2-sha256) and " +
    "passwordHistory(5) and notUsername and passwordBlacklist(2025-199_most_used_passwords.txt)"
);
```

OR after the realm is created:

```
realm.setPasswordPolicy(
    session.getContext()
           .getSessionFactory()
           .getPasswordPolicyManager()
           .parse("length(12) and ... passwordBlacklist(2025-199_most_used_passwords.txt)")
);
```

But for blacklist to work, the file must exist in a very specific place on disk. That’s the Gradle/image part.

---

## 2. Making the blacklist file available to Keycloak

### 2.1. Where Keycloak expects blacklist files

For the built-in `passwordBlacklist(...)` policy, Keycloak looks by default in:

```text
$KC_HOME/data/password-blacklists/
```

Inside that directory, it expects files like:

```text
/opt/keycloak/data/password-blacklists/2025-199_most_used_passwords.txt
```

So our job is:

1. Take `src/main/resources/2025-199_most_used_passwords.txt` from your provider project

2. During `prepareKeycloakDistribution`, copy it into:

   ```text
   ${augmentedKeycloakDir}/data/password-blacklists/2025-199_most_used_passwords.txt
   ```

3. Because Jib copies the entire `augmentedKeycloakDir` into the image under `${kcHomeInImage}`, it will end up as:

   ```text
   /opt/keycloak/data/password-blacklists/2025-199_most_used_passwords.txt
   ```

No extra Jib config is needed beyond what you already have.

### 2.2. Add a copy step in `prepareKeycloakDistribution`

You already create/clean `data` and call `kc.sh build`. Let’s extend `doLast` in `prepareKeycloakDistribution` with one extra `copy` block.

In your `prepareKeycloakDistribution` task, inside `doLast { ... }`, add:

```
    // Ensure data/password-blacklists exists and copy blacklist file from resources
    doLast {
        ...
        // existing copy blocks (config, providers, themes, etc.)

        def dataDir = new File("${augmentedKeycloakDir.get().asFile}/data")
        if (!dataDir.exists()) {
            println "📁 Creating missing data directory at ${dataDir.absolutePath}"
            dataDir.mkdirs()
        }

        // 🔐 Create password-blacklists directory
        def passwordBlacklistDir = new File(dataDir, "password-blacklists")
        if (!passwordBlacklistDir.exists()) {
            println "📁 Creating password-blacklists directory at ${passwordBlacklistDir.absolutePath}"
            passwordBlacklistDir.mkdirs()
        }

        // 🔐 Copy your blacklist file from src/main/resources
        copy {
            from("src/main/resources/2025-199_most_used_passwords.txt")
            into(passwordBlacklistDir.absolutePath)
            println "📄 Copied password blacklist to ${passwordBlacklistDir.absolutePath}"
        }

        exec {
            workingDir augmentedKeycloakDir.get().asFile
            environment kcBuildEnv
            commandLine "${augmentedKeycloakDir.get().asFile}/bin/kc.sh", "build",
                    "--features=fips", "--fips-mode=strict"
        }
        println "kc.sh build command completed."
        ...
    }
```

Things to note:

* `from("src/main/resources/...")` is relative to **this module’s** root (the provider module where your Gradle file is). If you put the file somewhere else (e.g. `src/main/resources/password-blacklists/...`), adjust the path accordingly.
* We create `data` if missing (you already had that) and also `data/password-blacklists`.

You already set permissions in `configureJibPermissions` for `${kcHomeInImage}/data` as `777`, so Keycloak will be able to read that file.

### 2.3. Optional: keep things organized

If you want to support multiple blacklist files, a tidy layout is:

* In your provider project:

  ```text
  src/main/resources/password-blacklists/2025-199_most_used_passwords.txt
  ```

* And then in Gradle:

  ```groovy
  copy {
      from("src/main/resources/password-blacklists")
      into(passwordBlacklistDir.absolutePath)
      include("*.txt")
      println "📄 Copied password blacklist(s) to ${passwordBlacklistDir.absolutePath}"
  }
  ```

Now any `.txt` you drop into `src/main/resources/password-blacklists/` gets copied into Keycloak’s `data/password-blacklists/`.

---

## 3. Using it in the password policy string

Once the file is in place, your realm password policy string can reference the **filename only**:

```
realmRep.setPasswordPolicy(
    "length(12) and hashIterations(27500) and hashAlgorithm(pbkdf2-sha256) and " +
    "passwordHistory(5) and notUsername and passwordBlacklist(2025-199_most_used_passwords.txt)"
);
```

Keycloak internally will look for:

```
$KC_HOME/data/password-blacklists/2025-199_most_used_passwords.txt
```

If the file is missing or unreadable, you’ll see errors in the server log and a 500 when trying to set that policy via Admin REST.

---

## 4. Quick end-to-end checklist

1. **File location in repo**

   ```text
   src/main/resources/2025-199_most_used_passwords.txt
   ```

2. **Gradle: copy file into image**

    * Add the `copy { from ... into data/password-blacklists }` block in `prepareKeycloakDistribution.doLast`.

3. **Rebuild & rebuild image**

   ```bash
   ./gradlew clean buildDocker
   ./gradlew run   # or docker run ... if using your run task
   ```

4. **Confirm in the container**

   Once Keycloak is running, exec into the container:

   ```bash
   docker exec -it component-keycloak-standalone /bin/bash
   ls -l /opt/keycloak/data/password-blacklists
   ```

   You should see `2025-199_most_used_passwords.txt`.

5. **Set password policy**

    * Either via your Quarkus initializer (`realmRep.setPasswordPolicy(...)` in `createRealm`)
    * Or via Admin Console / Admin REST for testing.

If you want, I can show exactly where to drop `realmRep.setPasswordPolicy(...)` in your `createRealm` / `createMaactiveRealm` methods so MA Active, SCI, and SAML realms all share the same blacklist-aware, FedRAMP-friendly password policy.
