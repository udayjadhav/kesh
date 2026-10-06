package com.kesh.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolSchema(String type, Map<String, Property> properties, List<String> required) {

    public ToolSchema {
        type = type == null ? "object" : type;
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        required = required == null ? List.of() : List.copyOf(required);
    }

    public static ToolSchema object(Map<String, Property> properties, List<String> required) {
        return new ToolSchema("object", properties, required);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", type);
        Map<String, Object> props = new LinkedHashMap<>();
        properties.forEach((name, property) -> props.put(name, property.toMap()));
        root.put("properties", props);
        root.put("required", required);
        return root;
    }

    public record Property(String type, String description) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            if (description != null) {
                map.put("description", description);
            }
            return map;
        }
    }
}
