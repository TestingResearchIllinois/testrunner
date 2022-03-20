package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.mapper.Mapper;

import java.lang.reflect.Field;

public class ReflectionConverter extends com.thoughtworks.xstream.converters.reflection.ReflectionConverter {

    public ReflectionConverter(final Mapper mapper, final ReflectionProvider reflectionProvider) {
        super(mapper, reflectionProvider);
    }

    @Override
    protected Object unmarshallField(final UnmarshallingContext context, final Object result, final Class<?> type,
                                     final Field field) {
        return ReflectionConverterHelper.unmarshallField(context, result, type, field, mapper);
    }


}