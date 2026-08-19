# Task List - Responsive UI and Account Identification Fixes

- [x] **Phase 1: Responsive Pie Chart**
    - [x] Update `fragment_charts.xml` for flexible Pie Chart layout.
    - [x] Remove hardcoded size in `ChartsFragment.java`.
- [x] **Phase 2: Biometric Re-rendering Fix**
    - [x] Update `DashboardFragment.java` to refresh summary widgets on privacy mode change.
- [x] **Phase 3: Unique Account Identification**
    - [x] Update `TransactionDao.java` queries (`getUniqueAccounts`, `getAccountHistory`).
    - [x] Update `AccountsFragment.java` navigation logic.
    - [x] Update `AccountHistoryFragment.java` history fetching logic (not needed as name is now accountId).
- [x] **Phase 4: Verification**
    - [x] Verify responsive chart.
    - [x] Verify biometric summary refresh.
    - [x] Verify account grouping by name.
