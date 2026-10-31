// Verify correct member offsets using:
//   UDCOMPILEASSERT(offsetof(udSceneNode, isVisible) == 0, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, UUID) == 4, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, lastUpdate) == 48, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, itemtype) == 56, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, itemtypeStr) == 60, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pName) == 68, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pURI) == 72, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, hasBoundingBox) == 76, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, boundingBox) == 80, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, geomtype) == 128, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, geomCount) == 132, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pCoordinates) == 136, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pParent) == 140, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pNextSibling) == 144, "Wrong offset");
//   UDCOMPILEASSERT(offsetof(udSceneNode, pFirstChild) == 148, "Wrong offset");

enum Offset {
  isVisible = 0,
  UUID = 4,
  lastUpdate = 48,
  itemtype = 56,
  itemtypeStr = 60,
  pName = 68,
  pURI = 72,
  hasBoundingBox = 76,
  boundingBox = 80,
  geomtype = 128,
  geomCount = 132,
  pCoordinates = 136,
  pParent = 140,
  pNextSibling = 144,
  pFirstChild = 148,
  pUserData = 156,
}

export interface Point {
  x: number;
  y: number;
  z: number;
}

export class ProjectNode {
  projectNodePtr = 0;

  constructor(projectNodePtr: number) {
    this.projectNodePtr = projectNodePtr;
  }

  // Getter and setters

  //!
  //! Get the node visibliity flag.
  //!
  //! @return {bool} true if the node is visible and should be drawn in the scene
  //!
  get isVisible() {
    if (this.projectNodePtr == 0)
      return false;

    return globalThis.getValue(this.projectNodePtr + Offset.isVisible, 'i32') == 0 ? false : true;
  }

