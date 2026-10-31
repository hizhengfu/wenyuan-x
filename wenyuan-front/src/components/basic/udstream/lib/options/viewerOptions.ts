type getClickedItemCallbackType = (uuid: string, isMultiSelect: boolean) => void;
export type viewerOptions = {
  id: string;
  args: string[];
  clickedItemCallback: getClickedItemCallbackType;
}