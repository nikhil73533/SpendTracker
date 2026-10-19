# Tasks

- [x] **Refine Charts UI**
    - [x] Update `ChartPageFragment.java`:
        - [x] Always set `drawValues(true)` for PieCharts.
        - [x] Always set `setDrawEntryLabels(true)` for PieCharts.
        - [x] Refine `setupLineChart` and `setupBarChart` to show trend shapes even when masked.
- [x] **Fix Transaction Form Alignment**
    - [x] Update `fragment_transaction_form.xml`:
        - [x] Apply consistent padding to `et_sender`, `et_receiver`, and `et_bank_name`.
        - [x] Ensure horizontal padding (`paddingHorizontal="16dp"`) is present for alignment.
- [x] **Verification**
    - [x] Verify chart percentages are visible in privacy mode.
    - [x] Verify form fields are correctly aligned.
