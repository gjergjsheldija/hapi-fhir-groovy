# Caching of Search Results

## Description

Switch caching of Search Results from the Database table "hfj_search_result" to cache "SearchResultsCache" in Infinispan.

## Configuration

In the `.env` file or overridden in the environment those variables define what caching backend is used.

| entry                              | description                                                                    |
|------------------------------------|--------------------------------------------------------------------------------|
| CACHE_TYPE                         | INFINISPAN / DATABASE                                                          |
| INFINISPAN_ADDRESS                 | address of the Infinisspan server                                              |
| INFINISPAN_ADDRESS                 | address of the Infinisspan server                                              |
| INFINISPAN_USER                    | username of the Infinisspan server                                             |
| INFINISPAN_PASSWORD                | password of the Infinisspan server                                             |
| RETAIN_CACHED_SEARCHES_MINS        | for how many minutes to retain the caches                                      |
| REUSE_CACHED_SEARCH_RESULTS_MILLIS | for how much time to reuse the cached entries                                  |
| INFINISPAN_CREATE_CACHE            | force the creation of the cache if it does not exist, for **development only** |
| INFINISPAN_CACHE_NAME              | the name of the cache to use                                                   |

## Observability

When running, you may observe the usage stats in Grafana under Dashboards / Infinispan server :

http://localhost:3000/
