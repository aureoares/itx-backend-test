package com.itx.similarproducts.infrastructure.adapter.out.http;

import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamServiceException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SimuladoProductCatalogAdapterTest {

    private MockRestServiceServer mockServer;
    private SimuladoProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        adapter = new SimuladoProductCatalogAdapter(restClient);
    }

    @Test
    void getSimilarProductIds_returnsIds_when200Ok() {
        mockServer.expect(requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[\"2\", \"3\", \"4\"]", MediaType.APPLICATION_JSON));

        List<String> ids = adapter.getSimilarProductIds("1");

        assertThat(ids).containsExactly("2", "3", "4");
        mockServer.verify();
    }

    @Test
    void getSimilarProductIds_handlesNumericIds_whenNumbersReturned() {
        mockServer.expect(requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[2, 3, 4]", MediaType.APPLICATION_JSON));

        List<String> ids = adapter.getSimilarProductIds("1");

        assertThat(ids).containsExactly("2", "3", "4");
        mockServer.verify();
    }

    @Test
    void getSimilarProductIds_throwsProductNotFoundException_when404() {
        mockServer.expect(requestTo("http://localhost:3001/product/999/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.getSimilarProductIds("999"))
            .isInstanceOf(ProductNotFoundException.class)
            .hasMessageContaining("999");

        mockServer.verify();
    }

    @Test
    void getSimilarProductIds_throwsUpstreamServiceException_when500() {
        mockServer.expect(requestTo("http://localhost:3001/product/1/similarids"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.getSimilarProductIds("1"))
            .isInstanceOf(UpstreamServiceException.class);

        mockServer.verify();
    }

    @Test
    void getProductDetail_returnsDetail_when200Ok() {
        String json = """
            {
              "id": "2",
              "name": "Dress",
              "price": 19.99,
              "availability": true
            }
            """;

        mockServer.expect(requestTo("http://localhost:3001/product/2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<ProductDetail> result = adapter.getProductDetail("2");

        assertThat(result).isPresent();
        ProductDetail product = result.get();
        assertThat(product.id()).isEqualTo("2");
        assertThat(product.name()).isEqualTo("Dress");
        assertThat(product.price()).isEqualTo(new BigDecimal("19.99"));
        assertThat(product.availability()).isTrue();
        mockServer.verify();
    }

    @Test
    void getProductDetail_returnsEmpty_when404NotFound() {
        mockServer.expect(requestTo("http://localhost:3001/product/5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<ProductDetail> result = adapter.getProductDetail("5");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void getProductDetail_throwsUpstreamServiceException_when500() {
        mockServer.expect(requestTo("http://localhost:3001/product/6"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.getProductDetail("6"))
            .isInstanceOf(UpstreamServiceException.class);

        mockServer.verify();
    }
}