  //!
  //! Set the node visibliity flag.
  //!
  //! @param isVisible {bool} true if the node is visible and should be drawn in the scene
  //!
  set isVisible(isVisible) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetVisibility', 'int', ['int', 'int'], [this.projectNodePtr, isVisible ? 1 : 0]);
  }

  //!
  //! Get the node UUID.
  //!
  //! @return {string} The ID as a string
  //!
  get UUID() {
    if (this.projectNodePtr == 0)
      return;

    return globalThis.UTF8ToString(this.projectNodePtr + Offset.UUID, 37);
  }

  //!
  //! Get the last time this node was updated.
  //!
  //! @return {number} UTC time
  //!
  //get lastUpdate() {
  //  return udSDKJS_ProjectNode_GetLastUpdateInternal(this.projectNodePtr);
  //}
  lastUpdate = 0.0;

  //!
  //! Get the type of this node.
  //!
  //! @return {number} The id of the node type, see udSceneNodeType for more information.
  //!
  get itemtype(): number | undefined {
    if (this.projectNodePtr == 0)
      return;

    return globalThis.getValue(this.projectNodePtr + Offset.itemtype, 'i32');
  }

  //!
  //! Get the type of this node.
  //!
  //! @return {string} The id of the node type, see udSceneNodeType for more information.
  //!
  get itemtypeStr() {
    if (this.projectNodePtr == 0)
      return null;

    return globalThis.UTF8ToString(this.projectNodePtr + Offset.itemtypeStr);
  }

  //!
  //! Get the type of this node.
  //!
  //! @return {string} The id of the node type, see udSceneNodeType for more information.
  //!
  get name() {
    if (this.projectNodePtr == 0)
      return null;

    return globalThis.UTF8ToString(globalThis.getValue(this.projectNodePtr + Offset.pName, 'i32'));
  }

  //!
  //! Set the name of this node.
  //!
  //! @param name {string} The node name
  //!
  set name(name) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('vcJavascriptHooks_SetProjectNodeName', undefined, ['int', 'string'], [this.projectNodePtr, name]);
  }

  //!
  //! Get the URI of this node.
  //!
  //! @return {string} The node URI
  //!
  get uri() {
    if (this.projectNodePtr == 0)
      return null;

    return globalThis.UTF8ToString(globalThis.getValue(this.projectNodePtr + Offset.pURI, 'i32'));
  }

  /*
  //!
  //! Set the name of this node.
  //!
  //! @param name {string} The new node URI
  //!
  set uri(uri) {
    udSDKJS_ProjectNode_SetURIInternal(this.projectNodePtr, uri);
  }

  //!
  //! Query if the node has a bounding box
  //!
  //! @return {bool} true if the node has a bounding box, false otherwise
  //!
  get hasBoundingBox() {
    return udSDKJS_ProjectNode_GetHasBoundingBoxInternal(this.projectNodePtr) == 0 ? false : true;
  }

  //!
  //! Retrieve the node bounding box.
  //!
  //! @return {object} null if the node does not have a bounding box, otherwise will return the following object:
  //!                  { minPoint: { x, y, z },
  //!                    maxPoint: { x, y, z } }
  //!
  get boundingBox() {
    if (!this.hasBoundingBox)
      return null;

    let offset = udSDKJS_ProjectNode_GetBoundingBoxInternal(this.projectNodePtr);
    return {
      minPoint: { x: getValue(offset, "double"), y: getValue(offset + 8, "double"), z: getValue(offset + 16, "double") },
      maxPoint: { x: getValue(offset + 24, "double"), y: getValue(offset + 32, "double"), z: getValue(offset + 48, "double") }
    };
  }
  */

  //!
  //! Retrieve the geometry type of the node
  //!
  //! @return {number} A code representing the geometry type. See udScene.h to interpret this code.
  //!
  get geomtype(): number | undefined {
    if (this.projectNodePtr == 0)
      return;

    return globalThis.getValue(this.projectNodePtr + Offset.geomtype, 'i32');
  }

  /*
  //!
  //! How many geometry items can be found on this node
  //!
  //! @return {number} The number of 3D points associated with the model
  //!
  get geomCount() {
    return udSDKJS_ProjectNode_GetGeomCountInternal(this.projectNodePtr);
  }

  //!
  //! Get the geometry coordinates associated with the node
  //!
  //! @return {object} An array of {x, y, z} coordinates
  //!
  get coordinates() {
    let offset = udSDKJS_ProjectNode_GetCoordinatesInternal(this.projectNodePtr);
    let ary = [];
    let count = udSDKJS_ProjectNode_GetGeomCountInternal(this.projectNodePtr);
    for (let i = 0; i < count; i++) {
      ary.push({ x: getValue(offset, "double"), y: getValue(offset + 8, "double"), z: getValue(offset + 16, "double") });
      offset = offset + 24;
    }
    return ary;
  }

  //!
  //! Set the geometry coordinates for this node.
  //!
  //! @param coordArray {object} An array of {x, y, z} coordinates
  //! @param geomType {string} Geometry type. Options are "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon".
  //!                          For more information, see udScene.h : udSceneGeometryType
  //!
  SetGeometry(coordArray, geomType) {
    let len = coordArray.length;
    let ptr = Module._malloc(len * 3 * 8);
    let ptrBegin = ptr;
    for (let i = 0; i < len; i++) {
      Module.setValue(ptr, coordArray[i].x, 'double');
      ptr = ptr + 8;
      Module.setValue(ptr, coordArray[i].y, 'double');
      ptr = ptr + 8;
      Module.setValue(ptr, coordArray[i].z, 'double');
      ptr = ptr + 8;
    }
    let code = udSDKJS_ProjectNodeSetGeometryInternal(this.projectNodePtr, geomType, len, ptrBegin);
    Module._free(ptrBegin);
    return code;
  }
  */

  //!
  //! Get the node parent.
  //!
  //! @return {udSDKJS_ProjectNode} null if this node is the project root, parent node otherwise
  //!
  parentInternal?: ProjectNode = undefined;
  get parent(): ProjectNode | undefined {
    if (this.projectNodePtr != 0) {
      const ptr = globalThis.getValue(this.projectNodePtr + Offset.pParent, 'i32');
      if (this.parentInternal == undefined || this.parentInternal.projectNodePtr != ptr) {
        this.parentInternal = (ptr != 0) ? new ProjectNode(ptr) : undefined;
      }
    }

    return this.parentInternal;
  }

  //!
  //! Get the next sibling of this node.
  //!
  //! @return {udSDKJS_ProjectNode} null if there is no next sibling, sibling node otherwise
  //!
  nextSiblingInternal?: ProjectNode = undefined;
  get nextSibling(): ProjectNode | undefined {
    if (this.projectNodePtr != 0) {
      const ptr = globalThis.getValue(this.projectNodePtr + Offset.pNextSibling, 'i32');
      if (this.nextSiblingInternal == undefined || this.nextSiblingInternal.projectNodePtr != ptr) {
        this.nextSiblingInternal = (ptr != 0) ? new ProjectNode(ptr) : undefined;
      }
    }

    return this.nextSiblingInternal;
  }

  //!
  //! Get the first child of this node.
  //!
  //! @return {udSDKJS_ProjectNode} null if there are no children, child node otherwise
  //!
  firstChildInternal?: ProjectNode = undefined;
  get firstChild(): ProjectNode | undefined {
    if (this.projectNodePtr != 0) {
      const ptr = globalThis.getValue(this.projectNodePtr + Offset.pFirstChild, 'i32');
      if (this.firstChildInternal == undefined || this.firstChildInternal.projectNodePtr != ptr) {
        this.firstChildInternal = (ptr != 0) ? new ProjectNode(ptr) : undefined;
      }
    }

    return this.firstChildInternal;
  }

  // write by zzy
  get length(): number {
    let child = this.firstChild;
    let count = 0;
    while (child != null) {
      child = child.nextSibling;
      count++;
    }
    return count;
  }

  resetRootNodeFormat() {
    const timePtr = setInterval(() => {
      const preLength = this.length
      setTimeout(() => {
        const curLength = this.length
        if (preLength === curLength) {
          this.forEachChild((node: ProjectNode) => {
            if (node.name != null && node.name.includes('&')) {
              const entries = node.name.split('&')
              for (const entry of entries) {
                const entryArr = entry.split('=')
                const key: string = entryArr[0]
                const value: string = entryArr[1]
                if (key === 'name') {
                  node.name = value
                }
                node.SetMetadataString(key, value)
              }
            } else {
              if (node.name != null && node.name.includes('&')) {
                node.name = node.name.split('=')[1]
              }
            }
          })
          clearInterval(timePtr)
        }
      }, 3000)
    }, 100)
  }


  /*
  //!
  //! NOT IMPLEMENTED. When a project node is deleted, this function is called first
  //!
  get userDataCleanupCallback() {
    // TODO: Create setter for this to support collab projects
    return udSDKJS_ProjectNode_GetUserDataCleanupCallbackInternal(this.projectNodePtr);
  }
  
  //!
  //! Get a raw pointer to the user data associated with this node.
  //!
  //! @return {number} Pointer to the data.
  //!
  get userData() {
    return udSDKJS_ProjectNode_GetUserDataInternal(this.projectNodePtr);
  }
  */

  //!
  //! Get a metadata item of the node as an integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {number} The value to return if the metadata item isn't in the node or if it isn't of an integer type.
  //! @return {number} The metadata value
  //!
  GetMetadataInt(key: string, defaultValue: number) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(4);
    globalThis.Module.ccall('udSceneNode_GetMetadataInt', undefined, ['number', 'string', 'number', 'number'], [this.projectNodePtr, key, ptr, defaultValue]);
    const val = globalThis.getValue(ptr, 'i32');
    globalThis.Module._free(ptr);

    return val;
  }

  //!
  //! Set a metadata item of the node as an integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {number} The value to write to the metadata key
  //!
  SetMetadataInt(key: string, value: number) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetMetadataInt', undefined, ['number', 'string', 'number'], [this.projectNodePtr, key, value]);
  }

  //!
  //! Get a metadata item of the node as an unsigned integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {number} The value to return if the metadata item isn't in the node or if it isn't of an unsigned integer type.
  //! @return {number} The metadata value
  //!
  GetMetadataUint(key: string, defaultValue: number) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(4);
    globalThis.Module.ccall('udSceneNode_GetMetadataUint', undefined, ['number', 'string', 'number', 'number'], [this.projectNodePtr, key, ptr, defaultValue]);
    const val = globalThis.getValue(ptr, 'i32');
    globalThis.Module._free(ptr);

    return val;
  }

  //!
  //! Set a metadata item of the node as an unsigned integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {number} The value to write to the metadata key
  //!
  SetMetadataUint(key: string, value: number) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetMetadataUint', undefined, ['number', 'string', 'number'], [this.projectNodePtr, key, value]);
  }

  //!
  //! Get a metadata item of the node as a 64-bit integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {number} The value to return if the metadata item isn't in the node or if it isn't of a 64-bit integer type.
  //! @return {number} The metadata value
  //!
  GetMetadataInt64(key: string, defaultValue: number) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(8);
    globalThis.Module.ccall('udSceneNode_GetMetadataInt64', undefined, ['number', 'string', 'number', 'number'], [this.projectNodePtr, key, ptr, defaultValue]);
    const val = globalThis.getValue(ptr, 'i64');
    globalThis.Module._free(ptr);

    return val;
  }

  //!
  //! Set a metadata item of the node as a 64-bit integer.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {number} The value to write to the metadata key
  //!
  SetMetadataInt64(key: string, value: number) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetMetadataInt64', undefined, ['number', 'string', 'number'], [this.projectNodePtr, key, value]);
  }

  //!
  //! Get a metadata item of the node as a 64-bit floating point number.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {number} The value to return if the metadata item isn't in the node or if it isn't of a 64-bit floating point number.
  //! @return {number} The metadata value
  //!
  GetMetadataDouble(key: string, defaultValue: number) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(8);
    globalThis.Module.ccall('udSceneNode_GetMetadataDouble', undefined, ['number', 'string', 'number', 'number'], [this.projectNodePtr, key, ptr, defaultValue]);
    const val = globalThis.getValue(ptr, 'double');
    globalThis.Module._free(ptr);

    return val;
  }

  //!
  //! Set a metadata item of the node as a double.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {number} The value to write to the metadata key
  //!
  SetMetadataDouble(key: string, value: number) {
    if (this.projectNodePtr == 0)
      return;

    return globalThis.Module.ccall('udSceneNode_SetMetadataDouble', undefined, ['number', 'string', 'number'], [this.projectNodePtr, key, value]);
  }

  //!
  //! Get a metadata item of the node as a boolean.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {bool} The value to return if the metadata item isn't in the node or if it isn't of a boolean type.
  //! @return {number} The metadata value
  //!
  GetMetadataBool(key: string, defaultValue: boolean) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(1);
    globalThis.Module.ccall('udSceneNode_GetMetadataBool', undefined, ['number', 'string', 'number', 'number'], [this.projectNodePtr, key, ptr, defaultValue ? 1 : 0]);
    const val = globalThis.getValue(ptr, 'i8');
    globalThis.Module._free(ptr);

    return val == 1;
  }

  //!
  //! Set a metadata item of the node as a bool.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {bool} The value to write to the metadata key
  //!
  SetMetadataBool(key: string, value: boolean) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetMetadataBool', undefined, ['number', 'string', 'number'], [this.projectNodePtr, key, value ? 1 : 0]);
  }

  //!
  //! Get a metadata item of the node as a string.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param defaultValue {string} The value to return if the metadata item isn't in the node or if it isn't of a string type.
  //! @return {number} The metadata value
  //!
  GetMetadataString(key: string, defaultValue: string) {
    if (this.projectNodePtr == 0)
      return defaultValue;

    const ptr = globalThis.Module._malloc(1);
    globalThis.Module.ccall('udSceneNode_GetMetadataString', undefined, ['number', 'string', 'number', 'string'], [this.projectNodePtr, key, ptr, defaultValue]);
    const val = globalThis.UTF8ToString(globalThis.getValue(ptr, '*'));
    globalThis.Module._free(ptr);

    return val;
  }

  //!
  //! Set a metadata item of the node from a string.
  //!
  //! @param key {string} The name of the metadata key.
  //! @param value {string} The value to write to the metadata key
  //!
  SetMetadataString(key: string, value: string) {
    if (this.projectNodePtr == 0)
      return;

    globalThis.Module.ccall('udSceneNode_SetMetadataString', undefined, ['number', 'string', 'string'], [this.projectNodePtr, key, value]);
  }

  //!
  //! Iteration helper.
  //!
  //! let rootNode = udSDKJS_GetProjectRoot();
  //! let count = 0;
  //! rootNode.forEachChild((child) => { count++; });
  //!
  forEachChild(callback: (child: ProjectNode, index: number) => void | boolean) {
    let child = this.firstChild;
    let count = 0;
    while (child != null) {
      if (callback(child, count))
        return;
      child = child.nextSibling;
      count++;
    }
    return count;
  }

  //!
  //! Iteration helpers
  //!
  //! let rootNode = udSDKJS_GetProjectRoot();
  //! let count = 0;
  //! let iter = this.children();
  //! let child: ProjectNode = null;
  //! while ((child = iter.next().value) != null) {
  //!   count++;
  //! }
  //!
  * children(): Generator<ProjectNode, ProjectNode, ProjectNode> | undefined {
    let child = this.firstChild;
    while (child != undefined) {
      yield child;
      child = child.nextSibling;
    }
    return undefined;
  }

  checkUpdates() {
    this.lastUpdate = globalThis.getValue(this.projectNodePtr + Offset.lastUpdate, 'double');
    this.forEachChild((child: ProjectNode) => child.checkUpdates());
  }

  FetchNodeGeometryAsCartesian() {
    return new Promise<Point>((resolve) => {
      const callback = (geometry: number) => {
        globalThis.Module.removeFunction(callbackPtr);
        resolve(JSON.parse(globalThis.UTF8ToString(geometry)));
      };
      const callbackPtr = globalThis.Module.addFunction(callback, 'vi');
      globalThis.Module.ccall('vcJavascriptHooks_FetchNodeGeometryAsCartesian', undefined, ['number', 'number'], [callbackPtr, this.projectNodePtr]);
    });
  }

  UpdateNodeGeometryFromCartesian(points: Point[], newType = -1) {
    globalThis.Module.ccall('vcJavascriptHooks_UpdateNodeGeometryFromCartesian', undefined, ['number', 'string', 'number'], [this.projectNodePtr, JSON.stringify(points), newType]);
  }
}

