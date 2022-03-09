package edu.illinois.cs.xstream;

import java.lang.reflect.Field;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.mapper.Mapper;


public class SerializableConverter extends com.thoughtworks.xstream.converters.reflection.SerializableConverter {

    public SerializableConverter(final Mapper mapper, final ReflectionProvider reflectionProvider, final ClassLoaderReference classLoaderReference) {
        super(mapper, reflectionProvider, classLoaderReference);
    }

    @Override
    protected Object unmarshallField(final UnmarshallingContext context, final Object result, final Class<?> type,
                                     final Field field) {
        return ReflectionConverterHelper.unmarshallField(context, result, type, field, mapper);
    }
}
