# Mosaic Development Notes

This file records the current state of the `mdi-mosaic` project and the algorithmic milestones reached so far. It is intended as a handoff/reference note for future development sessions.

## Project Context

`mdi-mosaic` is a Java/Maven/Eclipse project outside the core `mdi` and `mdi-3D` repositories. It depends on both `mdi` and `mdi-3D`, but the goal is to keep MDI essentially frozen. Any changes to MDI should be small, general-purpose extensibility hooks, not Mosaic-specific behavior.

The project works in GSM coordinates:

- Origin: Earth center.
- +X: Earth toward Sun.
- +Z: tilted toward north magnetic pole.
- +Y: completes the right-handed GSM system.
- Length unit: Earth radii, `R_e`.

The main computational goal is to compute the areas and perimeters of patches formed by intersecting:

1. A Cartesian GSM grid,
2. A spherical shell of radius `R`,
3. A spherical theta grid,
4. A spherical phi grid.

The current visual/diagnostic strategy uses `MosaicView2D`, a `MapView2D` subclass, with custom GSM spherical grid drawing, Monte Carlo coloring, prepatch overlay, theta patch overlay, and final patch overlay.

## Current Stable Algorithm Pipeline

### Step 1: Intersect Cartesian Cells with the Sphere

The algorithm identifies Cartesian grid cells that intersect the spherical shell.

Current representative run:

```text
Intersecting cells: 3618
  CORNER_STRADDLE: 3602
  FACE_PENETRATION_NO_INSIDE_CORNERS: 16
  TANGENT_OR_NEAR_TANGENT: 0
  BOUNDARY_DEGENERATE: 0
```

The `FACE_PENETRATION_NO_INSIDE_CORNERS` cases are currently deferred for later degeneracy handling. They are near-tangent/face-penetration cases where the sphere intersects a face although no cell corner lies inside the sphere.

### Step 2: Build Ordinary Prepatches

Ordinary prepatches are built for the `CORNER_STRADDLE` cells. A prepatch is the intersection of one Cartesian cell with the spherical shell, before theta/phi subdivision.

Current representative run:

```text
Ordinary prepatches: 3602
Failed ordinary cells: 0
Deferred face-penetration cells: 16
```

Prepatches use `GeneralCurve` boundary curves. Area is computed by sampling the boundary and approximating it as a spherical polygon.

Area validation is excellent:

```text
Prepatch A_norm ≈ 1.0 to roundoff
```

Representative convergence:

```text
samples/curve=  4  A_norm=0.99999999999999640
samples/curve=  8  A_norm=1.0000000000000013
samples/curve= 16  A_norm=0.99999999999999890
samples/curve= 32  A_norm=0.99999999999999640
samples/curve= 64  A_norm=0.99999999999999860
```

### Pole Classification

Prepatch pole classification is implemented and working.

Representative run:

```text
Pole involvement:
  any pole: 2
  north: INSIDE=1
  south: INSIDE=1
```

The two pole-containing prepatches must receive special handling in later splicing stages.

### Step 3: Theta Splicing

Theta splicing subdivides prepatches by spherical theta bands.

Important points:

- Non-pole theta splicing works.
- Pole-containing prepatches are handled at the theta stage using special polar fan handling.
- Theta splicing now includes the two pole prepatches rather than deferring them.

Representative stable result:

```text
Theta patches built: 6476
Deferred pole prepatches: 0
Handled pole prepatches: 2
Theta A_norm: 1.0000001362237638
Reference A_norm: 0.99999999999999890
Delta A_norm: 1.362e-07 at samples/curve=16
```

Theta convergence is good:

```text
samples/curve=  4  theta A_norm=1.0000033871998400  delta=3.387e-06
samples/curve=  8  theta A_norm=1.0000006250807743  delta=6.251e-07
samples/curve= 16  theta A_norm=1.0000001362237638  delta=1.362e-07
samples/curve= 32  theta A_norm=1.0000000318996565  delta=3.190e-08
samples/curve= 64  theta A_norm=1.0000000077240108  delta=7.724e-09
```

This is considered validated for the current sampled approach.

### Step 4: Phi Splicing / Final Patches

Phi splicing currently works for non-polar theta patches.

Important points:

- `PhiSplicer` uses local relative-phi clipping.
- Do not use the old full-meridian-plane clipping approach; it caused huge overcounting because a meridian plane contains both `phi` and `phi + pi` branches.
- Polar-derived theta patches are currently deferred from phi splicing.
- This is the current stable committed behavior.

Representative stable non-polar phi result:

```text
Phi reference excludes polar theta patches.
Phi deferred polar: approximately 234
Phi A_norm ≈ 0.99916645391910050
Reference A_norm ≈ 0.99916699036599030
Phi delta A_norm ≈ -5.36e-7
```

