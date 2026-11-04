# Issue #54 — 0.0 carb count is legit

**Status:** Done — merged via PR #55

**Status:** Done — merged via PR #55

## Summary

The Edit Results screen rejects a food item whose carb count is `0` / `0.0`, showing "All items must have a carb value greater than 0". Zero-carb foods (meat, eggs, cheese, diet drinks, etc.) are legitimate, so the edit form should accept a carb value of zero.

## Root cause / context

`EditSubmissionViewModel.validateItems()` (`app/src/main/java/com/kevcoder/carbcalculator/ui/edit/EditSubmissionViewModel.kt:180`) rejects any value where `carbs <= 0f`. This was added with the editable results feature (#49 / PR #50). The check should only reject missing/unparseable or negative values.

A unit test, `onSaveEdits sets error when carbs are zero` in `app/src/test/java/com/kevcoder/carbcalculator/ui/edit/EditSubmissionViewModelTest.kt:129`, currently codifies the wrong behavior.

## Proposed approach

1. In `validateItems()`, change the condition to `carbs == null || carbs < 0f || !carbs.isFinite()` and update the message to "All items must have a carb value of 0 or more".
2. Update tests in `EditSubmissionViewModelTest.kt`:
   - Replace the "carbs are zero" error test with one asserting `0` (and `0.0`) saves successfully with no error.
   - Add a test that a negative value (e.g. `-5`) still produces the validation error.
   - Keep/confirm a test that blank/non-numeric input still errors.
3. Check the Re-analyze path uses the same validation (it calls `validateItems`) so zero is accepted there too (the backend already accepts `0` for `estimatedCarbs`).
4. Add a patch version entry in `AboutScreen.kt` changelog.

## Risks & open questions

- Confirmed by the reporter: a meal where **every** item is 0 (total 0g) is acceptable and must save.
- Confirmed: the backend already accepts 0-carb items, so no backend change is needed.

## Acceptance criteria

- Entering `0` or `0.0` for an item's carbs on the Edit Results screen saves without error, both before and after a log is saved.
- A meal where all items are 0g (total 0g) saves successfully; add a unit test for this.
- Negative and blank/non-numeric values are still rejected.
- Unit tests pass (`docker compose run --rm build ./gradlew test`).
