import {Type} from "class-transformer";

interface RobotApi {
  count?: number;
  next?: any;
  previous?: any;
  results?: Results[];
}

interface Results {
  url: string;
  position: Position;
  state: State;
  battery: Battery;
  robot_sn: string;
  idleTask: boolean;
  task_list: TaskList[];
  created?: string;
}

interface Position {
  x: number;
  y: number;
  z?: number;
  rotation: number;
  created?: string;
}

interface State {
  state: string;
  state_msg: string;
  created: string;
}

interface Battery {
  value: string;
  created: string;
}

interface TaskList {
  url?: string;
  task_id: string;
  task_info?: TaskInfo[] ;
  task_state: string;
  task_state_msg: string;
  task_time: string;
}

interface TaskInfo {
  task_type: string;
  task_order?: TaskOrder| string
}

interface TaskOrder {
  text?: string;
  x?: number;
  y?: number;
  z?: number;
  rotation?: number;
  before_hint?: string;//出发前提示
  explain_way?: string;//途中讲解
  aftet_hint?: string;//到达后提示
  explain?: string;//到达后讲解
}


interface RasterApi {
  count: number;
  next?: any;
  previous?: any;
  results: RasterResults[];
}

interface RasterResults {
  raster_state: RasterState;
  raster_id: string;
  created: string;
}

interface RasterState {
  state: string;
  state_msg: string;
  created: string;
}
