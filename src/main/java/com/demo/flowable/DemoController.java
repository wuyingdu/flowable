package com.demo.flowable;


import org.flowable.engine.*;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
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
    private static String processKey = "Expense";

    /**
     * 添加报销
     *
     * @param userId    用户Id
     * @param money     报销金额
     * @param descption 描述
     */
    @RequestMapping(value = "add")
    public String addExpense(String userId, Integer money, String descption) {
        //启动流程
        HashMap<String, Object> map = new HashMap<>();
        map.put("taskUser", userId);
        map.put("money", money);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey, map);
        return "提交成功.流程Id为：" + processInstance.getId();
    }

    /**
     * 获取审批管理列表
     */
    @RequestMapping(value = "/list")
    public Object list(String userId) {
        List<Task> tasks = taskService.createTaskQuery().taskAssignee(userId).orderByTaskCreateTime().desc().list();
        for (Task task : tasks) {
            System.out.println(task.toString());
        }
        return tasks.toArray().toString();
    }

    /**
     * 批准
     * @param taskId 任务ID
     */
    @RequestMapping(value = "apply")
    public String apply(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("流程不存在");
        }
        //通过审核
        HashMap<String, Object> map = new HashMap<>();
        map.put("outcome", "通过");
        taskService.complete(taskId, map);
        return "processed ok!";
    }

    /**
     * 拒绝
     */
    @RequestMapping(value = "reject")
    public String reject(String taskId) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("outcome", "驳回");
        taskService.complete(taskId, map);
        return "reject";
    }

    /**
     * 生成流程图
     *
     * @param processId 任务ID
     */
    @RequestMapping(value = "processDiagram", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] genProcessDiagram(String processId) throws Exception {
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

}

