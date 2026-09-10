package org.itheima.ai;

import org.itheima.utils.ThreadLocalUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GroupSummaryControllerTest {
    @Test void groupEndpointsUseSessionAndSummarySharesSseLifecycle() throws Exception {
        var summaries = mock(GroupSummaryService.class); var gateway = mock(ChatModelGateway.class);
        when(gateway.configured()).thenReturn(true); when(summaries.groups(7)).thenReturn(List.of());
        when(summaries.source(7, 5, 1)).thenReturn(Map.of("content", "demo"));
        doAnswer(call -> {
            ChatModelGateway.Sink sink = call.getArgument(3);
            sink.send("summary", Map.of("points", List.of(), "actions", List.of(), "sources", List.of(), "groupId", 5));
            sink.send("done", Map.of("finishReason", "stop", "usage", Map.of("total_tokens", 0))); return null;
        }).when(summaries).generate(eq(7), any(), eq(gateway), any(), any());
        var controller = new AiChatController(gateway, null, null, null, null, summaries);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        try {
            mvc.perform(get("/ai/groups")).andExpect(status().isUnauthorized());
            ThreadLocalUtil.set(Map.of("id", 7));
            mvc.perform(get("/ai/groups").param("userId", "99")).andExpect(status().isOk());
            verify(summaries).groups(7); verify(summaries, never()).groups(99);
            mvc.perform(get("/ai/groups/5/messages/1")).andExpect(status().isOk());
            String body = "{\"mode\":\"group-summary\",\"groupId\":5,\"start\":\"2026-09-08T00:00:00\",\"end\":\"2026-09-09T00:00:00\",\"messages\":[{\"role\":\"user\",\"content\":\"摘要\"}]}";
            var result = mvc.perform(post("/ai/chat").contentType("application/json").content(body)).andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(3000);
            String stream = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(stream.contains("event:ready")); assertTrue(stream.contains("event:summary")); assertTrue(stream.contains("elapsedMs"));
            mvc.perform(post("/ai/chat").contentType("application/json").content(body.replace("2026-09-09", "2026-09-30"))).andExpect(status().isBadRequest());
        } finally { ThreadLocalUtil.remove(); controller.shutdown(); }
    }
}
