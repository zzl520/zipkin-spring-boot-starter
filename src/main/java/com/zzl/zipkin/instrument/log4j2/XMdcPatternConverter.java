package com.zzl.zipkin.instrument.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringBuilders;
import org.apache.logging.log4j.util.TriConsumer;

/**
 * @author zzl on 2019/3/29.
 * @description 为了适配日志格式，为MDC添加默认值-
 * <p>
 * log4j2使用
 */
@Plugin(name = "XMdcPatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"XX", "mdc", "MDC"})
@PerformanceSensitive("allocation")
public final class XMdcPatternConverter extends LogEventPatternConverter {

    private static final ThreadLocal<StringBuilder> threadLocal = new ThreadLocal<>();
    private static final int DEFAULT_STRING_BUILDER_SIZE = 64;

    /**
     * Name of property to output.
     */
    private final String key;
    private final String[] keys;
    private final boolean full;

    /**
     * Private constructor.
     *
     * @param options options, may be null.
     */
    private XMdcPatternConverter(final String[] options) {
        super(options != null && options.length > 0 ? "MDC{" + options[0] + '}' : "MDC", "mdc");
        if (options != null && options.length > 0) {
            full = false;
            if (options[0].indexOf(',') > 0) {
                keys = options[0].split(",");
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = keys[i].trim();
                }
                key = null;
            } else {
                keys = null;
                key = options[0];
            }
        } else {
            full = true;
            key = null;
            keys = null;
        }
    }

    /**
     * Obtains an instance of PropertiesPatternConverter.
     *
     * @param options options, may be null or first element contains name of property to format.
     * @return instance of PropertiesPatternConverter.
     */
    public static XMdcPatternConverter newInstance(final String[] options) {
        return new XMdcPatternConverter(options);
    }

    private static final TriConsumer<String, Object, StringBuilder> WRITE_KEY_VALUES_INTO = new TriConsumer<String, Object, StringBuilder>() {
        @Override
        public void accept(final String key, final Object value, final StringBuilder sb) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(key).append('=');
            StringBuilders.appendValue(sb, value);
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        final ReadOnlyStringMap contextData = event.getContextData();
        // if there is no additional options, we output every single
        // Key/Value pair for the MDC in a similar format to Hashtable.toString()
        if (full) {
            if (contextData == null || contextData.size() == 0) {
                toAppendTo.append("{}");
                return;
            }
            appendFully(contextData, toAppendTo);
        } else {
            if (keys != null) {
                if (contextData == null || contextData.size() == 0) {
                    toAppendTo.append("{}");
                    return;
                }
                appendSelectedKeys(keys, contextData, toAppendTo);
            } else if (contextData != null) {
                // otherwise they just want a single key output
                final Object value = contextData.getValue(key);
                if (value != null) {
                    StringBuilders.appendValue(toAppendTo, value);
                } else {
                    //添加默认值
                    StringBuilders.appendValue(toAppendTo, "-");

                }
            }
        }
    }

    private static void appendFully(final ReadOnlyStringMap contextData, final StringBuilder toAppendTo) {
        final StringBuilder sb = getStringBuilder();
        sb.append("{");
        contextData.forEach(WRITE_KEY_VALUES_INTO, sb);
        sb.append('}');
        toAppendTo.append(sb);
        trimToMaxSize(sb);
    }

    private static void appendSelectedKeys(final String[] keys, final ReadOnlyStringMap contextData, final StringBuilder toAppendTo) {
        // Print all the keys in the array that have a value.
        final StringBuilder sb = getStringBuilder();
        sb.append("{");
        for (int i = 0; i < keys.length; i++) {
            final String theKey = keys[i];
            final Object value = contextData.getValue(theKey);
            if (value != null) { // !contextData.containskey(theKey)
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(theKey).append('=');
                StringBuilders.appendValue(sb, value);
            }
        }
        sb.append('}');
        toAppendTo.append(sb);
        trimToMaxSize(sb);
    }

    private static StringBuilder getStringBuilder() {
        StringBuilder result = threadLocal.get();
        if (result == null) {
            result = new StringBuilder(DEFAULT_STRING_BUILDER_SIZE);
            threadLocal.set(result);
        }
        result.setLength(0);
        return result;
    }

    private static void trimToMaxSize(final StringBuilder stringBuilder) {
        StringBuilders.trimToMaxSize(stringBuilder, Constants.MAX_REUSABLE_MESSAGE_SIZE);
    }
}
