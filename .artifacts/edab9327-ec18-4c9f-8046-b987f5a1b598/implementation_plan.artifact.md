# Implementation Plan - UI Refinement for Charts and Transaction Form

This plan addresses visibility issues in charts and alignment/padding issues in the transaction form.

## Proposed Changes

### 1. Chart Section Enhancements
#### [MODIFY] [ChartPageFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartPageFragment.java)
- **Visibility in Privacy Mode**: Enable values and entry labels in PieCharts even when masked, as `PercentFormatter` only shows percentages which are not sensitive.
- **Improved Visuals**: Ensure PieCharts have sufficient offsets and label positioning to avoid overlap.
- **Masking Strategy**: Refine masking to only hide absolute currency values and specific names, while keeping percentages and trend shapes visible.

### 2. Transaction Form Alignment
#### [MODIFY] [fragment_transaction_form.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_transaction_form.xml)
- **Padding Consistency**: Add consistent `paddingHorizontal="12dp"` and `paddingVertical="16dp"` to `et_sender`, `et_receiver`, and `et_bank_name`.
- **Remove Inconsistent Padding**: Remove the solo `paddingVertical="16dp"` from `et_sender` and apply a unified padding style to all `MaterialAutoCompleteTextView` components.
- **Alignment Fix**: Ensure placeholder text and input values align with the `TextInputLayout` borders correctly.

## Verification Plan

### Manual Verification
- **Charts**:
    - Enable privacy mode and verify that PieChart percentages are visible.
    - Verify that chart labels don't overlap.
- **Transaction Form**:
    - Check "Sender", "Receiver / Payee", and "Bank Name" fields.
    - Verify that placeholders and input text have a consistent left margin (padding) matching the "Category" and "Note" fields.
