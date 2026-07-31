package io.learnaws.cloudmart.catalog;

public record Product(String id, String name, String description, long priceCents, String imageKey) {
}