This validates ordinary non-polar final patch generation.

## Failed Polar Phi Experiments

Several attempts were made to handle polar-derived theta patches directly inside `PhiSplicer`. These should not be revived without a redesigned approach.

### Failed Approach 1: Clip Polar Fan Triangles by Phi

Tried phi-splicing pole fan triangles using the ordinary phi clipper.

Result:

```text
Phi A_norm error ≈ +1.12e-4
```

The error was concentrated in the polar parents.

### Failed Approach 2: Assign Polar Fan Triangles by Midpoint Phi

Tried assigning each small polar fan triangle to one phi cell using a representative midpoint azimuth.

Result:

```text
Phi A_norm error ≈ +1.59e-4
```

The polar region was visibly overrepresented, with many fan pieces filling a large disk-like area.

### Conclusion

Do not implement polar phi splicing by clipping or assigning the internal polar fan triangles generated during theta splicing. Those fan triangles are an internal construction artifact and do not provide a clean basis for final polar phi sectors.

## Current Visualization Status

The following overlays work in `MosaicView2D`:

- Monte Carlo points,
- ordinary prepatch boundaries,
- theta patch boundaries,
- final non-polar phi patch boundaries.

Final patch visualization is useful and looks plausible for non-polar regions. The polar region remains incomplete because polar-derived theta patches are intentionally deferred from phi splicing.

The overlay colors used during debugging have been intentionally loud, especially for final patches. These should eventually be toned down for polished/book-quality figures.

## Current Stable Status Summary

```text
Step 1: Cartesian cell/sphere intersections          good
Step 2: ordinary prepatch construction               excellent
Step 3: theta splicing including pole prepatches      validated
Step 4: non-polar phi/final patches                   validated
Remaining: polar-derived theta patches in phi stage   unresolved
```

## Recommended Next Task

Implement polar phi splicing as a separate, clean branch/class, probably:

```text
edu.cnu.mdi.mosaic.phi.PolarPhiSplicer
```

Do not complicate the ordinary `PhiSplicer` further.

The next design should work at the grouped polar-cap level rather than on individual internal fan triangles. A likely approach:

1. Group polar-derived theta patches by pole, parent cell, and theta band.
2. Reconstruct or represent the polar theta band region as a polar cap/sector object.
3. Construct final polar phi sectors directly using:
   - the pole as an explicit vertex,
   - meridian great-circle arcs as phi boundaries,
   - theta-circle arcs as theta boundaries,
   - Cartesian-cell boundary constraints as needed.
4. Validate area:

```text
non-polar final patch area + polar final patch area ≈ theta patch area ≈ prepatch area
```

Target diagnostic:

```text
Phi deferred polar: 0
Phi handled polar: > 0
Full Phi A_norm ≈ Theta A_norm
```

## Useful Class/Package Map

Important packages/classes created or modified so far:

```text
edu.cnu.mdi.mosaic.algorithm
  MosaicAlgorithm
  MosaicAlgorithmOptions
  MosaicAlgorithmResult

edu.cnu.mdi.mosaic.area
  SphericalPolygonArea
  PrepatchBoundarySampler
  PrepatchAreaCalculator
  PrepatchAreaResult

edu.cnu.mdi.mosaic.patch
  Prepatch
  GeneralCurve
  PoleClassification
  PoleRelation
  PoleClassifier
  PoleStats

edu.cnu.mdi.mosaic.diagnostic
  PrepatchDiagnostic
  PrepatchDiagnosticBuilder
  PrepatchDiagnosticSummary

edu.cnu.mdi.mosaic.theta
  ThetaPatch
  ThetaSplicer
  ThetaSpliceResult
  ThetaSpliceStats
  ThetaSpliceConvergenceResult
  ThetaParentAreaDiagnostics
  ThetaParentAreaDiagnosticBuilder
  ThetaParentAreaError

edu.cnu.mdi.mosaic.phi
  PhiPatch
  PhiSplicer
  PhiSpliceResult
  PhiSpliceStats
  PhiSpliceConvergenceResult
  PhiParentAreaDiagnostics
  PhiParentAreaDiagnosticBuilder
  PhiParentAreaError
  ThetaPatchKey
```

## Notes for Future Development Sessions

When restarting in a new chat, paste this note and ask to continue from the current stable state. The next useful request is probably:

```text
Continue from NOTES.md. Help me design and implement PolarPhiSplicer as a separate class that handles polar-derived theta patches without using internal fan triangles as final-patch parents.
```

For the next phase, provide the current versions of these classes if possible:

```text
PhiSplicer.java
PhiPatch.java
ThetaPatch.java
PhiSpliceResult.java
PhiSpliceStats.java
MosaicAlgorithm.java
```
