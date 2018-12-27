package com.demo.flowable;



import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@RestController
@RequestMapping("demo")
public class DemoController {
    /**
     * 启动流程节点 (启动流程实例)
     */
    @Autowired
    private RuntimeService runtimeService;
    /**
     * 流程任务 (查询分配给用户或组的任务)
     */
    @Autowired
    private TaskService taskService;
    /**
     * 管理流程资源 (查询已知的部署和流程定义，和流程资源，包括流程图)
     */
    @Autowired
    private RepositoryService repositoryService;
    /**
     * 初始化，构建流程引擎(读取配置文件，加载)
     */
    @Autowired
    private ProcessEngine processEngine;
    /**
     * 身份管理(用户和组)
     */
    private IdentityService identityService;
    /**
     * 历史数据
     */
    public HistoryService historyService;

    //请假流程 key
    private static String processKey = "demo1226";

    /**
     * 启动流程
     */
    @RequestMapping(value = "start")
    public String start(String userId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("taskUser", userId);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, map);
        return "启动 Apply 成功。流程Id为：" + processInstance.getId();
    }

    /**
     * 填写申请单
     */
    @RequestMapping(value = "init")
    public String init(String taskId) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(taskId)
                .singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
/*        HashMap<String, Object> map = new HashMap<>();
        map.put("day", 5);*/
        taskService.complete(task.getId());
        return "流程节点： 填写申请单执行成功。";
    }

    /**
     * 提交申请
     */
    @RequestMapping(value = "submit")
    public String submit(String taskId) {
        Task task = taskService.createTaskQuery().processInstanceId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        /*HashMap<String, Object> map = new HashMap<>();
        map.put("status", 1);*/
        taskService.complete(task.getId());
        return "流程节点： 提交申请执行成功。 需要审核";
    }

    /**
     * 根据userId查询代办流程
     */
    @RequestMapping(value = "/list")
    public JSONArray list(String userId) {
        List<Task> tasks = taskService.createTaskQuery().taskAssignee(userId).orderByTaskCreateTime().desc().list();
        JSONArray array = new JSONArray();
        int i = 0;
        for (Task task : tasks) {
            JSONObject jo = new JSONObject();
            jo.put("code" , i++);
            jo.put("taskId", task.getId());
            jo.put("taskName", task.getName());
            array.add(jo);
        }
        return array;
    }

    /**
     * 审核
     * @param taskId 任务ID
     */
    @RequestMapping(value = "audit")
    public String audit(String taskId) {
        Task task = taskService.createTaskQuery().processInstanceId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //通过
        HashMap<String, Object> map = new HashMap<>();
        map.put("status", "pass");
        taskService.complete(task.getId(), map);
        return "流程节点： 审核通过。  参数 " + map.get("status").toString();
    }

    /**
     * 审核
     * @param taskId 任务ID
     */
    @RequestMapping(value = "reject")
    public String reject(String taskId) {
        Task task = taskService.createTaskQuery().processInstanceId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //驳回
        HashMap<String, Object> map = new HashMap<>();
        map.put("status", "reject");
        taskService.complete(task.getId(), map);
        return "流程节点： 审核驳回。  参数 " + map.get("status").toString();
    }

    /**
     * 再次申请
     * @param taskId 任务ID
     */
    @RequestMapping(value = "again")
    public String again(String taskId) {
        Task task = taskService.createTaskQuery().processInstanceId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        taskService.complete(task.getId());
        return "流程节点： 再次申请成功。";
    }

    /**
     * 正在执行的流程图
     * @param httpServletResponse
     * @param taskId
     * @throws Exception
     */
    @RequestMapping(value = "processImage")
    public void genProcessDiagram(HttpServletResponse httpServletResponse, String taskId) throws Exception {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(taskId).singleResult();
        //流程走完的不显示图
        if (pi == null) {
            return;
        }
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        //使用流程实例ID，查询正在执行的执行对象表，返回流程实例对象
        String InstanceId = task.getProcessInstanceId();
        List<Execution> executions = runtimeService.createExecutionQuery().processInstanceId(InstanceId).list();

        //得到正在执行的Activity的Id
        List<String> activityIds = new ArrayList<>();
        List<String> flows = new ArrayList<>();
        for (Execution exe : executions) {
            List<String> ids = runtimeService.getActiveActivityIds(exe.getId());
            activityIds.addAll(ids);
        }

        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
        ProcessEngineConfiguration engconf = processEngine.getProcessEngineConfiguration();
        ProcessDiagramGenerator diagramGenerator = engconf.getProcessDiagramGenerator();
        InputStream in = diagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows, engconf.getActivityFontName(),
                engconf.getLabelFontName(), engconf.getAnnotationFontName(), engconf.getClassLoader(), 1.0, false);
        OutputStream out = null;
        byte[] buf = new byte[1024];
        int legth = 0;
        try {
            out = httpServletResponse.getOutputStream();
            while ((legth = in.read(buf)) != -1) {
                out.write(buf, 0, legth);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * 查询已完成任务
     * @param taskId
     * @return
     */
    @RequestMapping("finish")
    public Object historyActInstanceList(String taskId){
        List<HistoricActivityInstance>  list=processEngine.getHistoryService().createHistoricActivityInstanceQuery()
                                                            .processInstanceId(taskId)
                                                            .finished()
                                                            .list();
        JSONArray array = new JSONArray();
        for(HistoricActivityInstance hai:list){
            JSONObject obj = new JSONObject();
            obj.put("活动ID:", hai.getId());
            obj.put("流程实例ID:", hai.getProcessInstanceId());
            obj.put("活动名称：", hai.getActivityName());
            obj.put("办理人：", hai.getAssignee());
            obj.put("开始时间：", hai.getStartTime());
            obj.put("结束时间：", hai.getEndTime());
            array.add(obj);
        }
        return array;
    }

}

