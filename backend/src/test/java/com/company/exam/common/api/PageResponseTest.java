package com.company.exam.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsContentAndPaginationFromPersistencePage() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 7);

        PageResponse<String> response = PageResponse.from(page, value -> "n" + value);

        assertThat(response.content()).containsExactly("n1", "n2");
        assertThat(response.pagination().page()).isEqualTo(1);
        assertThat(response.pagination().size()).isEqualTo(2);
        assertThat(response.pagination().totalElements()).isEqualTo(7);
        assertThat(response.pagination().totalPages()).isEqualTo(4);
    }

    @Test
    void nullContentBecomesEmptyImmutableList() {
        PageResponse<String> response = new PageResponse<>(null, new Pagination(0, 20, 0, 0));

        assertThat(response.content()).isEmpty();
    }
}
