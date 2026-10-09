package com.ccms.common;

import com.ccms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Running document numbers per contractor and year, e.g. {@code BILL-2026-0007}.
 * Uses MySQL's LAST_INSERT_ID(expr) trick so concurrent requests never get the same number.
 */
@Component
@RequiredArgsConstructor
public class DocNumbers {

    public enum Type {
        QUOTATION("Q"), WORK_ORDER("WO"), BILL("BILL");

        final String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }
    }

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Type type, LocalDate date) {
        int year = (date != null ? date : LocalDate.now()).getYear();
        jdbc.update("""
                INSERT INTO doc_sequence (contractor_id, doc_type, year_no, last_no) VALUES (?, ?, ?, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE last_no = LAST_INSERT_ID(last_no + 1)""",
                TenantContext.require(), type.name(), year);
        Long n = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return "%s-%d-%04d".formatted(type.prefix, year, n);
    }

    /** User-typed number if given, otherwise the next one in sequence. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String orNext(String given, Type type, LocalDate date) {
        return given != null && !given.isBlank() ? given.trim() : next(type, date);
    }
}
