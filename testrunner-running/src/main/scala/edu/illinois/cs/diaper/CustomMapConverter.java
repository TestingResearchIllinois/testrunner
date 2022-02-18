package edu.illinois.cs.diaper;

import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
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
            // String clazz = item.getClass().getName();
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
}
