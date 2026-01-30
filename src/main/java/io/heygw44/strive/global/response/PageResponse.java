package io.heygw44.strive.global.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        boolean hasNext
) {}
