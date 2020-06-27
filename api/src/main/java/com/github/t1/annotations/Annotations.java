package com.github.t1.annotations;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface Annotations {
    static Annotations on(Class<?> type) {
        return AnnotationsLoader.INSTANCE.onType(type);
    }

    static Annotations onField(Class<?> type, String fieldName) {
        return AnnotationsLoader.INSTANCE.onField(type, fieldName);
    }

    static Annotations onMethod(Class<?> type, String methodName, Class<?>... argTypes) {
        return AnnotationsLoader.INSTANCE.onMethod(type, methodName, argTypes);
    }

    /**
     * Get all {@link Annotation} instances.
     * If the annotation type is {@link java.lang.annotation.Repeatable}, the same type
     * can show up several times, eventually with different properties.
     */
    List<Annotation> all();

    /**
     * Get the 'strongest' {@link Annotation} instance of this type.
     * Multiple annotations may be applicable, e.g. from {@link MixinFor mixins}/
     * The annotation will be picked in this order:
     * <ol>
     *     <li>mixin</li>
     *     <li>target</li>
     *     <li>target stereotypes</li>
     *     <li>containing class (TODO not yet implemented)</li>
     *     <li>containing class stereotypes (TODO not yet implemented)</li>
     *     <li>containing package (TODO not yet implemented)</li>
     *     <li>containing package stereotypes (TODO not yet implemented)</li>
     * </ol>
     * If this order not sufficiently defined, e.g. because there are multiple repeatable annotations,
     * or because there are two mixins with the same annotation for the same class,
     * an {@link AmbiguousAnnotationResolutionException} is thrown.
     */
    <T extends Annotation> Optional<T> get(Class<T> type);

    /**
     * Get all (eventually {@link java.lang.annotation.Repeatable repeatable})
     * {@link Annotation} instances of this <code>type</code>.
     */
    <T extends Annotation> Stream<T> all(Class<T> type);
}
