package ru.wisla.fm.common.api;

public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PageMeta of(int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageMeta(page, size, totalElements, totalPages);
    }
}
