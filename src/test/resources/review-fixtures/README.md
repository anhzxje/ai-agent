# Offline review fixtures

These small source repositories are data, not application dependencies or executable test targets. Never send the real repository to a provider as part of these automated tests.

| Repository | Contract and ground truth | Evidence |
| --- | --- | --- |
| vulnerable | `find` receives an untrusted account name. Concatenating it into SQL permits changing the query structure. A name containing `' OR '1'='1` demonstrates the issue under that input contract. | AccountRepository.java:5-6 |
| clean | The same input is bound as a JDBC parameter. This SQL-injection case must produce zero injection findings; statement and result set are closed by try-with-resources. | AccountRepository.java:4-8 |

The vulnerable fixture is intentionally unsafe. No database, HTTP endpoint or LLM is invoked. `ReviewQualityIntegrationTest` mocks model responses and exercises actual Spring AI tool execution, prompt composition, validation and metadata. It does **not** establish model precision or recall. Coverage of other vulnerability classes requires additional labelled fixtures before a real evaluation.
