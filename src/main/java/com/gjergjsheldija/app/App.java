package com.gjergjsheldija.app;

import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.r4.model.BackboneElement;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import java.util.ArrayList;
import java.util.List;

/**
 * An external service that serves custom endpoints. Operations are routed by
 * {@code AppProxyFilter}; see doc/apps.md.
 */
@ResourceDef(name = "App", profile = "http://fhir.demo/StructureDefinition/App", id = "App")
public class App extends DomainResource {

	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_INACTIVE = "inactive";
	public static final String ENDPOINT_TYPE_HTTP_RPC = "http-rpc";

	@Child(name = "status", type = {CodeType.class}, order = 0, min = 1, max = 1, summary = true)
	@Description(shortDefinition = "active | inactive")
	protected CodeType status;

	@Child(name = "endpoint", order = 1, min = 1, max = 1, summary = true)
	@Description(shortDefinition = "How the server reaches the app")
	protected AppEndpointComponent endpoint;

	@Child(name = "operation", order = 2, min = 1, max = Child.MAX_UNLIMITED, summary = true)
	@Description(shortDefinition = "Endpoints the app serves")
	protected List<AppOperationComponent> operation;

	public String getStatus() {
		return status == null ? null : status.getValue();
	}

	public App setStatus(String value) {
		this.status = value == null ? null : new CodeType(value);
		return this;
	}

	public AppEndpointComponent getEndpoint() {
		return endpoint;
	}

	public App setEndpoint(AppEndpointComponent value) {
		this.endpoint = value;
		return this;
	}

	public List<AppOperationComponent> getOperation() {
		if (operation == null) {
			operation = new ArrayList<>();
		}
		return operation;
	}

	public App addOperation(AppOperationComponent value) {
		getOperation().add(value);
		return this;
	}

	@Override
	public App copy() {
		App dst = new App();
		copyValues(dst);
		dst.status = status == null ? null : status.copy();
		dst.endpoint = endpoint == null ? null : endpoint.copy();
		if (operation != null) {
			dst.operation = new ArrayList<>();
			for (AppOperationComponent next : operation) {
				dst.operation.add(next.copy());
			}
		}
		return dst;
	}

	@Override
	public ResourceType getResourceType() {
		// Custom types have no ResourceType constant; Configuration uses Basic the same way.
		return ResourceType.Basic;
	}

	@Override
	public String fhirType() {
		return "App";
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty() && ElementUtil.isEmpty(status, endpoint, operation);
	}

	@Block
	public static class AppEndpointComponent extends BackboneElement {

		@Child(name = "type", type = {CodeType.class}, order = 0, min = 1, max = 1)
		@Description(shortDefinition = "http-rpc")
		protected CodeType type;

		@Child(name = "url", type = {UriType.class}, order = 1, min = 1, max = 1)
		@Description(shortDefinition = "Base URL the server POSTs operation envelopes to")
		protected UriType url;

		@Child(name = "secret", type = {StringType.class}, order = 2, min = 0, max = 1)
		@Description(shortDefinition = "Password for Basic auth (user = App id) on calls to the app")
		protected StringType secret;

		public String getType() {
			return type == null ? null : type.getValue();
		}

		public AppEndpointComponent setType(String value) {
			this.type = value == null ? null : new CodeType(value);
			return this;
		}

		public String getUrl() {
			return url == null ? null : url.getValue();
		}

		public AppEndpointComponent setUrl(String value) {
			this.url = value == null ? null : new UriType(value);
			return this;
		}

		public String getSecret() {
			return secret == null ? null : secret.getValue();
		}

		public AppEndpointComponent setSecret(String value) {
			this.secret = value == null ? null : new StringType(value);
			return this;
		}

		@Override
		public AppEndpointComponent copy() {
			AppEndpointComponent dst = new AppEndpointComponent();
			copyValues(dst);
			dst.type = type == null ? null : type.copy();
			dst.url = url == null ? null : url.copy();
			dst.secret = secret == null ? null : secret.copy();
			return dst;
		}

		@Override
		public boolean isEmpty() {
			return super.isEmpty() && ElementUtil.isEmpty(type, url, secret);
		}
	}

	/**
	 * The operation id is the FHIR element id ({@code "id"} in JSON), inherited from Element.
	 */
	@Block
	public static class AppOperationComponent extends BackboneElement {

		@Child(name = "method", type = {CodeType.class}, order = 0, min = 1, max = 1)
		@Description(shortDefinition = "GET | POST | PUT | DELETE | PATCH")
		protected CodeType method;

		@Child(name = "path", type = {StringType.class}, order = 1, min = 1, max = 1)
		@Description(shortDefinition = "Path under the FHIR base, e.g. Patient/$daily-report/{date}")
		protected StringType path;

		@Child(name = "public", type = {BooleanType.class}, order = 2, min = 0, max = 1)
		@Description(shortDefinition = "If true, no access token is required")
		protected BooleanType isPublic;

		public String getMethod() {
			return method == null ? null : method.getValue();
		}

		public AppOperationComponent setMethod(String value) {
			this.method = value == null ? null : new CodeType(value);
			return this;
		}

		public String getPath() {
			return path == null ? null : path.getValue();
		}

		public AppOperationComponent setPath(String value) {
			this.path = value == null ? null : new StringType(value);
			return this;
		}

		public boolean isPublic() {
			return isPublic != null && Boolean.TRUE.equals(isPublic.getValue());
		}

		public AppOperationComponent setPublic(boolean value) {
			this.isPublic = new BooleanType(value);
			return this;
		}

		@Override
		public AppOperationComponent copy() {
			AppOperationComponent dst = new AppOperationComponent();
			copyValues(dst);
			dst.method = method == null ? null : method.copy();
			dst.path = path == null ? null : path.copy();
			dst.isPublic = isPublic == null ? null : isPublic.copy();
			return dst;
		}

		@Override
		public boolean isEmpty() {
			return super.isEmpty() && ElementUtil.isEmpty(method, path, isPublic);
		}
	}
}
