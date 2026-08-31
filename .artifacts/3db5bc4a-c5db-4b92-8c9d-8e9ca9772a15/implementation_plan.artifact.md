# Implementation Plan - Fix Missing Binding for TransactionDao

The project is failing to build because `TransactionDao` is being injected without a Hilt qualifier in several places (`DataInitializer`, `TransactionGroupRepositoryImpl`), but it is only provided with `@MainDatabase` and `@ClonedDatabase` qualifiers in `DatabaseModule`.

## Proposed Changes

### [Component Name] :app

#### [MODIFY] [DatabaseModule.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/di/DatabaseModule.java)
- Add an unqualified `@Provides` method for `TransactionDao` that returns the DAO from the main database. This will satisfy injections that don't specify a qualifier.
- Keep the `@MainDatabase` and `@ClonedDatabase` qualified providers to support `TransactionRepositoryImpl` which specifically requests them.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:hiltJavaCompileDebug` to verify that the Hilt binding error is resolved.
- Run existing unit tests if available to ensure database functionality is intact.

### Manual Verification
- Deploy the app to a device/emulator and verify that data initialization and transaction grouping still work correctly.
