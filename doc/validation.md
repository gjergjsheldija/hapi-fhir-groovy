# Validation

The HAPI server performs validation via the `hapi.validation.requests_enabled` variable. This variable
is set to the value of `REQUEST_VALIDATION`.
Due to our internal need we had to implement a custom resource, named `Configuration`. This made
the usage the default validation classes impossible.
For this reason we implemented a custom interceptor called `com.clinomic.valiadation.CustomRequestValidatingInterceptor`
and loaded in the `application.yaml` file.