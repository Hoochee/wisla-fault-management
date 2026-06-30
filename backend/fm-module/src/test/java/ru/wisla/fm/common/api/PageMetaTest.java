package ru.wisla.fm.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageMetaTest {

    @Test
    void computesTotalPages() {
        PageMeta meta = PageMeta.of(0, 50, 120);
        assertEquals(3, meta.totalPages());
        assertEquals(120, meta.totalElements());
    }
}
