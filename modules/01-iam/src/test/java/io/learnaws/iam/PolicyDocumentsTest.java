package io.learnaws.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/** No Floci required: just validates the shape of the JSON policy documents we ship. */
class PolicyDocumentsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void trustPolicyAllowsSameAccountRootToAssumeTheRole() throws IOException {
        JsonNode policy = mapper.readTree(PolicyDocuments.load(PolicyDocuments.TRUST_POLICY_RESOURCE));

        assertThat(policy.get("Version").asText()).isEqualTo("2012-10-17");
        JsonNode statement = policy.get("Statement").get(0);
        assertThat(statement.get("Effect").asText()).isEqualTo("Allow");
        assertThat(statement.get("Action").asText()).isEqualTo("sts:AssumeRole");
        assertThat(statement.get("Principal").get("AWS").asText()).contains(":root");
    }

    @Test
    void appPolicyScopesDynamoDbAndS3ToSpecificResources() throws IOException {
        JsonNode policy = mapper.readTree(PolicyDocuments.load(PolicyDocuments.APP_POLICY_RESOURCE));

        assertThat(policy.get("Statement")).hasSize(2);

        JsonNode dynamoStatement = policy.get("Statement").get(0);
        assertThat(dynamoStatement.get("Sid").asText()).isEqualTo("TaskTrackerTableAccess");
        assertThat(dynamoStatement.get("Resource").get(0).asText()).contains("table/task-tracker");
        assertThat(dynamoStatement.get("Action").get(0).asText()).startsWith("dynamodb:");

        JsonNode s3Statement = policy.get("Statement").get(1);
        assertThat(s3Statement.get("Sid").asText()).isEqualTo("TaskTrackerFileVaultAccess");
        assertThat(s3Statement.get("Resource").asText()).contains("task-tracker-files");
    }
}
