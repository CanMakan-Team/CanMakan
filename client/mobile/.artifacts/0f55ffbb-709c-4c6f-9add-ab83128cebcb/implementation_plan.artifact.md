# Migrating to JUnit 5 (Jupiter)

Add JUnit 5 support to the project to enable `@DisplayName` and other modern testing features, and migrate `DietaryRestrictionViewModelTest` to use JUnit 5.

## User Review Required

> [!IMPORTANT]
> This change introduces JUnit 5 as the primary testing framework. While JUnit 4 tests can still run via the vintage engine (if added), this plan focuses on migrating the specific test file requested to JUnit 5.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/ameli/Documents/GDipSA/AD/canmakan/client/mobile/gradle/libs.versions.toml)
- Add `junitJupiter` version.
- Add `junit-jupiter-api` and `junit-jupiter-engine` library definitions.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/ameli/Documents/GDipSA/AD/canmakan/client/mobile/app/build.gradle.kts)
- Add JUnit 5 dependencies to `testImplementation` and `testRuntimeOnly`.
- Configure the `test` task to `useJUnitPlatform()`.

---

### Test Migration

#### [MODIFY] [DietaryRestrictionViewModelTest.kt](file:///C:/Users/ameli/Documents/GDipSA/AD/canmakan/client/mobile/app/src/test/java/sg/edu/nus/iss/canmakan/features/dietaryprofile/restrictions/DietaryRestrictionViewModelTest.kt)
- Update imports from `org.junit` to `org.junit.jupiter.api`.
- Replace `@Before` with `@BeforeEach`.
- Replace `@After` with `@AfterEach`.
- Ensure all `@Test` annotations use the Jupiter version.
- Clean up any fully qualified references to `org.junit.jupiter.api.DisplayName`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:testDebugUnitTest` to verify that the migrated tests pass.
- Verify that the `DisplayName` annotations are correctly picked up by the test runner.
