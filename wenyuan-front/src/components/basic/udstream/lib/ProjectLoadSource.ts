// Copied from udScene.h

export enum ProjectLoadSource {
  Memory, //!< The project source exists in memory; udScene_CreateInMemory, udScene_LoadFromMemory or udScene_SaveToMemory
  Server, //!< The project source exists from the server; udScene_CreateInServer, udScene_LoadFromServer or udScene_SaveToServer
  URI, //!< The project source exists from a file or URL; udScene_CreateInFile, udScene_LoadFromFile or udScene_SaveToFile

  Count //!< Total number of source types. Used internally but can be used as an iterator max when displaying different source types.
}
