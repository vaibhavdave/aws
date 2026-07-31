package io.learnaws.iam;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the JSON policy documents shipped under {@code src/main/resources/policies}. */
public final class PolicyDocuments {

    private PolicyDocuments() {
    }

    public static final String TRUST_POLICY_RESOURCE = "policies/task-tracker-trust-policy.json";
    public static final String APP_POLICY_RESOURCE = "policies/task-tracker-app-policy.json";

    public static String load(String classpathResource) {
        try (InputStream in = PolicyDocuments.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Policy document not found on classpath: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read policy document: " + classpathResource, e);
        }
    }
}
