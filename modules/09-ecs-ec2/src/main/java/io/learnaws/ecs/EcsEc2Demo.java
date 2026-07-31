package io.learnaws.ecs;

import io.learnaws.ecs.NetworkProvisioner.NetworkInfo;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Service;

/**
 * Requires: docker build -t task-tracker-container:latest .   (from modules/09-ecs-ec2/,
 * after `mvn -pl modules/09-ecs-ec2 -am package` has produced target/ecs-ec2-task-tracker.jar)
 * Then run: mvn -pl modules/09-ecs-ec2 compile exec:java
 */
public final class EcsEc2Demo {

    private static final String CONTAINER_IMAGE = "task-tracker-container:latest";

    public static void main(String[] args) {
        FlociEndpoint floci = FlociEndpoint.local();

        try (Ec2Client ec2 = floci.configure(Ec2Client.builder());
                EcsClient ecs = floci.configure(EcsClient.builder())) {

            NetworkInfo network = NetworkProvisioner.provision(ec2);
            System.out.println("VPC " + network.vpcId() + ", subnet " + network.subnetId()
                    + ", security group " + network.securityGroupId());

            EcsDeployer.ensureClusterExists(ecs);
            String taskDefinitionArn = EcsDeployer.registerTaskDefinition(ecs, CONTAINER_IMAGE);
            System.out.println("Registered task definition: " + taskDefinitionArn);

            Service service = EcsDeployer.createOrUpdateService(ecs, taskDefinitionArn, network);
            System.out.println("Service " + service.serviceName() + " desired count " + service.desiredCount());

            System.out.println("Waiting for the service to reach steady state...");
            EcsDeployer.waitUntilStable(ecs);
            System.out.println("Service is stable: running count == desired count.");
        }
    }
}