export enum ProjectNodeType {
  Custom, //!< Need to check the itemtypeStr string manually

  PointCloud, //!< A Euclideon Unlimited Detail Point Cloud file ("UDS")
  PointOfInterest, //!< A point, line or region describing a location of interest ("POI")
  Folder, //!< A folder of other nodes ("Folder")
  Media, //!< An Image, Movie, Audio file or other media object ("Media")
  Viewpoint, //!< An Camera Location & Orientation ("Camera")
  VisualisationSettings, //!< Visualisation settings (itensity, map height etc) ("VizSet")
  I3S, //!< An Indexed 3d Scene Layer (I3S) or Scene Layer Package (SLPK) dataset ("I3S")
  Water, //!< A region describing the location of a body of water ("Water")
  ViewShed, //!< A point describing where to generate a view shed from ("ViewMap")
  Polygon, //!< A polygon model, usually an OBJ or FBX ("Polygon")
  QueryFilter, //!< A query filter to be applied to all PointCloud types in the scene ("QFilter")
  Places, //!< A collection of places that can be grouped together at a distance ("Places")
  HeightMeasurement, //!< A height measurement object ("MHeight")
  GTFS, //!< A General Transit Feed Specification object ("GTFS")
  LassoNode, //!< A Lasso Selection Folder ("LNode")
  QueryGroup, //!< A Group of Query node being represented as one node ("QGroup")
  Count //!< Total number of node types. Used internally but can be used as an iterator max when displaying different type modes.
}

export enum NodeGeometryType {
  None, //!< There is no geometry associated with this node
  Point, //!< pCoordinates is a single 3D position
  MultiPoint, //!< Array of udPGT_Point, pCoordinates is an array of 3D positions
  LineString, //!< pCoordinates is an array of 3D positions forming an open line
  MultiLineString, //!< Array of udPGT_LineString; pCoordinates is NULL and children will be present
  Polygon, //!< pCoordinates will be a closed linear ring (the outside), there MAY be children that are interior as pChildren udPGT_MultiLineString items, these should be counted as islands of the external ring.
  MultiPolygon, //!< pCoordinates is null, children will be udPGT_Polygon (which still may have internal islands)
  GeometryCollection, //!< Array of geometries; pCoordinates is NULL and children may be present of any type

  Count, //!< Total number of geometry types. Used internally but can be used as an iterator max when displaying different type modes.
  Internal, //!< Used internally when calculating other types. Do not use.
}
