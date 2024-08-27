/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-06-06
 */

package com.clinomic.configuration;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.api.annotation.SearchParamDefinition;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.utilities.Utilities;

import java.util.List;

@ResourceDef(name = "Configuration", profile = "http://fhir.mona.icu/StructureDefinition/Configuration", id = "Configuration")
public class Configuration extends DomainResource implements IBaseResource {

	public enum ConfigurationStatus {
		/**
		 * This account is active and may be used.
		 */
		ACTIVE,
		/**
		 * This account is inactive and should not be used to track financial information.
		 */
		INACTIVE;

		public static Configuration.ConfigurationStatus fromCode(String codeString) throws FHIRException {
			if (codeString == null || "".equals(codeString)) return null;
			if ("active".equals(codeString)) return ACTIVE;
			if ("inactive".equals(codeString)) return INACTIVE;
			else throw new FHIRException("Unknown ConfigurationStatus code '" + codeString + "'");
		}

		public String toCode() {
			switch (this) {
				case ACTIVE:
					return "active";
				case INACTIVE:
					return "inactive";
				default:
					return "?";
			}
		}

		public String getSystem() {
			switch (this) {
				case ACTIVE:
					return "http://hl7.org/fhir/account-status";
				case INACTIVE:
					return "http://hl7.org/fhir/account-status";
				default:
					return "?";
			}
		}

		public String getDefinition() {
			switch (this) {
				case ACTIVE:
					return "This configuration is active and may be used.";
				case INACTIVE:
					return "This configuration is inactive and should not be used.";
				default:
					return "?";
			}
		}

		public String getDisplay() {
			switch (this) {
				case ACTIVE:
					return "active";
				case INACTIVE:
					return "inactive";
				default:
					return "?";
			}
		}
	}

	public static class ConfigurationStatusEnumFactory implements EnumFactory<Configuration.ConfigurationStatus> {
		public Configuration.ConfigurationStatus fromCode(String codeString) throws IllegalArgumentException {
			if (codeString == null || "".equals(codeString))
				if (codeString == null || "".equals(codeString)) return null;
			if ("active".equals(codeString)) return Configuration.ConfigurationStatus.ACTIVE;
			if ("inactive".equals(codeString)) return Configuration.ConfigurationStatus.INACTIVE;
			throw new IllegalArgumentException("Unknown ConfigurationStatus code '" + codeString + "'");
		}

		public Enumeration<Configuration.ConfigurationStatus> fromType(Base code) throws FHIRException {
			if (code == null) return null;
			if (code.isEmpty()) return new Enumeration<Configuration.ConfigurationStatus>(this);
			String codeString = ((PrimitiveType) code).asStringValue();
			if (codeString == null || "".equals(codeString)) return null;
			if ("active".equals(codeString))
				return new Enumeration<Configuration.ConfigurationStatus>(this, Configuration.ConfigurationStatus.ACTIVE);
			if ("inactive".equals(codeString))
				return new Enumeration<Configuration.ConfigurationStatus>(this, Configuration.ConfigurationStatus.INACTIVE);
			throw new FHIRException("Unknown ConfigurationStatus code '" + codeString + "'");
		}

		public String toCode(Configuration.ConfigurationStatus code) {
			if (code == Configuration.ConfigurationStatus.ACTIVE) return "active";
			if (code == Configuration.ConfigurationStatus.INACTIVE) return "inactive";
			return "?";
		}

		public String toSystem(Configuration.ConfigurationStatus code) {
			return code.getSystem();
		}
	}

	public enum ConfigurationType {
		/**
		 * This resource is a Groovy script
		 */
		SCRIPT,
		/**
		 * This resource is a YAML configuration entry
		 */
		CONFIGURATION;

		public static Configuration.ConfigurationType fromCode(String codeString) throws FHIRException {
			if (codeString == null || "".equals(codeString)) return null;
			if ("script".equals(codeString)) return SCRIPT;
			if ("configuration".equals(codeString)) return CONFIGURATION;
			else throw new FHIRException("Unknown ConfigurationType code '" + codeString + "'");
		}

		public String toCode() {
			switch (this) {
				case SCRIPT:
					return "script";
				case CONFIGURATION:
					return "configuration";
				default:
					return "?";
			}
		}

		public String getSystem() {
			switch (this) {
				case SCRIPT:
					return "http://hl7.org/fhir/account-status";
				case CONFIGURATION:
					return "http://hl7.org/fhir/account-status";
				default:
					return "?";
			}
		}

		public String getDefinition() {
			switch (this) {
				case SCRIPT:
					return "This resource is a Groovy script.";
				case CONFIGURATION:
					return "This resource is a YAML configuration entry.";
				default:
					return "?";
			}
		}

		public String getDisplay() {
			switch (this) {
				case SCRIPT:
					return "script";
				case CONFIGURATION:
					return "configuration";
				default:
					return "?";
			}
		}
	}

	public static class ConfigurationTypeEnumFactory implements EnumFactory<Configuration.ConfigurationType> {
		public Configuration.ConfigurationType fromCode(String codeString) throws IllegalArgumentException {
			if (codeString == null || "".equals(codeString))
				if (codeString == null || "".equals(codeString)) return null;
			if ("script".equals(codeString)) return ConfigurationType.SCRIPT;
			if ("configuration".equals(codeString)) return ConfigurationType.CONFIGURATION;
			throw new IllegalArgumentException("Unknown ConfigurationType code '" + codeString + "'");
		}

