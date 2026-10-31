import request from "@/utils/request";

export function controlRobot(data: any) {
  return request({
    url: 'http://101.133.156.32:8800/api/task/',
    data,
    method: "post"
  })
}

/**
 * 清理任务
 * */
export function clearRobotTask(robot_sn: string) {
  let length = robot_sn.length
  let id = robot_sn.substring(length - 1)
  return request({
    url: `http://101.133.156.32:8800/api/robot/group/${id}/`,
    data: {robot_sn: robot_sn, task_list: null},
    method: "post"
  })
}


/**
 * 场地人员数量限制
 * */
export function peopleNumber(data: any) {
  return request({
    url: 'http://101.133.156.32:8800/api/people_number/',
    data,
    method: "post"
  })
}


/**
 * 光栅参数设置
 * */
export function rasterConfig(data: any) {
  return request({
    url: 'http://101.133.156.32:8800/api/raster/set/',
    data,
    method: "post"
  })
}
