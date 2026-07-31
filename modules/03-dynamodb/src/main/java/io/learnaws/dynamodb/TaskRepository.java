package io.learnaws.dynamodb;

import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class TaskRepository {

    public static final String TABLE_NAME = "task-tracker";
    public static final String OWNER_INDEX = "owner-index";

    private final DynamoDbTable<TaskItem> table;

    public TaskRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(TaskItem.class));
    }

    public Task save(Task task) {
        table.putItem(TaskMapper.toItem(task));
        return task;
    }

    public Optional<Task> findById(String id) {
        TaskItem item = table.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(item).map(TaskMapper::toDomain);
    }

    /** Uses the owner-index GSI - tasks for one owner, ordered by creation time. */
    public List<Task> findByOwner(String owner) {
        DynamoDbIndex<TaskItem> index = table.index(OWNER_INDEX);
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(owner).build());

        return index.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .map(TaskMapper::toDomain)
                .toList();
    }

    public Task updateStatus(String id, TaskStatus newStatus) {
        Task existing = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No task with id " + id));
        Task updated = existing.withStatus(newStatus);
        table.updateItem(TaskMapper.toItem(updated));
        return updated;
    }

    public void delete(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
