package com.demo.flowable.Juel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *         flowable:candidateGroups="${JuelUser.getUserList()}
 *         HashMap<String, Object> map = new HashMap<>();
 *         map.put("JuelUser", new JuelUser());
 *         taskService.complete(taskId, map);
 */
public class JuelUser implements Serializable {

    public List<String> getUserList(){
        List<String> list = new ArrayList<>();
        list.add("userA");
        list.add("wyd");
        return list;
    }
}
