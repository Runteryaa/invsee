# Project Rules

- **Pushing to GitHub:** Do NOT automatically push changes to GitHub. ALWAYS wait for the user's explicit permission or request to push. ("BEN SANA PUSHLA DEMEDEN PUSHLAMA")
- **Local Testing:** ALWAYS build and test the project locally with JAVA 25 (e.g. `./gradlew build`) BEFORE pushing to GitHub to catch compile errors.
- **Commit Messages & Changelogs:** 
  - Changelogs and commit messages must ALWAYS be in English.
  - When committing, you MUST write down EVERY SINGLE added or changed feature in the commit body (description) as a detailed list. This ensures that the GitHub Actions release pulls a comprehensive, fully English changelog.
- **Versioning (SemVer a.b.c):** 
  - ALWAYS increment the **C (Patch)** version for every minor request, fix, or feature the user asks for.
  - Increment the **B (Minor)** version for relatively large or significant updates.
  - NEVER increment the **A (Major)** version unless explicitly commanded by the user.
  - Automatically apply these increments to `mod_version` in `gradle.properties` when making changes.
