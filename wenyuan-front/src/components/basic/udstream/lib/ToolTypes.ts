export enum ActiveTool {
  Select, // Can select items and camera input works as expected

  MeasureLine, // Clicking places nodes in the current selected POI (or creates one if the current selected item isn't a POI)
  MeasureArea, // Clicking places nodes in the current selected POI (or creates one if the current selected item isn't a POI)
  MeasureHeight, // Clicking places nodes in the current selected POI (or creates one if the current selected item isn't a POI)

  Annotate, //Single POI
  Inspect, // Inspects the voxel under the mouse

  AddBoxFilter, // Add box filter at the current mouse position
  AddSphereFilter,// Add sphere filter at the current mouse position
  AddCylinderFilter,// Add cylinder filter at the current mouse position
  AddPolygonFilter,// Add polygon filter at the current mouse position
  AddSimpleCrossSection,// Add simple cross section filter at the current mouse position
  AddSectionView,

  AddViewShed,// Add a viewshed at the current mouse position
  Placement, // Add Places to the Places Layer
  AddMedia,// Add a media node at the current mouse position
  ViewpointBounds, // Add viewpoint camera bounds sphere
  UDSPick,
  DangerReport,
  MeasuredResults,
  VerticalLineMeasurements,
  UDSPickGeneral,

  Count
}

export enum GizmoTool {
  NoGizmo,
  Translate,
  Rotate,
  Scale
}