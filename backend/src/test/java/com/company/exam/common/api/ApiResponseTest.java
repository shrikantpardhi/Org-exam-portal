package com.company.exam.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApiResponseTest {

    @Test
    void okWrapsDataWithDefaultMessageAndNoPagination() {
        ApiResponse<String> response = ApiResponse.ok("value");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo(ApiResponse.DEFAULT_MESSAGE);
        assertThat(response.data()).isEqualTo("value");
        assertThat(response.pagination()).isNull();
    }

    @Test
    void okWithCustomMessageKeepsMessage() {
        ApiResponse<Integer> response = ApiResponse.ok("Created", 42);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Created");
        assertThat(response.data()).isEqualTo(42);
    }

    @Test
    void pagedCarriesContentAndPagination() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        ApiResponse<List<String>> response = ApiResponse.paged(PageResponse.from(page, Function.identity()));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly("a", "b");
        assertThat(response.pagination()).isNotNull();
        assertThat(response.pagination().totalElements()).isEqualTo(5);
        assertThat(response.pagination().totalPages()).isEqualTo(3);
        assertThat(response.pagination().page()).isZero();
        assertThat(response.pagination().size()).isEqualTo(2);
    }
}
