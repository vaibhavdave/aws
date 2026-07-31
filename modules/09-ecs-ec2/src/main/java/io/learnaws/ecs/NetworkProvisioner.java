package io.learnaws.ecs;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.Subnet;
import software.amazon.awssdk.services.ec2.model.Vpc;

/**
 * Fargate tasks run in "awsvpc" network mode, which requires a real VPC/subnet/security
 * group even locally - Fargate has no underlying EC2 instances of its own to attach a
 * network interface to otherwise. This is genuinely EC2 API surface (VPCs, subnets,
 * security groups are all EC2 resources), even though nothing here is a compute instance.
 */
public final class NetworkProvisioner {

    private static final String VPC_NAME = "task-tracker-vpc";
    private static final String SUBNET_NAME = "task-tracker-subnet";
    private static final String SECURITY_GROUP_NAME = "task-tracker-sg";
    private static final int CONTAINER_PORT = 8089;

    private NetworkProvisioner() {
    }

    public record NetworkInfo(String vpcId, String subnetId, String securityGroupId) {
    }

    public static NetworkInfo provision(Ec2Client ec2) {
        String vpcId = findOrCreateVpc(ec2);
        String subnetId = findOrCreateSubnet(ec2, vpcId);
        String securityGroupId = findOrCreateSecurityGroup(ec2, vpcId);
        return new NetworkInfo(vpcId, subnetId, securityGroupId);
    }

    private static String findOrCreateVpc(Ec2Client ec2) {
        var existing = ec2.describeVpcs(b -> b.filters(f -> f.name("tag:Name").values(VPC_NAME))).vpcs();
        if (!existing.isEmpty()) {
            return existing.get(0).vpcId();
        }

        Vpc vpc = ec2.createVpc(b -> b
                        .cidrBlock("10.0.0.0/16")
                        .tagSpecifications(t -> t
                                .resourceType(ResourceType.VPC)
                                .tags(tag -> tag.key("Name").value(VPC_NAME))))
                .vpc();
        return vpc.vpcId();
    }

    private static String findOrCreateSubnet(Ec2Client ec2, String vpcId) {
        var existing = ec2.describeSubnets(b -> b.filters(f -> f.name("tag:Name").values(SUBNET_NAME))).subnets();
        if (!existing.isEmpty()) {
            return existing.get(0).subnetId();
        }

        Subnet subnet = ec2.createSubnet(b -> b
                        .vpcId(vpcId)
                        .cidrBlock("10.0.1.0/24")
                        .tagSpecifications(t -> t
                                .resourceType(ResourceType.SUBNET)
                                .tags(tag -> tag.key("Name").value(SUBNET_NAME))))
                .subnet();
        return subnet.subnetId();
    }

    private static String findOrCreateSecurityGroup(Ec2Client ec2, String vpcId) {
        var existing = ec2.describeSecurityGroups(b -> b.filters(f -> f.name("group-name").values(SECURITY_GROUP_NAME)))
                .securityGroups();
        if (!existing.isEmpty()) {
            return existing.get(0).groupId();
        }

        String groupId = ec2.createSecurityGroup(b -> b
                        .groupName(SECURITY_GROUP_NAME)
                        .description("Ingress for the containerized Task Tracker service")
                        .vpcId(vpcId))
                .groupId();

        ec2.authorizeSecurityGroupIngress(b -> b
                .groupId(groupId)
                .ipProtocol("tcp")
                .fromPort(CONTAINER_PORT)
                .toPort(CONTAINER_PORT)
                .cidrIp("0.0.0.0/0"));

        return groupId;
    }
}
