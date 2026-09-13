package io.zershyan.damagestats.network.codec;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface StreamCodec<B, V> {
    V decode(B buffer);

    void encode(B buffer, V value);

    static <B, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Decoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(buffer, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V expectedValue) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return expectedValue;
            }

            @Override
            public void encode(B buffer, V value) {
                if(!expectedValue.equals(value)) {
                    throw new IllegalStateException("Can't encode '" + value + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> factory,
                                      Function<? super O, ? extends V> getter) {
        return of(
                (buffer, value) -> encode(buffer, getter.apply(value)),
                buffer -> factory.apply(decode(buffer)));
    }

    static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            Function<T1, C> factory) {
        return of(
                (buffer, value) -> codec1.encode(buffer, getter1.apply(value)),
                buffer -> factory.apply(codec1.decode(buffer)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            BiFunction<T1, T2, C> factory) {
        return of(
                (buffer, value) -> {
                    codec1.encode(buffer, getter1.apply(value));
                    codec2.encode(buffer, getter2.apply(value));
                },
                buffer -> factory.apply(codec1.decode(buffer), codec2.decode(buffer)));
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            Function3<T1, T2, T3, C> factory) {
        return of(
                (buffer, value) -> {
                    codec1.encode(buffer, getter1.apply(value));
                    codec2.encode(buffer, getter2.apply(value));
                    codec3.encode(buffer, getter3.apply(value));
                },
                buffer -> factory.apply(codec1.decode(buffer), codec2.decode(buffer), codec3.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            Function4<T1, T2, T3, T4, C> factory) {
        return of(
                (buffer, value) -> {
                    codec1.encode(buffer, getter1.apply(value));
                    codec2.encode(buffer, getter2.apply(value));
                    codec3.encode(buffer, getter3.apply(value));
                    codec4.encode(buffer, getter4.apply(value));
                },
                buffer -> factory.apply(codec1.decode(buffer), codec2.decode(buffer), codec3.decode(buffer),
                        codec4.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            Function5<T1, T2, T3, T4, T5, C> factory) {
        return of(
                (buffer, value) -> {
                    codec1.encode(buffer, getter1.apply(value));
                    codec2.encode(buffer, getter2.apply(value));
                    codec3.encode(buffer, getter3.apply(value));
                    codec4.encode(buffer, getter4.apply(value));
                    codec5.encode(buffer, getter5.apply(value));
                },
                buffer -> factory.apply(codec1.decode(buffer), codec2.decode(buffer), codec3.decode(buffer),
                        codec4.decode(buffer), codec5.decode(buffer)));
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
            Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return of(
                (buffer, value) -> {
                    codec1.encode(buffer, getter1.apply(value));
                    codec2.encode(buffer, getter2.apply(value));
                    codec3.encode(buffer, getter3.apply(value));
                    codec4.encode(buffer, getter4.apply(value));
                    codec5.encode(buffer, getter5.apply(value));
                    codec6.encode(buffer, getter6.apply(value));
                },
                buffer -> factory.apply(codec1.decode(buffer), codec2.decode(buffer), codec3.decode(buffer),
                        codec4.decode(buffer), codec5.decode(buffer), codec6.decode(buffer)));
    }

    @FunctionalInterface
    interface Encoder<B, V> {
        void encode(B buffer, V value);
    }

    @FunctionalInterface
    interface Decoder<B, V> {
        V decode(B buffer);
    }

    @FunctionalInterface
    interface CodecOperation<B, V, O> {
        StreamCodec<B, O> apply(StreamCodec<B, V> codec);
    }
}
