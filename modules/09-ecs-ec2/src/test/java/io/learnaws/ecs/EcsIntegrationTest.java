package io.learnaws.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.learnaws.ecs.NetworkProvisioner.NetworkInfo;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Service;

/**
 * Requires Docker AND a locally-built image:
 *   mvn -pl modules/09-ecs-ec2 -am package
 *   docker build -t task-tracker-container:latest modules/09-ecs-ec2
 *   docker compose up -d
 * Then: mvn test -Pfloci -pl modules/09-ecs-ec2
 *
 * Targets the docker-compose Floci instance directly (same reasoning as Modules 05-08):
 * the Fargate task Floci launches needs a predictable route back to Floci for its own
 * DynamoDB calls, via the AWS_ENDPOINT_URL EcsDeployer sets on the container definition.
 */
@Tag("floci")
class EcsIntegrationTest {

    @Test
    void serviceReachesSteadyStateWithTheDesiredTaskCountRunning() {
        FlociEndpoint floci = FlociEndpoint.local();

        Ec2Client ec2 = floci.configure(Ec2Client.builder());
        EcsClient ecs = floci.configure(EcsClient.builder());

        NetworkInfo network = NetworkProvisioner.provision(ec2);
        EcsDeployer.ensureClusterExists(ecs);
        String taskDefinitionArn = EcsDeployer.registerTaskDefinition(ecs, "task-tracker-container:latest");
        EcsDeployer.createOrUpdateService(ecs, taskDefinitionArn, network);

        EcsDeployer.waitUntilStable(ecs);

        Service service = ecs.describeServices(b -> b
                        .cluster(EcsDeployer.CLUSTER_NAME)
                        .services(EcsDeployer.SERVICE_NAME))
                .services()
                .get(0);

        assertThat(service.runningCount()).isEqualTo(service.desiredCount());
    }
}
