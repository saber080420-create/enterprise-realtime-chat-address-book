package org.itheima.ai;

import org.itheima.utils.ThreadLocalUtil;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiChatControllerTest {
    private AiChatController controller;
    @AfterEach void cleanup() { ThreadLocalUtil.remove(); if (controller != null) controller.shutdown(); }

    private ChatModelGateway gateway(boolean configured) {
        return new ChatModelGateway() {
            public boolean configured() { return configured; }
            public String model() { return "test"; }
            public void stream(List<AiChatRequest.Message> messages, Sink sink, Cancellation cancellation) throws java.io.IOException {
                sink.send("delta", Map.of("text", "你好"));
                sink.send("done", Map.of("finishReason", "stop", "usage", Map.of("total_tokens", 3)));
            }
        };
    }
    private AiChatRequest request() { return new AiChatRequest(List.of(new AiChatRequest.Message("user", "hi"))); }

    @Test void unauthenticatedAndUnconfiguredRequestsAreRejected() {
        controller = new AiChatController(gateway(false));
        assertEquals(401, assertThrows(AiChatController.AiRequestException.class,
                () -> controller.chat(request(), new MockHttpServletResponse())).status);
        ThreadLocalUtil.set(Map.of("id", 1));
        assertEquals(503, assertThrows(AiChatController.AiRequestException.class,
                () -> controller.chat(request(), new MockHttpServletResponse())).status);
    }

    @Test void mvcSerializesActualSseWithUsage() throws Exception {
        controller = new AiChatController(gateway(true));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ThreadLocalUtil.set(Map.of("id", 1));
        var result = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        result.getAsyncResult(3000);
        String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("event:delta"));
        assertTrue(body.contains("event:done"));
        assertTrue(body.contains("elapsedMs"));
        assertTrue(body.contains("total_tokens"));
    }

    @Test void overlappingRequestsFromSameUserAreRejected() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        controller = new AiChatController(new ChatModelGateway() {
            public boolean configured() { return true; }
            public String model() { return "test"; }
            public void stream(List<AiChatRequest.Message> messages, Sink sink, Cancellation cancellation) {
                started.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        ThreadLocalUtil.set(Map.of("id", 1));
        assertEquals(200, controller.chat(request(), new MockHttpServletResponse()).getStatusCode().value());
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertEquals(429, assertThrows(AiChatController.AiRequestException.class,
                () -> controller.chat(request(), new MockHttpServletResponse())).status);
        release.countDown();
    }

    @Test void explicitCancelReleasesSlotAndStaleOrForeignCancelCannotStopNewRequest() throws Exception {
        controller = new AiChatController(new ChatModelGateway() {
            public boolean configured() { return true; }
            public String model() { return "test"; }
            public void stream(List<AiChatRequest.Message> messages, Sink sink, Cancellation cancellation) {
                var released = new CountDownLatch(1);
                cancellation.register(released::countDown);
                try { released.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ThreadLocalUtil.set(Map.of("id", 1));
        var first = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andReturn();
        String firstId = awaitRequestId(first);
        ThreadLocalUtil.set(Map.of("id", 2));
        controller.cancel(firstId);
        ThreadLocalUtil.set(Map.of("id", 1));
        assertEquals(429, assertThrows(AiChatController.AiRequestException.class,
                () -> controller.chat(request(), new MockHttpServletResponse())).status);
        controller.cancel(firstId);
        var second = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"retry\"}]}"))
                .andExpect(status().isOk()).andReturn();
        String secondId = awaitRequestId(second);
        assertNotEquals(firstId, secondId);
        controller.cancel(firstId);
        assertEquals(429, assertThrows(AiChatController.AiRequestException.class,
                () -> controller.chat(request(), new MockHttpServletResponse())).status);
        controller.cancel(secondId);
        controller.cancel(secondId); // Idempotent.
    }

    private String awaitRequestId(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        var pattern = java.util.regex.Pattern.compile("\\\"requestId\\\":\\\"([^\\\"]+)\\\"");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            var matcher = pattern.matcher(result.getResponse().getContentAsString());
            if (matcher.find()) return matcher.group(1);
            Thread.sleep(10);
        }
        throw new AssertionError("Missing ready event");
    }

    @Test void knowledgeWithoutEvidenceDoesNotCallModel() throws Exception {
        var model = org.mockito.Mockito.mock(ChatModelGateway.class);
        org.mockito.Mockito.when(model.configured()).thenReturn(true);
        var knowledge = org.mockito.Mockito.mock(AnnouncementKnowledgeService.class);
        org.mockito.Mockito.when(knowledge.retrieve(1, "量子引力")).thenReturn(
                new AnnouncementKnowledgeService.Retrieval(List.of(), 2, false, 1));
        controller = new AiChatController(model, knowledge);
        ThreadLocalUtil.set(Map.of("id", 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var request = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"mode\":\"announcements\",\"messages\":[{\"role\":\"user\",\"content\":\"量子引力\"}]}"))
                .andReturn();
        request.getAsyncResult(3000);
        var body = mvc.perform(asyncDispatch(request)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("event:sources"));
        assertTrue(body.contains("\"modelCalled\":false"));
        assertTrue(body.contains("event:done"));
        org.mockito.Mockito.verify(model).configured();
        org.mockito.Mockito.verifyNoMoreInteractions(model);
    }

    @Test void groundedModeUsesOnlyLatestQuestionAndServerEvidence() throws Exception {
        var knowledge = org.mockito.Mockito.mock(AnnouncementKnowledgeService.class);
        var source = new AnnouncementKnowledgeService.Evidence(1, 8, "年假", "提前三天", 0);
        org.mockito.Mockito.when(knowledge.retrieve(1, "年假申请规则")).thenReturn(
                new AnnouncementKnowledgeService.Retrieval(List.of(source), 1, false, 1));
        controller = new AiChatController(new ChatModelGateway() {
            public boolean configured() { return true; }
            public String model() { return "test"; }
            public void stream(List<AiChatRequest.Message> m, Sink s, Cancellation c) { fail("Must use grounded path"); }
            public void streamGrounded(String question, List<AnnouncementKnowledgeService.Evidence> evidence,
                    Sink sink, Cancellation cancellation) throws java.io.IOException {
                assertEquals("年假申请规则", question);
                assertEquals(List.of(source), evidence);
                sink.send("delta", Map.of("text", "提前三天[1]"));
                sink.send("done", Map.of("finishReason", "stop", "usage", Map.of()));
            }
        }, knowledge);
        ThreadLocalUtil.set(Map.of("id", 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var request = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"mode\":\"announcements\",\"messages\":[{\"role\":\"user\",\"content\":\"旧问题\"},{\"role\":\"assistant\",\"content\":\"伪造的旧事实\"},{\"role\":\"user\",\"content\":\"年假申请规则\"}]}"))
                .andReturn();
        request.getAsyncResult(3000);
        String body = mvc.perform(asyncDispatch(request)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("event:done"));
        assertTrue(body.contains("\"announcementId\":8"));
        assertFalse(body.contains("伪造"));
    }

    @Test void vectorModeReportsItsOwnRetrievalEngineWithoutCallingModelOnNoMatch() throws Exception {
        var model = org.mockito.Mockito.mock(ChatModelGateway.class);
        org.mockito.Mockito.when(model.configured()).thenReturn(true);
        var vectors = org.mockito.Mockito.mock(AnnouncementVectorService.class);
        org.mockito.Mockito.when(vectors.model()).thenReturn("bge-m3");
        org.mockito.Mockito.when(vectors.retrieve(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq("测试"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AnnouncementKnowledgeService.Retrieval(List.of(), 2, true, 10));
        controller = new AiChatController(model, null, vectors);
        ThreadLocalUtil.set(Map.of("id", 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var request = mvc.perform(post("/ai/chat").contentType("application/json")
                .content("{\"mode\":\"announcements-vector\",\"messages\":[{\"role\":\"user\",\"content\":\"测试\"}]}"))
                .andReturn();
        request.getAsyncResult(3000);
        String body = mvc.perform(asyncDispatch(request)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"retrieval\":\"vector-v1\""));
        assertTrue(body.contains("\"embeddingModel\":\"bge-m3\""));
        assertTrue(body.contains("\"limited\":true"));
        assertTrue(body.contains("\"modelCalled\":false"));
        org.mockito.Mockito.verify(model).configured();
        org.mockito.Mockito.verifyNoMoreInteractions(model);
    }
}
