package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import edu.illinois.cs.xstream.UnmarshalChain;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

public class MapConverterHelper {

    private static Method findMethod(String methodName, Class clz) {
        if (clz == null) {
            return null;    // Should never happen, must have found this method
        }
        for (Method meth : clz.getDeclaredMethods()) {
            if (meth.getName().equals(methodName)) {
                return meth;
            }
        }
        return findMethod(methodName, clz.getSuperclass());
    }

    protected static void putCurrentEntryIntoMap(final HierarchicalStreamReader reader, final UnmarshallingContext context,
                                          final Map<?, ?> map, final Map<?, ?> target, MapConverter converter) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method readCompleteItem = findMethod("readCompleteItem", converter.getClass());
        readCompleteItem.setAccessible(true);
        final Object key = readCompleteItem.invoke(converter, reader, context, map);
        UnmarshalChain.pushNode(UnmarshalChain.makeUnmarshalMapEntryNode(key)); // Try getting the key and putting it in the chain to map to the value
        Object value = null;
        String nodeName = reader.getNodeName();
        try {
            value = readCompleteItem.invoke(converter, reader, context, map);
        } catch (ConversionException ce) {
            // If there is a problem getting the value from this map entry, use the value in the current heap
            try {
                value = UnmarshalChain.getCurrObject();
            } catch (UnmarshalChain.MapEntryMissingException e) {   // If map entry is simply missing in current heap, then return, don't update map
                return;
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                throw new ConversionException(e);
            }
        } finally {
            // Make sure level moves up to the proper location after this kind of exception
            while (!reader.getNodeName().equals(nodeName)) {
                reader.moveUp();
            }
            UnmarshalChain.popNode();
        }
        @SuppressWarnings("unchecked")
        final Map<Object, Object> targetMap = (Map<Object, Object>)target;
        targetMap.put(key, value);
    }
}
