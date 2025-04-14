package com.example.repro;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class ReproController {

    private final JsonMapper jsonMapper;

    public ReproController(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/check")
    public Map<String, Object> checkFeature() {
        boolean failOnUnknown = jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return Map.of(
            "FAIL_ON_UNKNOWN_PROPERTIES", failOnUnknown,
            "bug_reproduced", failOnUnknown,
            "message", failOnUnknown
                ? "BUG: FAIL_ON_UNKNOWN_PROPERTIES is TRUE (should be FALSE with jackson2 compat)"
                : "OK: FAIL_ON_UNKNOWN_PROPERTIES is FALSE"
        );
    }
}