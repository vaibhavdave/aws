package io.learnaws.ecs;

import java.util.List;

import io.learnaws.ecs.NetworkProvisioner.NetworkInfo;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.Compatibility;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkMode;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.TransportProtocol;

/**
 * Registers a task definition for the containerized Task Tracker image and runs it as an
 * ECS service on Fargate. Real AWS Fargate would also want an executionRoleArn (to pull the
 * image and ship logs) - omitted here since Floci pulls local/plain images directly; add
 * one (with the AmazonECSTaskExecutionRolePolicy) before deploying this to real AWS.
 */
public final class EcsDeployer {

    public static final String CLUSTER_NAME = "task-tracker-cluster";
    public static final String FAMILY = "task-tracker";
    public static final String SERVICE_NAME = "task-tracker-service";
    private static final int CONTAINER_PORT = 8089;

    private EcsDeployer() {
    }

    public static void ensureClusterExists(EcsClient ecs) {
        ecs.createCluster(b -> b.clusterName(CLUSTER_NAME));
    }

    public static String registerTaskDefinition(EcsClient ecs, String containerImage) {
        RegisterTaskDefinitionResponse response = ecs.registerTaskDefinition(b -> b
                .family(FAMILY)
                .networkMode(NetworkMode.AWSVPC)
                .requiresCompatibilities(Compatibility.FARGATE)
                .cpu("256")
                .memory("512")
                .containerDefinitions(c -> c
                        .name(FAMILY)
                        .image(containerImage)
                        .essential(true)
                        .portMappings(p -> p.containerPort(CONTAINER_PORT).protocol(TransportProtocol.TCP))
                        .environment(e -> e.name("AWS_ENDPOINT_URL").value("http://floci:4566"))));

        return response.taskDefinition().taskDefinitionArn();
    }

    public static Service createOrUpdateService(EcsClient ecs, String taskDefinitionArn, NetworkInfo network) {
        boolean exists = serviceExists(ecs);

        if (!exists) {
            return ecs.createService(b -> b
                            .cluster(CLUSTER_NAME)
                            .serviceName(SERVICE_NAME)
                            .taskDefinition(taskDefinitionArn)
                            .desiredCount(1)
                            .launchType(LaunchType.FARGATE)
                            .networkConfiguration(n -> n.awsvpcConfiguration(v -> v
                                    .subnets(network.subnetId())
                                    .securityGroups(network.securityGroupId())
                                    .assignPublicIp(AssignPublicIp.ENABLED))))
                    .service();
        }

        return ecs.updateService(b -> b
                        .cluster(CLUSTER_NAME)
                        .service(SERVICE_NAME)
                        .taskDefinition(taskDefinitionArn)
                        .desiredCount(1))
                .service();
    }

    private static boolean serviceExists(EcsClient ecs) {
        List<Service> services = ecs.describeServices(b -> b.cluster(CLUSTER_NAME).services(SERVICE_NAME)).services();
        return services.stream().anyMatch(s -> !"INACTIVE".equals(s.status()));
    }

    public static void waitUntilStable(EcsClient ecs) {
        // The SDK's built-in waiter also requires every deployment's rolloutState to reach
        // COMPLETED; CI hit its 40-attempt (10 minute) timeout even though the task's own
        // container logs showed the app fully up within seconds - diagnostic evidence needed
        // to tell whether Floci ever sets rolloutState at all. Poll runningCount/desiredCount
        // and deployment state directly (bounded, with logging) instead of trusting the waiter.
        for (int attempt = 0; attempt < 30; attempt++) {
            Service service = ecs.describeServices(b -> b.cluster(CLUSTER_NAME).services(SERVICE_NAME))
                    .services()
                    .get(0);
            System.out.println("[diagnostic] ecs service " + SERVICE_NAME + " (attempt " + attempt + "): " + service);
            if (service.runningCount().equals(service.desiredCount())) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        throw new IllegalStateException(
                "Service " + SERVICE_NAME + " never reached runningCount == desiredCount within 30s");
    }
}
