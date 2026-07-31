package io.learnaws.cloudmart.orders;

import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class OrderRepository {

    public static final String CUSTOMER_INDEX = "customer-index";

    private final DynamoDbTable<OrderItem> table;

    public OrderRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(OrderTableAdmin.TABLE_NAME, TableSchema.fromBean(OrderItem.class));
    }

    public Order save(Order order) {
        table.putItem(OrderMapper.toItem(order));
        return order;
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build())).map(OrderMapper::toDomain);
    }

    public List<Order> findByCustomer(String customerId) {
        DynamoDbIndex<OrderItem> index = table.index(CUSTOMER_INDEX);
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(customerId).build());
        return index.query(condition).stream().flatMap(page -> page.items().stream()).map(OrderMapper::toDomain).toList();
    }

    public Order updateStatus(String id, OrderStatus status) {
        Order existing = findById(id).orElseThrow(() -> new IllegalArgumentException("No order with id " + id));
        Order updated = existing.withStatus(status);
        table.updateItem(OrderMapper.toItem(updated));
        return updated;
    }
}
