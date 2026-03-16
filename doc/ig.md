# Implementation Guide

# Description

Implementation Guides (IG's) are a way to adapt the FHIR specification to the needs of a particular system.

## Installation

There are three ways to install the IG in a FHIR server

### Local installation

Copy the generated tgz file in the directory `src/main/resources/ig`.
Change the `application.yaml` file to include the following and restart the server

```yaml
    implementationguides:
      mona:
        name: fhir.mona.icu.r4
        reloadExisting: false
        installMode: STORE_AND_INSTALL
        version: 0.0.1
        packageUrl: 'classpath:/ig/fhir.mona.icu.r4.tgz'
```

### Remote installation

Change the `application.yaml` file to include the following, being aware of the versioning

```yaml
    implementationguides:
      mona:
        name: fhir.mona.icu.r4
        reloadExisting: false
        installMode: STORE_AND_INSTALL
        version: x.y.z
        packageUrl: 'http://demo.com/fhir.demo.x.y.z.tgz '
```

### Dynamic installation of IG's

It's possible to install a FHIR Implementation Guide package (`package.tgz`) either from a published package or from a
local package with the `$install` operation, without having to restart the server. This is available for R4 and R5.

This feature must be enabled in the application.yaml:

```yaml
hapi:
  fhir:
    ig_runtime_upload_enabled: true
```

The `$install` operation is triggered with a POST to `[server]/ImplementationGuide/$install`, with the payload below:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "npmContent",
      "valueBase64Binary": "[BASE64_ENCODED_NPM_PACKAGE_DATA]"
    }
  ]
}
```

# Tools

In the `utils` folder, there are two shell scripts that can be used to install and uninstall an IG.

**Installing**

```shell
Usage: ./install.sh [options]

Options:
  --type <local|remote>  Specify 'local' or 'remote' to set the type.
  --url <URL>            Specify the URL of the FHIR server, ex http://localhost:8008.
  --file <filename>      Specify the filename, local : file.tgz or remote : http://server.com/file.tgz.
  --help                 Display this help message and exit.

```

**Uninstalling**

```shell
Usage: ./uninstall.sh [options]

Options:
  --url <URL>            Specify the URL of the FHIR server, ex http://localhost:8008.
  --ig_name              Specify the IG name, ex: fhir.mona.icu.r4
  --version              Specify the IG version, ex: 0.0.1
  --help                 Display this help message and exit.

```