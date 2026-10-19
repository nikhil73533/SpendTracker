# Daily, Monthly and Total — Charcoal & Brass

## Research completed before implementation (2026-09-25)

Read README, all docs/*.md, existing colors/themes, dashboard and row layouts, and current dashboard controllers. No AGENTS.md, separate brand guide, or other UX/style documentation was found. Preserve transaction selection, swipe-to-Trash/Undo, privacy masking, category editing, account drill-down and report authentication from the existing functional docs.

Reviewed the current publicly available RealByte site and both official Home help articles, including rendered Daily, Monthly and Total screenshots. These are published references (the screenshots contain 2020 example data), not a claim of having tested the latest installed app.

- https://www.realbyteapps.com/ (Daily screenshot: images/main/img_sreen_light1.png)
- https://help.realbyteapps.com/hc/en-us/articles/360046150874-Home-tab (Monthly: Home5__1_.png; Total: Home6__1_.png)
- https://help.realbyteapps.com/hc/en-us/articles/360043325533-Home-tab
- https://developer.android.com/codelabs/m3-design-theming (Material 3 semantic on-color pairs, typography and shape)
- https://m3.material.io/styles/color/roles (JS-only page; official Android codelab used for readable guidance)
- https://developer.android.com/guide/topics/ui/accessibility/apps (4.5:1 small text, 3:1 large text, 48dp controls)
- https://developers.google.com/fonts/docs/material_symbols (current Symbols, Apache 2.0, vector assets)

Observed: Daily has a dense ledger, large left-hand day numbers, blue/red totals and category text columns. Monthly has thin full-width separators, outlined month badges and unlabelled amount columns. Total leads with budget progress and a boxed accounting list. All share a narrow summary bar, red tab underline and red floating add button. Avoid reproducing these compositions, typography hierarchy, icon arrangement and spacing. The evidence does not support describing the reference as simply a green app.

## Five intentional differences

1. Warm charcoal/ivory neutrals with brushed-brass primary and muted plum accent; reserve sage and rose for labeled income/expenses. No violet/amber/orange mix or legacy blue/red Android amounts.
2. An asymmetric summary: balance leads, followed by explicitly labeled cash-flow metrics, replacing the equal-column accounting bar.
3. Daily uses inset rounded transaction cards and quiet date captions; a labeled accessible chart button complements the reversible pull gesture.
4. Monthly uses a distinct month card with labeled income/expense and a prominent net result; Total groups movement, outflow, account breakdown and report actions into distinct sections.
5. A minimal seven-day spending strip is anchored above navigation, with a matching expanded chart. Daily activity charts remain an independent view of the selected period.

## Palette and roles

| Role | Light | Dark |
| --- | --- | --- |
| Primary / brass | #75552D | #D7B98A |
| On primary | #FFFFFF | #211A11 |
| Primary container | #EFE3D0 | #352E23 |
| Secondary / taupe | #645B50 | #C3B8A9 |
| Accent / plum | #71557F | #CAB5DA |
| Income / success | #326548 | #A7CEB2 |
| Expense / danger | #A23F3D | #F0ABA4 |
| Transfer | #665878 | #C8BED8 |
| Background | #F7F5F0 | #141414 |
| Surface | #FFFFFF | #1E1E1D |
| Surface elevated | #EFEBE4 | #292826 |
| Primary text | #24231F | #F4F1EB |
| Secondary text | #625E56 | #BDB8AF |

Use Material role pairs rather than white text on every brand fill. Charts and amounts use the same semantic tokens. Target 4.5:1 for all text colors on all three dashboard surfaces, and 3:1 for meaningful chart marks. Contrast is calculated from sRGB relative luminance during verification. Borders are decorative, never the sole indicator of an action.

## Interaction contract

User confirmed one bar per day and tap-to-expand spending strip. Daily activity is the sum of absolute transaction amounts (income, expense, transfer) per calendar day within the active filter. Label this explicitly; it is not net spending. Sort dates chronologically and include zero days between the earliest and latest activity. Seven-day strip and expanded spending chart show expenses only, anchored to today, consistently across tabs. Privacy hides both chart values and shapes. Close/Back restore the underlying view without changing transactions. Chart toggle is accessible without gestures; use 48dp hit targets, cancel horizontal pulls, and respect disabled system animations.

## Icon system

Fetch current Material Symbols Rounded, 24px / weight 400, from Google's material-design-icons repository; record revision and exact per-icon sources in docs/material-symbols.json. Semantic reuse is allowed for the same function (e.g. Share, Close), never for unrelated widgets. Dedicated symbols cover activity, date navigation, notifications, search, net balance, income, expense, transfer, trend, chart reveal, dismiss, export, download, account breakdown, monthly calendar, add, and transaction category types. Keep vectors bundled for offline use; no runtime icon downloads.

## Implementation and verification

- Dashboard layouts and shared rows use `dashboard_*` colors and `ThemeOverlay.SpendTracker.Dashboard`, with light defaults and `values-night` overrides. Legacy screens keep their dark surfaces; their shared brand roles now use charcoal/brass. The bottom navigation uses the dashboard overlay.
- 46 Material Symbols were freshly downloaded from Google at revision `bd8cb85bd4bad964fe6918f79665bb40c3a8efef`; provenance and the Apache license are bundled here.
- `python3 scripts/check-dashboard-contrast.py`: all 54 foreground/background pairs pass normal-text AA. Minimum light contrast is 5.33:1; minimum dark contrast is 7.46:1. This is a resource-color check, not a whole-app accessibility certification.
- Chart aggregation tests cover chronological grouping, missing days, mixed transaction types, seven-day boundaries, daylight-saving transitions, empty input and invalid numeric values.
- Gesture review: downward pulls only start at the top of the list; horizontal motion cancels detection so swipe-to-Trash retains its existing behavior. The explicit Chart button provides a non-gesture route. Close preserves the underlying list position; chart visibility survives recreation; offscreen Daily charts do not consume Back.
- The persistent strip participates in layout measurement, so its text reserves space. The Daily add button sits above it. Privacy clears chart data and spoken values; Total's comparison percentage is also masked.

### Device update — 2026-09-26

The final debug APK built successfully and all 740 JVM tests passed (including four chart-series cases). Installed as an in-place update on the connected Samsung SM-A146B with `adb install -r`; Android returned Success. Package metadata confirms the new update time and unchanged original installation time, with no uninstall or data-clearing operation. Manual visual and gesture checks on the phone remain unverified.

Final lint run completed with one existing error in `ui/premium/PremiumPlanAdapter.java:38` (captured RecyclerView position), verified against HEAD, and 511 warnings. This is outside the redesigned dashboard. APK assembly and the unit tests succeeded; the lint failure does not prevent installing the debug APK.

### Charts and More update — 2026-09-28

- Seven-day expense spending now lives in Charts → Expenses, with the rolling date range, total, and tap-for-amount detail. The dashboard strip is removed. The rolling week stays anchored to today, independently of the selected comparison period.
- Day vs Night compares the selected period and Income/Expenses type in local time: Day includes 06:00 through 17:59, Night includes 18:00 through 05:59. It excludes transfers and inactive transactions. Both this comparison and Weekend vs Weekday have visible rounded outlines; comparison bars have outlines as well.
- The plus button keeps its existing bottom/end margins and Daily-tab visibility, with dragging removed.
- All 16 More widgets use bundled Material Symbols Rounded. Twelve additional assets use the same pinned upstream revision and license recorded in `material-symbols.json`. The grid grows to fit every widget.
- Privacy hides the seven-day and comparison chart shapes, values, and accessible summaries, and restores them when privacy is disabled.
- Verification: debug APK assembled successfully; all 756 app JVM tests passed, including Day/Night boundary, type/date refresh, and seven-day observed-stream coverage. Layout resources compile and all 16 More icons resolve. No Android device or configured emulator was available, so on-device visual verification remains pending.
