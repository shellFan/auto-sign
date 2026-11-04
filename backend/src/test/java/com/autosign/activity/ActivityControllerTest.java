package com.autosign.activity;

import com.autosign.controller.TaskController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ActivityControllerTest {
    private final ActivityService service = mock(ActivityService.class);
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new TaskController(service)).build(); }

    @Test void everyLegacyTriggerOnlyEnsuresAuthenticatedUsersPlan() throws Exception {
        when(service.ensureToday(7L)).thenReturn(Collections.singletonMap("message", "计划已检查"));
        for (String path : new String[]{"all", "sign", "post", "reply", "forward", "view"}) {
            mvc.perform(post("/api/task/" + path).requestAttr("userId", 7L))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        }
        verify(service, times(6)).ensureToday(7L);
    }

    @Test void readsAndRecoveryCarryAuthenticatedUserScope() throws Exception {
        when(service.today(7L)).thenReturn(Collections.emptyList());
        when(service.history(7L)).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/task/today").requestAttr("userId", 7L)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/task/history").requestAttr("userId", 7L)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/task/account/9/resume").requestAttr("userId", 7L)).andExpect(jsonPath("$.code").value(200));
        verify(service).today(7L); verify(service).history(7L); verify(service).resume(7L, 9L);
    }

    @Test void templateSaveNeverTrustsBodyUserOrTopicIds() throws Exception {
        mvc.perform(put("/api/task/templates/3").requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"userId\":99,\"topicId\":88,\"postTemplates\":\"支持{topic}\",\"replyTemplates\":\"加油\"}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).saveTemplates(eq(7L), eq(3L), any(ActivityTemplates.class));
    }

    @Test void rejectedOwnershipIsReturnedAsAnError() throws Exception {
        when(service.templates(7L, 3L)).thenThrow(new IllegalArgumentException("超话不存在或无权访问"));
        mvc.perform(get("/api/task/templates/3").requestAttr("userId", 7L))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("超话不存在或无权访问"));
    }
}
