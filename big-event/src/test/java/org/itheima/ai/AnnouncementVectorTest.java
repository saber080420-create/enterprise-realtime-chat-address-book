package org.itheima.ai;

import org.itheima.pojo.Announcement;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnnouncementVectorTest {
    private Announcement document() {
        var a = new Announcement(); a.setId(1); a.setType("department"); a.setDepartmentId(10);
        a.setStatus("published"); a.setTitle("年假"); a.setContent("提前五天申请"); return a;
    }

    @Test void fingerprintChangesWithContentsAndScope() {
        var a = document(); String original = AnnouncementVectorService.fingerprint(a);
        a.setDepartmentId(20); assertNotEquals(original, AnnouncementVectorService.fingerprint(a));
        a = document(); a.setContent("新规定"); assertNotEquals(original, AnnouncementVectorService.fingerprint(a));
        a = document(); a.setStatus("revoked"); assertNotEquals(original, AnnouncementVectorService.fingerprint(a));
        a = document(); a.setTitle("a\u0000b"); a.setContent("c");
        String separated = AnnouncementVectorService.fingerprint(a);
        a.setTitle("a"); a.setContent("b\u0000c");
        assertNotEquals(separated, AnnouncementVectorService.fingerprint(a));
    }

    @Test void onlyAuthorizedCurrentRevisionCanBeReturned() throws Exception {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var embeddings = mock(OllamaEmbeddings.class);
        var store = mock(PgVectorStore.class);
        var settings = new VectorSettings(); settings.setEnabled(true);
        var a = document(); var hash = AnnouncementVectorService.fingerprint(a);
        when(mapper.candidates(7)).thenReturn(List.of(a));
        when(store.coverage(anyList())).thenReturn(Map.of(1, 1));
        when(embeddings.embed(anyList(), any())).thenReturn(List.of(new float[1024]));
        when(store.search(anyList(), any())).thenReturn(List.of(
                new PgVectorStore.Match(99, "unauthorized", 0, 1), new PgVectorStore.Match(1, hash, 0, .8)));
        when(mapper.visibleSource(7, 1)).thenReturn(a);
        var service = new AnnouncementVectorService(mapper, embeddings, store, settings);
        try {
            var result = service.retrieve(7, "想请假", new ChatModelGateway.Cancellation());
            assertEquals(1, result.sources().size());
            assertEquals(1, result.sources().get(0).announcementId());
            verify(store).search(eq(List.of(new PgVectorStore.Candidate(1, hash))), any());
            verify(mapper, never()).visibleSource(7, 99);
            when(mapper.visibleSource(7, 1)).thenReturn(null);
            assertTrue(service.retrieve(7, "想请假", new ChatModelGateway.Cancellation()).sources().isEmpty());
            var changed = document(); changed.setContent("规则已更改");
            when(mapper.visibleSource(7, 1)).thenReturn(changed);
            assertTrue(service.retrieve(7, "想请假", new ChatModelGateway.Cancellation()).sources().isEmpty());
        } finally { service.shutdown(); }
    }

    @Test void missingIndexFailsExplicitlyWithoutEmbeddingOrSilentKeywordFallback() throws Exception {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var embeddings = mock(OllamaEmbeddings.class);
        var store = mock(PgVectorStore.class);
        var settings = new VectorSettings(); settings.setEnabled(true);
        when(mapper.candidates(7)).thenReturn(List.of(document()));
        when(store.coverage(anyList())).thenReturn(Map.of());
        var service = new AnnouncementVectorService(mapper, embeddings, store, settings);
        try {
            assertThrows(AiChatController.AiRequestException.class, () -> service.retrieve(7, "请假", new ChatModelGateway.Cancellation()));
            verifyNoInteractions(embeddings);
        } finally { service.shutdown(); }
    }

    @Test void syncReusesUnchangedIndexAndDoesNotExposeJobToOtherUser() throws Exception {
        var mapper = mock(AnnouncementKnowledgeMapper.class);
        var embeddings = mock(OllamaEmbeddings.class);
        var store = mock(PgVectorStore.class);
        var settings = new VectorSettings(); settings.setEnabled(true);
        var a = document();
        when(mapper.candidates(7)).thenReturn(List.of(a)); when(mapper.visibleSource(7, 1)).thenReturn(a);
        when(store.current(eq(1), anyString(), eq(1))).thenReturn(true);
        var service = new AnnouncementVectorService(mapper, embeddings, store, settings);
        try {
            service.sync(7);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (service.status(7).get("state").equals("running") && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("completed", service.status(7).get("state"));
            assertEquals(1, service.status(7).get("reused"));
            assertEquals("idle", service.status(8).get("state"));
            verifyNoInteractions(embeddings);
        } finally { service.shutdown(); }
    }

    @Test void rejectsInvalidVectorsBeforeSqlWrite() {
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.vector(new float[1024]));
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.vector(new float[3]));
        var v = new float[1024]; v[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.vector(v));
    }
}
