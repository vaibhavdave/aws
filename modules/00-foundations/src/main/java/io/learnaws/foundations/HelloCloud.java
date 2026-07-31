package io.learnaws.foundations;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * The first program of the curriculum: proves the Java toolchain, the AWS SDK,
 * and Floci are all wired together correctly.
 *
 * Run {@code docker compose up -d} from the repo root first, then:
 *   mvn -pl modules/00-foundations exec:java -Dexec.mainClass=io.learnaws.foundations.HelloCloud
 */
public final class HelloCloud {

    public static void main(String[] args) {
        FlociEndpoint floci = FlociEndpoint.local();

        try (StsClient sts = floci.configure(StsClient.builder())) {
            GetCallerIdentityResponse identity = sts.getCallerIdentity();

            System.out.println("Connected to Floci at " + floci.getEndpoint());
            System.out.println("Account : " + identity.account());
            System.out.println("ARN     : " + identity.arn());
            System.out.println("UserId  : " + identity.userId());
        }
    }
}
