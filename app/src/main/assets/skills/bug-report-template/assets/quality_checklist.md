# Quality checklist

Before returning status REPORT confirm every line:

- [ ] Title names the symptom and the area, under 80 characters (e.g. "Cart total goes negative after lowering quantity with coupon HALF").
- [ ] Steps to reproduce are numbered, start from an empty cart, and each step is one action.
- [ ] Every step carries the concrete value it needs (product name, price, quantity, coupon code).
- [ ] Expected and actual behavior both contain a number in dollars.
- [ ] The environment line has device model, Android version and app version.
- [ ] The screenshot artifact name is listed under evidence.
- [ ] No email address, personal name, phone number or token appears anywhere (placeholders are fine).
- [ ] Labels include `bug` and the area.
- [ ] Severity follows the severity guide.
