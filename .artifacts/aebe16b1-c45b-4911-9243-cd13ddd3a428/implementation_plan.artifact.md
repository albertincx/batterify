# Disabling Checkbox for Frozen Applications

This plan details how to update the UI to reflect the "frozen" (Force Stopped) status of applications. When an application is already stopped or newly stopped by the automation, its checkbox will be disabled to prevent redundant actions.

## Proposed Changes

### Data Model

#### [MODIFY] [RunningPackage.kt](file:///Users/albert/AndroidStudioProjects/AppsApp/app/src/main/java/com/example/appsapp/RunningPackageHelper.kt)
- Add `isFrozen: Boolean = false` to the `RunningPackage` data class.

### Communication Bridge

#### [NEW] [FreezeStatusObserver.kt](file:///Users/albert/AndroidStudioProjects/AppsApp/app/src/main/java/com/example/appsapp/FreezeStatusObserver.kt)
- Create a singleton observer (e.g., using `MutableSharedFlow` or a simple callback list) to notify the UI when a package's status changes.

### Accessibility Service

#### [MODIFY] [FreezeAccessibilityService.kt](file:///Users/albert/AndroidStudioProjects/AppsApp/app/src/main/java/com/example/appsapp/FreezeAccessibilityService.kt)
- Call `FreezeStatusObserver.notifyPackageFrozen(packageName)` whenever a package is confirmed stopped (either already disabled button or after clicking OK).

### UI Components

#### [MODIFY] [RunningPackagesAdapter.kt](file:///Users/albert/AndroidStudioProjects/AppsApp/app/src/main/java/com/example/appsapp/ui/running/RunningPackagesAdapter.kt)
- In `onBindViewHolder`, disable the `packageCheckBox` if `runningPackage.isFrozen` is true.
- Add a visual cue (e.g., alpha reduction or a "Frozen" text) for frozen items.

#### [MODIFY] [RunningPackagesFragment.kt](file:///Users/albert/AndroidStudioProjects/AppsApp/app/src/main/java/com/example/appsapp/ui/running/RunningPackagesFragment.kt)
- Observe `FreezeStatusObserver` updates.
- Update the local `runningPackages` list and notify the adapter when a status change is received.

## Verification Plan

### Manual Verification
- Select multiple apps, including some that are already stopped.
- Click "Freeze Selected".
- Observe that as each app is processed, its checkbox becomes disabled in the list once the service returns to the main app.
- Verify that apps already stopped are immediately marked as disabled upon detection.
