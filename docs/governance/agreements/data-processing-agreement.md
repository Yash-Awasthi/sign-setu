# Data Processing Agreement (template)

Between <Department> ("Fiduciary") and <ISL team / ML team / cloud provider / other> ("Processor"). Use one per processor. Draft for legal review.

| Clause | Term |
| --- | --- |
| 1. Purpose | Processor processes only the data listed in Annex A, only to provide the service in Annex B, only on the Fiduciary's instructions. |
| 2. No retention | Request content (text, audio, landmarks) is processed in memory and not stored, logged or used for training, unless the Fiduciary agrees in writing. |
| 3. Location | Processing and any logs stay in India. |
| 4. Security | Processor applies the CERT-In Guidelines on Information Security Practices for Government Entities: TLS 1.2+, access control with MFA, logging of access (metadata only) for 180 days, NTP sync to NIC/NPL, patching. |
| 5. Sub-processors | None without prior written approval. |
| 6. Personnel | Access limited to named staff bound by confidentiality, aware of JJ Act section 74. |
| 7. Incidents | Notify the Fiduciary within 2 hours of noticing any incident affecting the service or data, to allow CERT-In reporting within 6 hours. |
| 8. Audit | Fiduciary or its auditor may audit; Processor shares its latest security audit report and SBOM (CERT-In 8.4, 8.5). |
| 9. Vulnerabilities | Processor informs the Fiduciary of identified vulnerabilities in its component within 7 days. |
| 10. Change | Interface or data changes need 30 days' notice and a versioned API (Open API policy). |
| 11. Termination | On end of agreement, Processor deletes any Fiduciary data and confirms in writing. |
| 12. Cost | <Nil / as agreed> |

**Annex A, data:** <e.g. landmark arrays: 33 pose + 2 x 21 hand points per frame, no images>
**Annex B, service:** <e.g. sign recognition, endpoint, availability target>
**Contacts:** <technical, security, legal>

Signed for Fiduciary: ________  Signed for Processor: ________  Date: ________
