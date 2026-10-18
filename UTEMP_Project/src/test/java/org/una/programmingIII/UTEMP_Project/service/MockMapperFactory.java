package org.una.programmingIII.UTEMP_Project.service;

import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapper;
import org.una.programmingIII.UTEMP_Project.transformers.mappers.GenericMapperFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Services build their mappers through {@link GenericMapperFactory#createMapper}. In unit tests this factory hands
 * them the {@code @Mock GenericMapper<Entity, Dto>} field declared in the test class with the same type arguments.
 * <p>
 * Usage: {@code @Spy private GenericMapperFactory mapperFactory = new MockMapperFactory(this);}
 */
public class MockMapperFactory extends GenericMapperFactory {

    private final Object testInstance;

    public MockMapperFactory(Object testInstance) {
        this.testInstance = testInstance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E, D> GenericMapper<E, D> createMapper(Class<E> entityClass, Class<D> dtoClass) {
        for (Field field : testInstance.getClass().getDeclaredFields()) {
            if (field.getType() == GenericMapper.class
                    && field.getGenericType() instanceof ParameterizedType type
                    && matches(type.getActualTypeArguments(), entityClass, dtoClass)) {
                try {
                    field.setAccessible(true);
                    return (GenericMapper<E, D>) field.get(testInstance);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    private static boolean matches(Type[] arguments, Class<?> entityClass, Class<?> dtoClass) {
        return arguments.length == 2 && arguments[0] == entityClass && arguments[1] == dtoClass;
    }
}
