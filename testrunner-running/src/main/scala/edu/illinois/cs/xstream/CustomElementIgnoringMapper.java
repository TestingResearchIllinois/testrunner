package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.core.util.FastField;
import com.thoughtworks.xstream.mapper.ElementIgnoringMapper;
import com.thoughtworks.xstream.mapper.Mapper;

import java.util.HashSet;
import java.util.Set;

public class CustomElementIgnoringMapper extends ElementIgnoringMapper {
    public CustomElementIgnoringMapper(Mapper mapper) {
        super(mapper);
    }

    private static Set<String> ignoreList;

    private static Set<String> getIgnoreList() {
        if (ignoreList == null) {
            ignoreList = new HashSet<>();

            ignoreList.add("java.security.CodeSource");
            ignoreList.add("sun.nio.cs.UTF_8$Decoder");
            ignoreList.add("java.nio.charset.CharsetEncoder");
            ignoreList.add("java.nio.charset.CharsetDecoder");
            ignoreList.add("sun.nio.cs.StreamEncoder");
            ignoreList.add("java.util.zip.ZipCoder");
            ignoreList.add("com.sun.crypto.provider.SunJCE");
            ignoreList.add("java.lang.ClassLoader");
            ignoreList.add("java.security.SecureClassLoader");
            ignoreList.add("java.security.Provider");
            ignoreList.add("javax.security.auth.Subject");
        }
        return ignoreList;
    }

    {
        getIgnoreList();
    }
    @Override
    public boolean shouldSerializeMember(final Class definedIn, final String fieldName) {
        if (fieldsToOmit.contains(customKey(definedIn, fieldName))) {
            return false;
        } else if (definedIn == Object.class && isIgnoredElement(fieldName)) {
            return false;
        }
        try {
            // Hack to ignore field of type CodeSource
            for(String ignoreItem : ignoreList) {
                if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName(ignoreItem))) {
                    return false;
                }
            }
            if (Class.forName("java.lang.ClassLoader").isAssignableFrom(definedIn.getDeclaredField(fieldName).getType())) {
                return false;
            }
        } catch (Exception exception) {
            // ignore
        }
        return super.shouldSerializeMember(definedIn, fieldName);
    }

    private FastField customKey(final Class<?> type, final String name) {
        return new FastField(type, name);
    }
}
