package Evan.Application.Fitness.Web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Serializes chart specs for <script type="application/json"> blocks. "<" is
 * escaped so user-entered labels (exercise names...) can never close the tag.
 */
@Component
public class ChartJson {
    private final ObjectMapper mapper;

    public ChartJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object spec) {
        try {
            return mapper.writeValueAsString(spec).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Chart spec not serializable", e);
        }
    }

    public static Map<String, Object> spec(String type, Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                m.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return m;
    }

    public static Map<String, Object> series(String name, List<?> values, String color, String style) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("values", values);
        m.put("color", color);
        if (style != null) {
            m.put("style", style);
        }
        return m;
    }

    public static Map<String, Object> target(double value, String label) {
        return Map.of("value", value, "label", label);
    }

    /** Trailing moving average over the last {@code window} non-null values. */
    public static List<Double> rollingAverage(List<Double> values, int window) {
        List<Double> out = new ArrayList<>();
        Deque<Double> recent = new ArrayDeque<>();
        for (Double v : values) {
            if (v != null) {
                recent.addLast(v);
                if (recent.size() > window) {
                    recent.removeFirst();
                }
            }
            out.add(v == null || recent.isEmpty() ? null
                    : Math.round(recent.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
        }
        return out;
    }
}
