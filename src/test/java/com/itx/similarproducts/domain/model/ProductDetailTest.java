package com.itx.similarproducts.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDetailTest {

    @Test
    void shouldCreateProductDetailSuccessfully() {
        ProductDetail product = new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true);

        assertThat(product.id()).isEqualTo("1");
        assertThat(product.name()).isEqualTo("Shirt");
        assertThat(product.price()).isEqualTo(new BigDecimal("9.99"));
        assertThat(product.availability()).isTrue();
    }

    @Test
    void shouldCreateProductDetailWithDoubleConstructor() {
        ProductDetail product = new ProductDetail("2", "Dress", 19.99, false);

        assertThat(product.id()).isEqualTo("2");
        assertThat(product.name()).isEqualTo("Dress");
        assertThat(product.price()).isEqualTo(BigDecimal.valueOf(19.99));
        assertThat(product.availability()).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenIdIsBlankOrNull() {
        assertThatThrownBy(() -> new ProductDetail(null, "Dress", 19.99, true))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id must not be null");

        assertThatThrownBy(() -> new ProductDetail(" ", "Dress", 19.99, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    void shouldThrowExceptionWhenNameIsBlankOrNull() {
        assertThatThrownBy(() -> new ProductDetail("1", null, 19.99, true))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name must not be null");

        assertThatThrownBy(() -> new ProductDetail("1", "   ", 19.99, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name must not be blank");
    }

    @Test
    void shouldThrowExceptionWhenPriceIsNull() {
        assertThatThrownBy(() -> new ProductDetail("1", "Shirt", null, true))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("price must not be null");
    }
}
