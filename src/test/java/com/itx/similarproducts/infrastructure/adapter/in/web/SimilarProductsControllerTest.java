package com.itx.similarproducts.infrastructure.adapter.in.web;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamServiceException;
import com.itx.similarproducts.domain.model.ProductDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SimilarProductsController.class)
@Import(GlobalExceptionHandler.class)
class SimilarProductsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSimilarProductsUseCase getSimilarProductsUseCase;

    @Test
    void getSimilarProducts_returns200AndOrderedArray_whenProductsFound() throws Exception {
        ProductDetail product2 = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
        ProductDetail product3 = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);
        ProductDetail product4 = new ProductDetail("4", "Boots", new BigDecimal("39.99"), true);

        when(getSimilarProductsUseCase.getSimilarProducts("1"))
            .thenReturn(List.of(product2, product3, product4));

        mockMvc.perform(get("/product/1/similar")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].id", is("2")))
            .andExpect(jsonPath("$[0].name", is("Dress")))
            .andExpect(jsonPath("$[0].price", is(19.99)))
            .andExpect(jsonPath("$[0].availability", is(true)))
            .andExpect(jsonPath("$[1].id", is("3")))
            .andExpect(jsonPath("$[1].name", is("Blazer")))
            .andExpect(jsonPath("$[1].price", is(29.99)))
            .andExpect(jsonPath("$[1].availability", is(false)))
            .andExpect(jsonPath("$[2].id", is("4")))
            .andExpect(jsonPath("$[2].name", is("Boots")))
            .andExpect(jsonPath("$[2].price", is(39.99)))
            .andExpect(jsonPath("$[2].availability", is(true)));
    }

    @Test
    void getSimilarProducts_returns200AndEmptyArray_whenNoSimilarProducts() throws Exception {
        when(getSimilarProductsUseCase.getSimilarProducts("10"))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/product/10/similar")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getSimilarProducts_returns404_whenRootProductNotFound() throws Exception {
        when(getSimilarProductsUseCase.getSimilarProducts("999"))
            .thenThrow(new ProductNotFoundException("999"));

        mockMvc.perform(get("/product/999/similar")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", is("Product not found: 999")));
    }

    @Test
    void getSimilarProducts_returns400_whenProductIdIsBlank() throws Exception {
        mockMvc.perform(get("/product/{productId}/similar", " ")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Product ID must not be blank")));
    }

    @Test
    void getSimilarProducts_returns502_whenUpstreamFails() throws Exception {
        when(getSimilarProductsUseCase.getSimilarProducts("1"))
            .thenThrow(new UpstreamServiceException("Connection refused to upstream service"));

        mockMvc.perform(get("/product/1/similar")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message", is("Upstream service error: Connection refused to upstream service")));
    }
}
