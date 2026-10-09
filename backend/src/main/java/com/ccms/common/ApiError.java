package com.ccms.common;

import java.util.List;
import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> params, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
