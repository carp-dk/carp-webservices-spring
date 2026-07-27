# Export Section

The Export section of the project provides tools and functionalities for generating anonymous accounts and exporting data.

## 1. Anonymous Account Generation

**Purpose**: The Anonymous Account Generation feature allows the researchers/researcher assistants to create anonymous accounts that can be used in studies.

- **Functionality**:
    - **Account Creation**: Generates accounts without personal identifiers, ensuring that users can participate anonymously.
    - **Unique Identifiers**: Each anonymous account is assigned a universally unique identifier (UUID) to distinguish between accounts without revealing personal information.
    - **Usage Context**: Primarily used in research studies, trials, or any situation where user anonymity must be maintained.

- **Privacy Considerations**: This feature ensures compliance with data protection regulations by avoiding the storage or processing of personally identifiable information (PII) within these accounts.

### Account Cleanup Scheduling

Anonymous accounts are one-time-use: once their magic link expires the account can no longer be logged into, so it becomes dead weight in Keycloak. To allow these accounts to be reclaimed later, every fast-pipeline generation now records a **cleanup schedule** for the study.

- **What is recorded**: one row per study in the `anonymous_account_cleanup` table, holding a `delete_after` timestamp and a cumulative `account_count`. All accounts generated for a study via the fast pipeline are members of a single Keycloak group keyed by the study id, so the study is the natural cleanup unit.
- **`delete_after`**: the latest link expiry across all of the study's generations **plus a 30-day safety buffer** (`ExportAnonymousParticipants.CLEANUP_BUFFER`). It is *extended* on every subsequent generation ("reset the timer"), so a study's accounts only become eligible once the last-generated batch is well past expiry. The buffer cushions clock skew, late link redemption, and the cleanup job's cadence.
- **Coverage**: the schedule is keyed on accounts *created in Keycloak* (not just those the app could use), so accounts skipped during generation (e.g. a malformed response) are still covered.
- **Status**: this records the schedule only. The actual deletion — sweeping a study's Keycloak group members once `now > delete_after` and their sessions have ended — is planned but **not yet implemented**.

## 2. Study Data Export

**Purpose**: The Study Data Export feature facilitates the secure extraction of study-related data. This can include participant responses, study results, and other relevant data points.

- **Functionality**:
    - **Data Selection**: Allows users to select specific datasets or the entire study data for export.
    - **Export Formats**: A zip file containing JSON for data, with TXT for system logs, and other files in study resources.
    - **Batch Export**: Supports exporting data in bulk for large studies or trials.

- **Security Measures**:
    - **Access Controls**: Researchers and researcher assistants only.

