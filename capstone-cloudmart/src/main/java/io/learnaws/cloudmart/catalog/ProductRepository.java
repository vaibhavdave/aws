package io.learnaws.cloudmart.catalog;

import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

public class ProductRepository {

    private final DynamoDbTable<ProductItem> table;

    public ProductRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(ProductTableAdmin.TABLE_NAME, TableSchema.fromBean(ProductItem.class));
    }

    public Product save(Product product) {
        table.putItem(ProductMapper.toItem(product));
        return product;
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build())).map(ProductMapper::toDomain);
    }

    public List<Product> list() {
        return table.scan().items().stream().map(ProductMapper::toDomain).toList();
    }
}
