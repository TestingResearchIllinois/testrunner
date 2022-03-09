package edu.illinois.cs.xstream;

import com.thoughtworks.xstream.core.util.FastField;
import com.thoughtworks.xstream.mapper.ElementIgnoringMapper;
import com.thoughtworks.xstream.mapper.Mapper;

public class CustomElementIgnoringMapper extends ElementIgnoringMapper {
    public CustomElementIgnoringMapper(Mapper mapper) {
        super(mapper);
    }

    @Override
    public boolean shouldSerializeMember(final Class<?> definedIn, final String fieldName) {
        if (fieldsToOmit.contains(customKey(definedIn, fieldName))) {
            return false;
        } else if (definedIn == Object.class && isIgnoredElement(fieldName)) {
            return false;
        }
        try {
            // Hack to ignore field of type CodeSource
            System.out.println("PROCESS: " + definedIn.getName() + " - " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);

            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.security.CodeSource"))) {
                System.out.println("IGNORING CodeSource: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("sun.nio.cs.UTF_8$Decoder"))) {
                System.out.println("IGNORING UTF_8$Decoder: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.nio.charset.CharsetEncoder"))) {
                System.out.println("IGNORING CharsetEncoder: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.nio.charset.CharsetDecoder"))) {
                System.out.println("IGNORING CharsetDecoder: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("sun.nio.cs.StreamEncoder"))) {
                System.out.println("IGNORING StreamEncoder: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.util.zip.ZipCoder"))) {
                System.out.println("IGNORING ZipCoder: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("com.sun.crypto.provider.SunJCE"))) {
                System.out.println("IGNORING SunJCE: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.lang.ClassLoader"))) {
                System.out.println("IGNORING ClassLoader: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (Class.forName("java.lang.ClassLoader").isAssignableFrom(definedIn.getDeclaredField(fieldName).getType())) {
                System.out.println("IGNORING ClassLoader subclass: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.security.SecureClassLoader"))) {
                System.out.println("IGNORING SecureClassLoader: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("java.security.Provider"))) {
                System.out.println("IGNORING java.security.Provider: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
            if (definedIn.getDeclaredField(fieldName).getType().equals(Class.forName("javax.security.auth.Subject"))) {
                System.out.println("IGNORING auth.Subject: " + definedIn.getDeclaredField(fieldName).getType() + " - " + fieldName);
                return false;
            }
        } catch (Exception exception) {
            // ignore
            System.out.println("EXCEPTION IN IGNORING:" + fieldName + " - " + exception);
        }
        return super.shouldSerializeMember(definedIn, fieldName);
    }

    private FastField customKey(final Class<?> type, final String name) {
        return new FastField(type, name);
    }
}
