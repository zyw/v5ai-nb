package xin.v5ai.nb.common.core.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * Tuple2 - A simple generic tuple class.
 * @param <T>
 * @param <S>
 */
@Data
@Builder
public class Tuple2<T,S> {
    private T first;
    private S second;
}
