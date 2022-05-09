package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.mapper.Mapper;

import java.lang.reflect.Field;

public abstract class ReflectionConverterHelper {

    public static Object unmarshallField(final UnmarshallingContext context, final Object result, final Class<?> type,
                                     final Field field, final Mapper mapper) {
        // Assume properties define the root node and should be initialized as such if not yet
        UnmarshalChain.pushNode(UnmarshalChain.makeUnmarshalFieldNode(field.getDeclaringClass().getName(), field.getName()));
        try {
            return context.convertAnother(result, type, mapper.getLocalConverter(field.getDeclaringClass(), field
                    .getName()));
        } catch (ConversionException ce) {
            ce.printStackTrace();
            try {
                return UnmarshalChain.getCurrObject();
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                throw new ConversionException(e);
            }
        } finally {
            UnmarshalChain.popNode();
        }
    }
}