		public Enumeration<Configuration.ConfigurationType> fromType(Base code) throws FHIRException {
			if (code == null) return null;
			if (code.isEmpty()) return new Enumeration<Configuration.ConfigurationType>(this);
			String codeString = ((PrimitiveType) code).asStringValue();
			if (codeString == null || "".equals(codeString)) return null;
			if ("script".equals(codeString))
				return new Enumeration<Configuration.ConfigurationType>(this, ConfigurationType.SCRIPT);
			if ("configuration".equals(codeString))
				return new Enumeration<Configuration.ConfigurationType>(this, ConfigurationType.CONFIGURATION);
			throw new FHIRException("Unknown ConfigurationType code '" + codeString + "'");
		}

		public String toCode(Configuration.ConfigurationType code) {
			if (code == ConfigurationType.SCRIPT) return "script";
			if (code == ConfigurationType.CONFIGURATION) return "configuration";
			return "?";
		}

		public String toSystem(Configuration.ConfigurationType code) {
			return code.getSystem();
		}
	}

	@Child(name = "identifier", type = {Identifier.class}, order = 0, min = 0, max = Child.MAX_UNLIMITED, modifier = false, summary = true)
	@Description(shortDefinition = "Logical identifier of this artifact")
	protected List<Identifier> identifier;

	@Child(name = "name", type = {StringType.class}, order = 1, min = 1, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "The name of the configuration")
	private StringType name;

	@Child(name = "body", type = {StringType.class}, order = 2, min = 1, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "Content of the configuration")
	protected StringType body;

	@Child(name = "defaultValue", type = {StringType.class}, order = 3, min = 0, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "The default value of the configuration")
	private StringType defaultValue;

	@Child(name = "status", type = {CodeType.class}, order = 4, min = 1, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "The status of the configuration (enabled or disabled)")
	protected Enumeration<Configuration.ConfigurationStatus> status;

	@Child(name = "description", type = {StringType.class}, order = 5, min = 0, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "A description of the configuration")
	private StringType description;

	@Child(name = "type", type = {CodeType.class}, order = 6, min = 1, max = 1, modifier = false, summary = true)
	@Description(shortDefinition = "The type of the configuration (script or configuration)")
	protected Enumeration<Configuration.ConfigurationType> type;

	@SearchParamDefinition(
		name = "name",
		path = "Configuration.name",
		description = "Search for a Configuration by name",
		type = "string"
	)
	public static final String SP_NAME = "name";
	public static final StringClientParam NAME = new StringClientParam(SP_NAME);

	@SearchParamDefinition(
		name = "type",
		path = "Configuration.type",
		description = "Search for a Configuration by type",
		type = "token"
	)
	public static final String SP_TYPE = "type";
	public static final StringClientParam TYPE = new StringClientParam(SP_TYPE);

	@SearchParamDefinition(
		name = "status",
		path = "Configuration.status",
		description = "Search for a Configuration by status",
		type = "token"
	)
	public static final String SP_STATUS = "status";
	public static final StringClientParam STATUS = new StringClientParam(SP_STATUS);

	@Override
	public Configuration copy() {
		Configuration copy = new Configuration();
		copy.identifier = identifier;
		copy.name = name;
		copy.defaultValue = defaultValue;
		copy.body = body;
		copy.status = status;
		copy.description = description;
		copy.type = type;
		return copy;
	}

	@Override
	public ResourceType getResourceType() {
		return ResourceType.Basic;
	}

	public List<Identifier> getIdentifier() {
		return identifier;
	}

	public Configuration setIdentifier(List<Identifier> identifier) {
		this.identifier = identifier;
		return this;
	}

	public String getName() {
		return name.getValue();
	}

	public Configuration setName(String value) {
		if (Utilities.noString(value))
			this.name = null;
		else {
			if (this.name == null)
				this.name = new StringType();
			this.name.setValue(value);
		}
		return this;
	}

	public StringType getBody() {
		return body;
	}

	public Configuration setBody(StringType body) {
		this.body = body;
		return this;
	}

	public StringType getDescription() {
		return description;
	}

	public Configuration setDescription(StringType description) {
		this.description = description;
		return this;
	}

	public ConfigurationStatus getStatus() {
		return this.status == null ? null : status.getValue();
	}

	public Configuration setStatus(ConfigurationStatus value) {
		if (this.status == null)
			this.status = new Enumeration<>(new ConfigurationStatusEnumFactory());
		this.status.setValue(value);
		return this;
	}

	public ConfigurationType getType() {
		return this.type == null ? null : type.getValue();
	}

	public Configuration setType(ConfigurationType value) {
		if (this.type == null)
			this.type = new Enumeration<>(new ConfigurationTypeEnumFactory());
		this.type.setValue(value);
		return this;
	}

	public boolean isEmpty() {
		return super.isEmpty() && ca.uhn.fhir.util.ElementUtil.isEmpty(identifier, status, type, name, description, body);
	}

	@Override
	public Base[] getProperty(int hash, String name, boolean checkValid) throws FHIRException {
		switch (hash) {
			case -1618432855: /*identifier*/
				return this.identifier == null ? new Base[0] : this.identifier.toArray(new Base[this.identifier.size()]); // Identifier
			case 3373707: /*name*/
				return this.name == null ? new Base[0] : new Base[]{this.name}; // StringType
			case -892481550: /*status*/
				return this.status == null ? new Base[0] : new Base[]{this.status}; // Enumeration<ConfigurationStatus>
			case 3575610: /*type*/
				return this.type == null ? new Base[0] : new Base[]{this.type}; // Enumeration<ConfigurationType>
			case -1724546052: /*description*/
				return this.description == null ? new Base[0] : new Base[]{this.description}; // StringType
			default:
				return super.getProperty(hash, name, checkValid);
		}

	}

}