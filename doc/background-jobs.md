# Background Jobs

[[_TOC_]]

The FHIR server has support for background jobs. There are two categories of scheduled jobs that run on the server :

- native
- custom

## Configuration

Enabling of disabling the running of a scheduled jobs can be done via the `JOB_SCHEDULING` environment variable.
It will disable **native** scheduled jobs only.

## Native jobs

The native jobs are enabled by default on the FHIR server, They are :

- Bulk import
- Bulk export
- Reindex
- Delete expunged
- Terminology index

### Getting a list of jobs

The server ships with two APIs that can be used to list and control those jobs, they are listed below.

`GET http://localhost:8080/control/jobs?pageStart=0&batchSize=200&jobStatus={STATUS}`

status can be one of :

- QUEUED
- IN_PROGRESS
- FINALIZE
- COMPLETED
- ERRORED
- FAILED
- CANCELLED

### Delete a job

Delete a running job can be done via :

`DELETE http://localhost:8080/control/jobs?instanceId=7c74a5c8-cc51-4f75-856d-ef2d6ea77479`

## Custom jobs

Custom jobs are based on the Spring task scheduling, explained [here](https://spring.io/guides/gs/scheduling-tasks)

:warning: **Note** : Those scripts are not persisted though application restarts and do not store their state.
Meaning they have to be restarted each time the server restarts

Storing, loading, and unloading them is the same as with other scripts is described here :  [scripts](scripting.md)

There are only two particularities that defines them, and that's the use of the `@EnableScheduling` annotation for the
class and `@Scheduled` for the main method. The following properties are supported for the `@Scheduled` annotation :

- fixedRate
- cron
- fixedDelay
- initialDelay

Job example :

```groovy
package com.gjergjsheldija.jobs;


import com.gjergjsheldija.scripting.api.CustomScript
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@CustomScript
@EnableScheduling
class BatchJobConfig {

    @Scheduled(fixedRate = 60000)
    void printHelloWorld() {
        println "Hello World!"
    }
}
```