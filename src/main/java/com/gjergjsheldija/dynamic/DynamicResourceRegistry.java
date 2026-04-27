package com.gjergjsheldija.dynamic;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.JpaResourceDao;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import com.gjergjsheldija.configuration.ConfigurationResourceProvider;
import groovy.lang.GroovyClassLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.stereotype.Service;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DynamicResourceRegistry {

    private static final Logger ourLog = LoggerFactory.getLogger(DynamicResourceRegistry.class);

    public DynamicResourceRegistry() {
    }

    @Autowired
    private FhirContext myFhirContext;

    @Autowired
    private ApplicationContext myApplicationContext;

    @Autowired
    private RestfulServer myRestfulServer;

    @Autowired
    private DaoRegistry myDaoRegistry;

    @PostConstruct
    public void init() {
        ourLog.info("Initializing Dynamic Resource Registry...");
        List<DynamicResourceDefinition> definitions = loadDefinitions();
        for (DynamicResourceDefinition def : definitions) {
            registerResource(def);
        }
    }

    private List<DynamicResourceDefinition> loadDefinitions() {
        List<DynamicResourceDefinition> definitions = new ArrayList<>();
        try {
            Yaml yaml = new Yaml();
            ourLog.debug("Attempting to load custom-resources.yaml from classpath...");
            InputStream inputStream = getClass().getResourceAsStream("/custom-resources.yaml");
            if (inputStream == null) {
                ourLog.warn("custom-resources.yaml NOT FOUND in classpath!");
                ourLog.warn("No custom-resources.yaml found in classpath. Skipping dynamic resource registration.");
                return definitions;
            }
            ourLog.debug("custom-resources.yaml found. Parsing...");
            Map<String, Object> obj = yaml.load(inputStream);
            List<Map<String, Object>> resources = (List<Map<String, Object>>) obj.get("resources");
            ourLog.debug("Found {} resource entries in YAML", (resources != null ? resources.size() : 0));
            if (resources != null) {
                for (Map<String, Object> resMap : resources) {
                DynamicResourceDefinition def = new DynamicResourceDefinition();
                def.setName((String) resMap.get("name"));
                def.setProfile((String) resMap.get("profile"));
                List<Map<String, String>> fields = (List<Map<String, String>>) resMap.get("fields");
                List<DynamicResourceDefinition.FieldDefinition> fieldDefs = new ArrayList<>();
                if (fields != null) {
                    for (Map<String, String> fieldMap : fields) {
                        DynamicResourceDefinition.FieldDefinition fieldDef = new DynamicResourceDefinition.FieldDefinition();
                        fieldDef.setName(fieldMap.get("name"));
                        fieldDef.setType(fieldMap.get("type"));
                        fieldDef.setShortDefinition(fieldMap.get("short"));
                        fieldDefs.add(fieldDef);
                    }
                }
                def.setFields(fieldDefs);
                definitions.add(def);
            }
        }
        } catch (Exception e) {
            ourLog.error("Failed to load dynamic resource definitions", e);
        }
        return definitions;
    }

    private void registerResource(DynamicResourceDefinition def) {
        try {
            ourLog.info("Registering dynamic resource: {}", def.getName());

            // 1. Generate Class using Groovy (easiest way to get annotations at runtime)
            Class<?> resourceClass = generateResourceClass(def);

            // 2. Register with FhirContext
            myFhirContext.registerCustomType((Class<? extends IBaseResource>) resourceClass);

            // 3. Register DAO bean
            String daoBeanName = def.getName().toLowerCase() + "Dao";
            
            ConfigurableApplicationContext configContext = (ConfigurableApplicationContext) myApplicationContext;
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) configContext.getBeanFactory();

            GenericBeanDefinition daoDefinition = new GenericBeanDefinition();
            daoDefinition.setBeanClass(JpaResourceDao.class);
            daoDefinition.getPropertyValues().add("resourceType", resourceClass);
            daoDefinition.getPropertyValues().add("context", myFhirContext);
            beanFactory.registerBeanDefinition(daoBeanName, daoDefinition);
            
            IFhirResourceDao dao = (IFhirResourceDao) configContext.getBean(daoBeanName);
            myDaoRegistry.register(dao);

            // 4. Register Provider
            String providerBeanName = def.getName().toLowerCase() + "Provider";
            GenericBeanDefinition providerDefinition = new GenericBeanDefinition();
            providerDefinition.setBeanClass(DynamicResourceProvider.class);
            providerDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, resourceClass);
            providerDefinition.getPropertyValues().add("context", myFhirContext);
            providerDefinition.getPropertyValues().add("dao", dao);
            beanFactory.registerBeanDefinition(providerBeanName, providerDefinition);
            
            IResourceProvider provider = (IResourceProvider) configContext.getBean(providerBeanName);
            myRestfulServer.registerProvider(provider);

            ourLog.info("Successfully registered dynamic resource: {}", def.getName());
        } catch (Exception e) {
            ourLog.error("Failed to register dynamic resource: " + def.getName(), e);
        }
    }

    private Class<?> generateResourceClass(DynamicResourceDefinition def) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.gjergjsheldija.dynamic;\n");
        sb.append("import ca.uhn.fhir.model.api.annotation.*;\n");
        sb.append("import org.hl7.fhir.r4.model.*;\n");
        sb.append("import java.util.List;\n");
        sb.append("\n");
        sb.append("@ResourceDef(name = \"").append(def.getName()).append("\", profile = \"").append(def.getProfile()).append("\", id = \"").append(def.getName()).append("\")\n");
        sb.append("public class ").append(def.getName()).append(" extends DomainResource {\n");

        int order = 1;
        for (DynamicResourceDefinition.FieldDefinition field : def.getFields()) {
            sb.append("    @Child(name = \"").append(field.getName()).append("\", type = [StringType.class], order = ").append(order++).append(", min = 0, max = 1)\n");
            sb.append("    @Description(shortDefinition = \"").append(field.getShortDefinition()).append("\")\n");
            sb.append("    private StringType ").append(field.getName()).append(";\n\n");
            
            // Add getter/setter
            sb.append("    public String get").append(capitalize(field.getName())).append("() { return ").append(field.getName()).append(" == null ? null : ").append(field.getName()).append(".getValue(); }\n");
            sb.append("    public void set").append(capitalize(field.getName())).append("(String value) { if (this.").append(field.getName()).append(" == null) this.").append(field.getName()).append(" = new StringType(); this.").append(field.getName()).append(".setValue(value); }\n\n");
        }

        sb.append("    @Override\n");
        sb.append("    public ").append(def.getName()).append(" copy() { return new ").append(def.getName()).append("(); }\n");
        sb.append("    @Override\n");
        sb.append("    public ResourceType getResourceType() { return ResourceType.Basic; }\n");
        sb.append("}\n");
        String code = sb.toString();

        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());
        return loader.parseClass(code);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
