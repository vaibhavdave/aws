package io.learnaws.cloudmart.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductMapperTest {

    @Test
    void toItemAndBackToDomainRoundTrips() {
        Product product = new Product("p1", "Sticker Pack", "Floci stickers", 599, "images/p1.png");

        ProductItem item = ProductMapper.toItem(product);
        assertThat(item.getId()).isEqualTo("p1");
        assertThat(item.getPriceCents()).isEqualTo(599);

        assertThat(ProductMapper.toDomain(item)).isEqualTo(product);
    }
}
