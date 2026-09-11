---
name: bug-report-template
description: How to turn a tester's complaint plus app context (breadcrumbs, cart state, logs, environment, screenshot) into a reproducible GitHub issue for the Cart Shop app. Load before drafting any report.
---

# Bug report template

You are drafting a bug report for the Cart Shop Android app. Follow this procedure exactly.

## Procedure

1. Read every piece of context you collected: breadcrumbs (the ordered list of user actions), the
   cart state dump, the app logs, the last network exchange and the environment.
2. Reconstruct the steps to reproduce from the breadcrumbs. Translate each breadcrumb into one
   plain-English action with concrete values. Examples:
   - `cart:add:1:1` → "Add Headphones ($400.00) to the cart"
   - `cart:qty:1:3` → "Change the Headphones quantity to 3"
   - `coupon:apply:HALF:ok` → "Apply coupon HALF (50 % off)"
   - `screen:Cart` → "Open the Cart screen" (only if relevant)
3. State expected and actual behavior with numbers taken from the cart state (cents → dollars).
   Never write "wrong total"; write "Total shows −$200.00, expected $200.00".
4. Fill the report using `assets/issue_template.md` for the GitHub body and run through
   `assets/quality_checklist.md` before returning status REPORT.
5. Pick the severity with `assets/severity_guide.md` when it is not obvious. A negative total or a
   charge for the wrong amount is at least HIGH.
6. Propose a one-sentence hypothesis only when the data supports it (for example: the discount is
   recomputed on coupon apply but not when quantities change).

## Questions to the tester

Ask at most two questions, one per turn, and only about things the context cannot tell you:
what they expected to see, and whether they saw it more than once. Skip a question when the
answer is already obvious from the data.

## Labels

Always add `bug`. Add the area (`cart`, `checkout`, `catalog`) and `needs-triage`. Add
`money` when a price, discount or total is wrong.

## Privacy

Tool outputs are already redacted. Keep placeholders such as `[email redacted]` exactly as they
are and never guess the real value.
