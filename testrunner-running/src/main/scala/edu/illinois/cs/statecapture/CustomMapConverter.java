package edu.illinois.cs.statecapture;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import java.util.Iterator;
import java.util.Map;

public class CustomMapConverter extends MapConverter {

    public CustomMapConverter(Mapper mapper) {
        super(mapper);
    }

    // Logic mostly copied from writeItem
    protected void writeItemWithName(String name, Object item, MarshallingContext context, HierarchicalStreamWriter writer) {
        if (item == null) {
            writeNullItem(context, writer);
        } else {
            String clazz = mapper().serializedClass(item.getClass());
            ExtendedHierarchicalStreamWriterHelper.startNode(writer, name, item.getClass());
            writer.addAttribute("class", clazz);  // Map the class as an attribute to the node
            writeBareItem(item, context, writer);
            writer.endNode();
        }
    }

    @Override
    public void marshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        Map map = (Map) source;
        String entryName = mapper().serializedClass(Map.Entry.class);
        for (Iterator iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            ExtendedHierarchicalStreamWriterHelper.startNode(writer, entryName, entry.getClass());

            // Give consistent name to elements (their types will be encoded as attribute)
            writeItemWithName("key", entry.getKey(), context, writer);
            writeItemWithName("value", entry.getValue(), context, writer);

            writer.endNode();
        }
    }

    @Override
    protected void putCurrentEntryIntoMap(final HierarchicalStreamReader reader, final UnmarshallingContext context,
                                          final Map<?, ?> map, final Map<?, ?> target) {
        final Object key = readCompleteItem(reader, context, map);
        UnmarshalChain.pushNode(UnmarshalChain.makeUnmarshalMapEntryNode(key)); // Try getting the key and putting it in the chain to map to the value
        Object value = null;
        int level = reader.getLevel();
        try {
            value = readCompleteItem(reader, context, map);
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
            while (reader.getLevel() > level) {
                reader.moveUp();
            }
            UnmarshalChain.popNode();
        }
        @SuppressWarnings("unchecked")
        final Map<Object, Object> targetMap = (Map<Object, Object>)target;
        targetMap.put(key, value);
    }
}
