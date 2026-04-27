package com.gjergjsheldija.dynamic;

import java.util.List;

public class DynamicResourceDefinition {
    private String name;
    private String profile;
    private List<FieldDefinition> fields;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public List<FieldDefinition> getFields() { return fields; }
    public void setFields(List<FieldDefinition> fields) { this.fields = fields; }

    public static class FieldDefinition {
        private String name;
        private String type; // e.g. "string", "token"
        private String shortDefinition;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getShortDefinition() { return shortDefinition; }
        public void setShortDefinition(String shortDefinition) { this.shortDefinition = shortDefinition; }
    }
}
