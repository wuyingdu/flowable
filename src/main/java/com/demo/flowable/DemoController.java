package com.demo.flowable;



import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
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
    private static String processKey = "Apply";


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
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        taskService.complete(taskId);
        return "流程节点： 填写申请单执行成功。";
    }

    /**
     * 提交申请
     */
    @RequestMapping(value = "submit")
    public String submit(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("status", 1);
        taskService.complete(taskId, map);
        return "流程节点： 提交申请执行成功。 无需审核";
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
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //通过
        HashMap<String, Object> map = new HashMap<>();
        map.put("status", "pass");
        taskService.complete(taskId, map);
        return "流程节点： 审核通过。  参数 " + map.get("status").toString();
    }

    /**
     * 审核
     * @param taskId 任务ID
     */
    @RequestMapping(value = "reject")
    public String reject(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //驳回
        HashMap<String, Object> map = new HashMap<>();
        map.put("status", "reject");
        taskService.complete(taskId, map);
        return "流程节点： 审核驳回。  参数 " + map.get("status").toString();
    }

    /**
     * 再次申请
     * @param taskId 任务ID
     */
    @RequestMapping(value = "again")
    public String again(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        taskService.complete(taskId);
        return "流程节点： 再次申请成功。";
    }

    /**
     * 流程图
     *
     */
    @RequestMapping(value = "image", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] image() throws Exception {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .list().get(0);
        String diagramResourceName = processDefinition.getDiagramResourceName();
        InputStream in = repositoryService.getResourceAsStream(
                processDefinition.getDeploymentId(), diagramResourceName);
        byte[] bytes = new byte[in.available()];
        in.read(bytes, 0, in.available());
        return bytes;
    }


    /**
     * 流程图
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
        InputStream in = null;
                //diagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows, engconf.getActivityFontName(), engconf.getLabelFontName(), engconf.getAnnotationFontName(), engconf.getClassLoader(), 1.0, false);
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


}

