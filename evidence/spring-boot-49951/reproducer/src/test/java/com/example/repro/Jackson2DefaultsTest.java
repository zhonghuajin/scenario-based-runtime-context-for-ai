package com.example.repro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class Jackson2DefaultsTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void failOnUnknownPropertiesShouldBeFalse() {
        assertThat(jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .as("FAIL_ON_UNKNOWN_PROPERTIES should be disabled to match Spring Boot 3 behavior")
                .isFalse();
    }

    @Test
    void deserializePageResponseWithUnknownFieldsShouldNotFail() {
        String pageJson = """
                {
                    "content": ["item1", "item2"],
                    "totalPages": 1,
                    "totalElements": 2,
                    "number": 0,
                    "size": 20,
                    "pageable": {
                        "sort": { "sorted": false, "unsorted": true, "empty": true },
                        "offset": 0,
                        "pageNumber": 0,
                        "pageSize": 20,
                        "paged": true,
                        "unpaged": false
                    },
                    "sort": { "sorted": false, "unsorted": true, "empty": true },
                    "first": true,
                    "last": true,
                    "empty": false,
                    "numberOfElements": 2
                }
                """;

        // 构造带泛型的 JavaType
        var type = jsonMapper.getTypeFactory()
                .constructParametricType(SimplePageDto.class, String.class);

        assertThatNoException()
                .as("Deserializing JSON with unknown fields should succeed (SB3 compat)")
                .isThrownBy(() -> jsonMapper.readValue(pageJson, type));
    }
}