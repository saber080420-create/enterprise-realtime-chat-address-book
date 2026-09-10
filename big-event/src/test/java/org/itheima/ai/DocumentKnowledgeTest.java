package org.itheima.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentKnowledgeTest {
    private MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test void strictTextParsingPreservesPlaintextAndRejectsUnsupportedFiles() {
        var parsed = DocumentKnowledgeService.parse(file("制度.md", "\uFEFF# 报销\r\n金额 < 3 元\r\n<script>例子</script>"));
        assertEquals("# 报销\n金额 < 3 元\n<script>例子</script>", parsed.getContent());
        assertEquals(64, parsed.getContentHash().length());
        for (String name : List.of("secret.pdf", "../a.txt", "C:\\a.txt", "x\n.txt", "a".repeat(200) + ".txt"))
            assertThrows(AiChatController.AiRequestException.class, () -> DocumentKnowledgeService.parse(file(name, "content")));
        for (String body : List.of(" ", "a".repeat(20001), "binary\u0000data"))
            assertThrows(AiChatController.AiRequestException.class, () -> DocumentKnowledgeService.parse(file("a.txt", body)));
        assertThrows(AiChatController.AiRequestException.class, () -> DocumentKnowledgeService.parse(
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{(byte) 0xc3, 0x28})));
        assertThrows(AiChatController.AiRequestException.class, () -> DocumentKnowledgeService.parse(
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[80001])));
    }

    @Test void uploadEnforcesAccountAndQuotaBeforeWriting() {
        var mapper = mock(DocumentKnowledgeMapper.class);
        var service = new DocumentKnowledgeService(mapper);
        var file = file("年假.txt", "年假提前三天申请");
        when(mapper.lockOwner(1)).thenReturn(null);
        assertEquals(403, assertThrows(AiChatController.AiRequestException.class, () -> service.upload(1, file)).status);
        when(mapper.lockOwner(1)).thenReturn(1);
        when(mapper.count(1)).thenReturn(50);
        assertEquals(409, assertThrows(AiChatController.AiRequestException.class, () -> service.upload(1, file)).status);
        verify(mapper, never()).insert(any());
        when(mapper.count(1)).thenReturn(49);
        when(mapper.insert(any())).thenAnswer(call -> { ((KnowledgeDocument) call.getArgument(0)).setId(7); return 1; });
        assertEquals(7, service.upload(1, file).get("id"));
        verify(mapper).insert(argThat(d -> d.getOwnerId() == 1));
    }

    @Test void retrievalRechecksRemovalAndRevisionAndHasTypedSources() {
        var mapper = mock(DocumentKnowledgeMapper.class);
        var service = new DocumentKnowledgeService(mapper);
        var d = DocumentKnowledgeService.parse(file("年假.md", "年假申请提前三天提交给主管。"));
        d.setId(12); d.setOwnerId(1);
        when(mapper.candidates(1)).thenReturn(List.of(d));
        assertTrue(service.retrieve(1, "年假").sources().isEmpty()); // Removed after candidate read.
        when(mapper.source(1, 12)).thenReturn(d);
        var result = service.retrieve(1, "年假");
        assertEquals(1, result.sources().size());
        assertEquals("document", result.sources().get(0).sourceType());
        assertEquals("年假申请提前三天提交给主管。", result.sources().get(0).excerpt());
        assertTrue(service.retrieve(1, "ZXQ987654").sources().isEmpty());
        var changed = DocumentKnowledgeService.parse(file("年假.md", "年假内容已调整")); changed.setId(12);
        when(mapper.source(1, 12)).thenReturn(changed);
        assertTrue(service.retrieve(1, "年假").sources().isEmpty());
        assertEquals(404, assertThrows(AiChatController.AiRequestException.class, () -> service.source(2, 12)).status);
        assertEquals(404, assertThrows(AiChatController.AiRequestException.class, () -> service.remove(2, 12)).status);
    }

    @Test void chunkingKeepsOverlapAndDocumentModeIsValid() {
        String text = "x".repeat(599) + "<important>" + "y".repeat(600);
        var chunks = DocumentKnowledgeService.chunks(text);
        assertEquals(text.substring(500, 600), chunks.get(0).substring(500));
        assertTrue(chunks.get(1).contains("<important>"));
        var request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "年假")), "documents");
        request.validate(); assertTrue(request.knowledgeMode()); assertTrue(request.documentMode());
    }
}
