package com.github.t1.annotations.impl;

import com.github.t1.annotations.AmbiguousAnnotationResolutionException;
import com.github.t1.annotations.Annotations;
import com.github.t1.annotations.AnnotationsLoader;
import com.github.t1.annotations.MixinFor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.t1.annotations.impl.JandexAnnotations.proxy;
import static com.github.t1.annotations.impl.JandexAnnotationsLoader.findMethod;
import static com.github.t1.annotations.impl.RepeatableResolver.isRepeatable;
import static java.util.stream.Collectors.toList;

class MixinAnnotationsLoader extends AnnotationsLoader {

    private final IndexView jandex;
    private final AnnotationsLoader delegate;

    MixinAnnotationsLoader(IndexView jandex, AnnotationsLoader delegate) {
        this.jandex = jandex;
        this.delegate = delegate;
    }

    @Override public Annotations onType(Class<?> type) {
        List<Annotations> candidates = mixinsFor(type)
            .map(mixin -> mixin.target().asClass())
            .map(classInfo -> new MixinAnnotations(classInfo::classAnnotations, classInfo::classAnnotation, delegate.onType(type)))
            .collect(toList());
        return (candidates.isEmpty()) ? delegate.onType(type)
            : new MergedAnnotations(candidates);
    }

    @Override public Annotations onField(Class<?> type, String fieldName) {
        List<Annotations> candidates = mixinsFor(type)
            .map(mixin -> mixin.target().asClass().field(fieldName))
            .map(fieldInfo -> (fieldInfo == null) ? delegate.onField(type, fieldName)
                : new MixinAnnotations(fieldInfo::annotations, fieldInfo::annotation, delegate.onField(type, fieldName)))
            .collect(toList());
        return (candidates.isEmpty()) ? delegate.onField(type, fieldName)
            : new MergedAnnotations(candidates);
    }

    @Override public Annotations onMethod(Class<?> type, String methodName, Class<?>... argTypes) {
        List<Annotations> candidates = mixinsFor(type)
            .map(mixin -> mixin.target().asClass())
            .map(classInfo -> findMethod(classInfo, methodName, argTypes).orElse(null))
            .map(methodInfo -> (methodInfo == null) ? delegate.onMethod(type, methodName, argTypes)
                : new MixinAnnotations(methodInfo::annotations, methodInfo::annotation, delegate.onMethod(type, methodName, argTypes)))
            .collect(toList());
        return (candidates.isEmpty()) ? delegate.onMethod(type, methodName, argTypes)
            : new MergedAnnotations(candidates);
    }

    private Stream<AnnotationInstance> mixinsFor(Class<?> type) {
        return jandex.getAnnotations(MIXIN_FOR).stream()
            .filter(mixin -> matches(mixin, type));
    }

    private static final DotName MIXIN_FOR = DotName.createSimple(MixinFor.class.getName());

    private boolean matches(AnnotationInstance mixin, Class<?> type) {
        return mixin.value().asClass().name().toString().equals(type.getName());
    }

    private class MixinAnnotations implements Annotations {
        private final Supplier<Collection<AnnotationInstance>> all;
        private final Function<DotName, AnnotationInstance> get;
        private final Annotations other;

        MixinAnnotations(Supplier<Collection<AnnotationInstance>> all,
                         Function<DotName, AnnotationInstance> get,
                         Annotations other) {
            this.all = all;
            this.get = get;
            this.other = other;
        }

        @Override public List<Annotation> all() {
            return Stream.concat(
                all.get().stream().map(JandexAnnotations::proxy),
                other.all().stream())
                .collect(toList());
        }

        @Override public <T extends Annotation> Optional<T> get(Class<T> type) {
            DotName typeName = DotName.createSimple(type.getName());
            ClassInfo typeInfo = jandex.getClassByName(typeName);
            if (typeInfo != null && isRepeatable(typeInfo))
                throw new AmbiguousAnnotationResolutionException("The annotation " + type.getName()
                    + " is ambiguous on " + ". You should query it with `all` not `get`."); // TODO target info
            AnnotationInstance targetAnnotation = get.apply(typeName);
            if (targetAnnotation == null)
                return other.get(type);
            @SuppressWarnings("unchecked")
            T proxy = (T) proxy(targetAnnotation);
            return Optional.of(proxy);
        }

        @Override public <T extends Annotation> Stream<T> all(Class<T> type) {
            return Stream.concat(
                all.get().stream()
                    .filter(instance -> instance.name().toString().equals(type.getName()))
                    .map(JandexAnnotations::proxy)
                    .map(type::cast),
                other.all(type));
        }
    }
}
