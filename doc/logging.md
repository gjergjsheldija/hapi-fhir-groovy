# Logging

## Format

Logging is structured as JSON, in order to simplify parsing and filtering of the logs.
The structure of the log file is as below :

```json
{
  "time": "2025-07-02T11:44:02.761+02:00",
  "level": "INFO",
  "thread": "http-nio-auto-1-exec-1",
  "logger": "com.gjergjsheldija.logging.LoggingService",
  "message": "{\"operationType\":\"read\",\"operationName\":\"\",\"idOrResourceName\":\"Patient/1\",\"requestParameters\":null,\"requestBodyFhir\":\"\"}",
  "context": {},
  "id_resource_name": "Patient/1",
  "method": "GET",
  "operation_type": "read",
  "bytes_in": "-1",
  "latency_human": "29ms",
  "latency": "29",
  "uri": "/fhir",
  "request_parameters": "",
  "message_source": "REST",
  "client_id": "--",
  "operation_name": "",
  "remote_ip": "127.0.0.1",
  "bytes_out": "0",
  "request_body": "",
  "host": "http://localhost:37421/fhir/Patient/1",
  "request_id": "TZTSxm2hkU8BkZv1",
  "user_agent": "HAPI-FHIR/7.2.0 (FHIR Client; FHIR 4.0.1/R4; apache)",
  "status": "200"
}
```

## Configuration

In the body of the request can be enabled / disabled by changing the value of :

```yaml
hapi:
  fhir:
    log_request_body: true
```