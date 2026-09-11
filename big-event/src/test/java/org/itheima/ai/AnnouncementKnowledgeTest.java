package org.itheima.ai;

import org.itheima.pojo.Announcement;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementKnowledgeTest {
    private Announcement document(int id, String title, String text) {
        var a = new Announcement();
        a.setId(id); a.setTitle(title); a.setContent(text); return a;
    }

    @Test void returnsMatchingSourceAndNotUnrelatedDocument() {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var annual = document(1, "年假申请流程", "申请年假需提前三个工作日提交。");
        var parking = document(2, "停车场维修", "周末停车场关闭。");
        when(mapper.candidates(7)).thenReturn(List.of(annual, parking));
        when(mapper.visibleSource(7, 1)).thenReturn(annual);
        var result = new AnnouncementKnowledgeService(mapper).retrieve(7, "年假需要提前多久申请？");
        assertEquals(1, result.sources().size());
        assertEquals(1, result.sources().get(0).announcementId());
        assertTrue(result.sources().get(0).excerpt().contains("三个工作日"));
        verify(mapper, never()).visibleSource(7, 2);
    }

    @Test void dropsSourcesRevokedOrEditedBetweenSearchAndRevalidation() {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var a = document(1, "年假", "年假申请旧规则");
        when(mapper.candidates(7)).thenReturn(List.of(a));
        var service = new AnnouncementKnowledgeService(mapper);
        assertTrue(service.retrieve(7, "年假").sources().isEmpty());
        when(mapper.visibleSource(7, 1)).thenReturn(document(1, "年假", "年假新规则"));
        assertTrue(service.retrieve(7, "年假").sources().isEmpty());
        when(mapper.visibleSource(7, 1)).thenReturn(null);
        assertThrows(AiChatController.AiRequestException.class, () -> service.source(7, 1));
    }

    @Test void noMatchProducesNoEvidence() {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        when(mapper.candidates(7)).thenReturn(List.of(document(1, "停车", "停车场关闭")));
        assertTrue(new AnnouncementKnowledgeService(mapper).retrieve(7, "量子引力").sources().isEmpty());
    }

    @Test void stripsHtmlAndSplitsWithOverlap() {
        assertEquals("年假 & 申请", AnnouncementKnowledgeService.clean("<p>年假 &amp; 申请</p><script>alert('x')</script>"));
        var chunks = AnnouncementKnowledgeService.chunks("a".repeat(550) + "关键跨界文本" + "b".repeat(650));
        assertEquals(3, chunks.size());
        assertEquals(600, chunks.get(0).length());
        assertTrue(chunks.get(0).contains("关键跨界文本"));
        assertTrue(chunks.get(1).contains("关键跨界文本"));
        assertTrue(AnnouncementKnowledgeService.terms("API 年假").containsAll(Set.of("api", "年假")));
    }

    @Test void capsScannedDocumentsAndSourcesAndReportsLimit() {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var docs = new ArrayList<Announcement>();
        for (int i = 1; i <= 201; i++) {
            var a = document(i, "年假 " + i, "年假规则");
            docs.add(a);
            when(mapper.visibleSource(7, i)).thenReturn(a);
        }
        when(mapper.candidates(7)).thenReturn(docs);
        var result = new AnnouncementKnowledgeService(mapper).retrieve(7, "年假");
        assertTrue(result.limited());
        assertEquals(200, result.scannedDocuments());
        assertEquals(4, result.sources().size());
        assertFalse(result.sources().stream().anyMatch(s -> s.announcementId() == 201));
    }

    @Test void rejectsUnknownMode() {
        assertThrows(IllegalArgumentException.class, () -> new AiChatRequest(List.of(new AiChatRequest.Message("user", "hi")), "admin").validate());
    }
}
