package org.itheima.ai;

import org.itheima.utils.ThreadLocalUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {
    @Test void multipartOwnerIsTakenFromSessionAndDocumentModeUsesGroundedSources() throws Exception {
        var service = mock(DocumentKnowledgeService.class);
        var vectors = mock(DocumentVectorService.class);
        when(vectors.model()).thenReturn("bge-m3");
        when(vectors.status(1)).thenReturn(Map.of("state", "idle"));
        when(vectors.sync(1)).thenReturn(Map.of("state", "running"));
        var gateway = mock(ChatModelGateway.class);
        when(gateway.configured()).thenReturn(true);
        var source = new AnnouncementKnowledgeService.Evidence(1, 7, "制度.txt", "年假提前三天申请", 0, "document");
        when(service.upload(eq(1), any())).thenReturn(Map.of("id", 7));
        when(service.retrieve(1, "年假")).thenReturn(new AnnouncementKnowledgeService.Retrieval(List.of(source), 1, false, 1));
        when(service.retrieve(1, "none")).thenReturn(new AnnouncementKnowledgeService.Retrieval(List.of(), 1, false, 1));
        when(vectors.retrieve(eq(1), eq("年假"), any())).thenReturn(new AnnouncementKnowledgeService.Retrieval(List.of(source), 1, false, 1));
        when(vectors.retrieve(eq(1), eq("none"), any())).thenReturn(new AnnouncementKnowledgeService.Retrieval(List.of(), 1, false, 1));
        doAnswer(call -> {
            ChatModelGateway.Sink sink = call.getArgument(2);
            sink.send("delta", Map.of("text", "提前三天[1]"));
            sink.send("done", Map.of("finishReason", "stop")); return null;
        }).when(gateway).streamGrounded(anyString(), anyList(), any(), any());
        var controller = new AiChatController(gateway, null, null, service, vectors);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        try {
            mvc.perform(get("/ai/knowledge/documents")).andExpect(status().isUnauthorized());
            mvc.perform(post("/ai/knowledge/documents/index")).andExpect(status().isUnauthorized());
            ThreadLocalUtil.set(Map.of("id", 1));
            mvc.perform(get("/ai/knowledge/documents/index")).andExpect(status().isOk());
            mvc.perform(post("/ai/knowledge/documents/index").param("ownerId", "2")).andExpect(status().isOk());
            verify(vectors).sync(1); verify(vectors, never()).sync(2);
            mvc.perform(multipart("/ai/knowledge/documents").file(new MockMultipartFile("file", "制度.txt", "text/plain", "demo".getBytes()))
                    .param("ownerId", "2")).andExpect(status().isOk());
            verify(service).upload(eq(1), any()); verify(service, never()).upload(eq(2), any());
            for (String mode : List.of("documents", "documents-vector")) for (String question : List.of("年假", "none")) {
                var result = mvc.perform(post("/ai/chat").contentType("application/json")
                        .content("{\"mode\":\"" + mode + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + question + "\"}]}"))
                        .andExpect(request().asyncStarted()).andReturn();
                result.getAsyncResult(3000);
                String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertTrue(body.contains("\"sourceType\":\"document\""));
                assertTrue(body.contains("event:done"));
                if (mode.equals("documents-vector")) { assertTrue(body.contains("vector-v1")); assertTrue(body.contains("bge-m3")); }
                if (question.equals("none")) assertTrue(body.contains("\"total_tokens\":0"));
            }
            verify(gateway, times(2)).streamGrounded(eq("年假"), eq(List.of(source)), any(), any());
            verify(gateway, never()).stream(any(), any(), any());
        } finally { ThreadLocalUtil.remove(); controller.shutdown(); }
    }
}
