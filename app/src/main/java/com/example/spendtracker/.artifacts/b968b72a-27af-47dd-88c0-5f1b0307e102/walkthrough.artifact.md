# Walkthrough - Spend Tracker Updates

This update delivers significant improvements to data visualization, navigation consistency, category management, and a new robust security layer.

## Security & Privacy Layer

> [!IMPORTANT]
> **End-to-End Local Encryption**: All transaction data is now stored in an encrypted database using **SQLCipher**. The encryption key is securely managed via the **Android KeyStore**.
>
> **Migration Support**: We've implemented a seamless migration path. If you have existing unencrypted data, the app will automatically encrypt it on first launch, ensuring no data is lost while upgrading to the new security model. A backup of the old unencrypted database is kept at `spend_tracker_db.bak` as an extra precaution.

- **Privacy Mode**: By default, all monetary amounts are masked as `***`.
- **Biometric Unlock**: Long-pressing the **"+" (Add Transaction)** button on the dashboard triggers a biometric prompt (Fingerprint, Face, or PIN/Pattern). Upon successful authentication, actual amounts are revealed across all screens.

## Charts & Analytics Fixes

- **Pie Chart Enhancements**:
    - Increased height to **200dp** for better visibility.
    - Labels for **0% categories are hidden** to reduce clutter.
    - Improved label distribution and connection lines using `OUTSIDE_SLICE` positioning and adjusted offsets.
- **Immediate Refresh**: Charts now update instantly when toggling between **Income** and **Expense**.
- **Granularity & Date State**:
    - Navigation arrows now move by **Month** consistently on both Daily and Charts screens.
    - **Weekly Trend** labels now show human-readable date ranges (e.g., *03 Aug – 09 Aug*) instead of week numbers.
    - Headers dynamically update based on granularity (Monthly, Yearly, or Weekly ranges).
- **Trend Logic**: The Trend chart now correctly shows the **Previous 2 periods + Current period** for better historical context.

## Category Management

- **Integrated CRUD**: Category management has been moved from the "More" section directly into the **Transaction Form**.
- **Safe Renaming**: Renaming a category now triggers a bulk update across all historical transactions, ensuring data integrity.
- **Typed Categories**: Income and Expense categories are now clearly differentiated and consistently displayed.

## Account & Recent Payments

- **Modern Redesign**: The "Account" section has been transformed into a **Recent Contacts** list.
- **Unique Identification**: Transactions are grouped by **UPI ID** or **Sender/Receiver name**, preventing duplicates for the same contact.
- **Chat-Style History**: Clicking a contact opens a messaging-style transaction history, chronologically sorted with clear income/expense bubbles.
- **Monthly Filter**: The contact history supports the standard monthly filter used throughout the app.

## Excel Export Fix

- Fixed the crash in the Excel export flow.
- Integrated `FileProvider` for secure and reliable sharing of the generated `.xlsx` file.
- Added handling for empty datasets and large exports.

## Technical Details

- **Database**: Room + SQLCipher (`net.zetetic:android-database-sqlcipher`).
- **Security**: `androidx.security:security-crypto` for preference encryption and key management.
- **Authentication**: `androidx.biometric:biometric` for cross-platform hardware authentication.
- **Charts**: `MPAndroidChart` with customized layout parameters for overlap prevention.
