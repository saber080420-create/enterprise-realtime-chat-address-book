package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.itheima.pojo.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupSummaryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final String valid = "{\"points\":[{\"text\":\"约定提交报告\",\"evidenceIds\":[\"1\"]}],\"actions\":[{\"task\":\"提交报告\",\"owner\":\"小王\",\"deadline\":\"周五\",\"evidenceIds\":[\"1\"]}]}";
    private ChatMessage message() {
        var m = new ChatMessage(); m.setId(1L); m.setSenderId(2); m.setCreateTime(LocalDateTime.parse("2026-09-08T10:00:00"));
        m.setContent("小王请在周五提交报告"); m.setContentVersion(1); return m;
    }
    private AiChatRequest request() { return new AiChatRequest(List.of(new AiChatRequest.Message("user", "摘要")), "group-summary", 5, "2026-09-08T00:00:00", "2026-09-09T00:00:00"); }
    @Test void schemaRejectsInventedReferencesDatesAndMalformedJson() {
        var service = new GroupSummaryService(mock(GroupSummaryMapper.class), json);
        var sources = List.of(new GroupSummaryService.Source("1", 2, "time", "小王请在周五提交报告"));
        assertEquals("周五", service.validate(valid, sources).actions().get(0).deadline());
        assertNull(service.validate(valid.replace("\"小王\"", "null").replace("\"周五\"", "null"), sources).actions().get(0).owner());
        for (String invalid : List.of(valid.replace("[\"1\"]", "[\"99\"]"), valid.replace("\"周五\"", "\"2026-09-11\""),
                valid.replace("\"小王\"", "\"小李\""), valid + "{}", "```json\n" + valid + "\n```", "{\"points\":[],\"actions\":[],\"actions\":[]}",
                valid.replace("[\"1\"]", "[]"), "{\"points\":[],\"actions\":[],\"unexpected\":1}"))
            assertThrows(AiChatController.AiRequestException.class, () -> service.validate(invalid, sources));
    }
    @Test void rangeIsBoundedAndSummaryModeValidatesBeforeGeneration() {
        request().validate();
        assertThrows(AiChatController.AiRequestException.class, () -> GroupSummaryService.range(5, "2026-09-01T00:00:00", "2026-09-09T00:00:00"));
        assertThrows(AiChatController.AiRequestException.class, () -> GroupSummaryService.range(null, "bad", null));
    }
    @Test void emptyRangeSkipsModelAndNonmemberIsRejected() throws Exception {
        var mapper = mock(GroupSummaryMapper.class); var gateway = mock(ChatModelGateway.class);
        var service = new GroupSummaryService(mapper, json);
        assertThrows(AiChatController.AiRequestException.class, () -> service.generate(7, request(), gateway, (e,d) -> {}, new ChatModelGateway.Cancellation()));
        when(mapper.group(7, 5)).thenReturn(new ChatGroup());
        List<String> events = new ArrayList<>();
        service.generate(7, request(), gateway, (e,d) -> events.add(e), new ChatModelGateway.Cancellation());
        assertEquals(List.of("summary", "delta", "done"), events); verifyNoInteractions(gateway);
    }
    @Test void validatedResultAppearsOnlyAfterGenerationAndRecheck() throws Exception {
        var mapper = mock(GroupSummaryMapper.class); var gateway = mock(ChatModelGateway.class);
        when(mapper.group(7, 5)).thenReturn(new ChatGroup()); var m = message();
        when(mapper.messages(eq(7), eq(5), any(), any())).thenReturn(List.of(m)); when(mapper.source(7, 5, 1L)).thenReturn(m);
        List<String> events = new ArrayList<>();
        doAnswer(call -> {
            assertTrue(events.isEmpty()); ChatModelGateway.Sink sink = call.getArgument(1);
            sink.send("delta", Map.of("text", valid)); assertTrue(events.isEmpty());
            sink.send("done", Map.of("finishReason", "stop", "usage", Map.of("total_tokens", 100))); return null;
        }).when(gateway).streamSummary(anyList(), any(), any());
        var service = new GroupSummaryService(mapper, json);
        service.generate(7, request(), gateway, (e,d) -> events.add(e), new ChatModelGateway.Cancellation());
        assertEquals(List.of("summary", "delta", "done"), events);
        events.clear(); when(mapper.source(7, 5, 1L)).thenReturn(m, (ChatMessage) null);
        assertThrows(AiChatController.AiRequestException.class, () -> service.generate(7, request(), gateway, (e,d) -> events.add(e), new ChatModelGateway.Cancellation()));
        assertTrue(events.isEmpty());
    }
    @Test void oversizedMessageIsSkippedWithExplicitLimitedFlag() throws Exception {
        var mapper = mock(GroupSummaryMapper.class); var gateway = mock(ChatModelGateway.class);
        when(mapper.group(7, 5)).thenReturn(new ChatGroup()); var m = message(); m.setContent("x".repeat(2001));
        when(mapper.messages(eq(7), eq(5), any(), any())).thenReturn(List.of(m));
        new GroupSummaryService(mapper, json).generate(7, request(), gateway, (e,d) -> { if(e.equals("summary")) assertEquals(true, d.get("limited")); }, new ChatModelGateway.Cancellation());
        verifyNoInteractions(gateway);
    }
}
