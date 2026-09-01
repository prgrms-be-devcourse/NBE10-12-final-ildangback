package com.gommit.global.dto;

import java.util.List;
import java.util.function.ToLongFunction;
import org.springframework.data.domain.Slice;

public record SliceResponse<T>(List<T> content, boolean hasNext, Long nextCursor) {

    public SliceResponse {
        content = (content == null) ? List.of() : List.copyOf(content);
    }

    public static <T> SliceResponse<T> of(Slice<T> slice) {
        return new SliceResponse<>(slice.getContent(), slice.hasNext(), null);
    }

    public static <T> SliceResponse<T> ofCursor(List<T> rows, int size, ToLongFunction<T> cursorOf) {
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 한다. 컨트롤러 파라미터에 @Min(1)이 있는지 확인할 것. size=" + size);
        }
        boolean hasNext = rows.size() > size;
        List<T> content = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext ? cursorOf.applyAsLong(content.get(content.size() - 1)) : null;
        return new SliceResponse<>(content, hasNext, nextCursor);
    }
}
