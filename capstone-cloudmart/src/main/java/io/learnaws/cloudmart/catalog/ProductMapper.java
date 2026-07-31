package io.learnaws.cloudmart.catalog;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductItem toItem(Product product) {
        ProductItem item = new ProductItem();
        item.setId(product.id());
        item.setName(product.name());
        item.setDescription(product.description());
        item.setPriceCents(product.priceCents());
        item.setImageKey(product.imageKey());
        return item;
    }

    public static Product toDomain(ProductItem item) {
        return new Product(item.getId(), item.getName(), item.getDescription(), item.getPriceCents(), item.getImageKey());
    }
}
