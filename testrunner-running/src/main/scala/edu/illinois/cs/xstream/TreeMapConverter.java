package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class TreeMapConverter extends com.thoughtworks.xstream.converters.collections.TreeMapConverter {

    public TreeMapConverter(Mapper mapper) {
        super(mapper);
    }

    @Override
    protected void putCurrentEntryIntoMap(final HierarchicalStreamReader reader, final UnmarshallingContext context,
                                          final Map<?, ?> map, final Map<?, ?> target) {
        try {
            MapConverterHelper.putCurrentEntryIntoMap(reader, context, map, target, this);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
