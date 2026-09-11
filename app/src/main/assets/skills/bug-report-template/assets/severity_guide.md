# Severity guide

| Severity | Use when |
|---|---|
| CRITICAL | Crash, data loss, or the user can be charged a wrong amount with no way to notice before paying. |
| HIGH | A visible wrong amount (negative total, wrong discount) that blocks or corrupts checkout. |
| MEDIUM | A functional defect with a workaround (remove and re-apply the coupon fixes the total). |
| LOW | Cosmetic issues, typos, alignment, non-blocking glitches. |

A negative total is HIGH, not CRITICAL, as long as the wrong amount is shown before paying.
