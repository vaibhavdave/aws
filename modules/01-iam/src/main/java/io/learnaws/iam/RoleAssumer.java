package io.learnaws.iam;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/** Wraps STS AssumeRole: exchanges a role ARN for short-lived session credentials. */
public final class RoleAssumer {

    private RoleAssumer() {
    }

    public static AwsSessionCredentials assumeRole(StsClient sts, String roleArn, String sessionName) {
        AssumeRoleResponse response = sts.assumeRole(r -> r
                .roleArn(roleArn)
                .roleSessionName(sessionName));

        Credentials credentials = response.credentials();
        return AwsSessionCredentials.create(
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                credentials.sessionToken());
    }
}
