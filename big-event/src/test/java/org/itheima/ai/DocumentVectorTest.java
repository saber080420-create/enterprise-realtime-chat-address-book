package org.itheima.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentVectorTest {
    private KnowledgeDocument document() {
        var d = new KnowledgeDocument(); d.setId(1); d.setOwnerId(7); d.setTitle("休假.txt");
        d.setContent("休假提前五个工作日向主管申请。<code>plain</code>"); d.setContentHash("content-hash"); return d;
    }
    private VectorSettings settings() { var settings = new VectorSettings(); settings.setEnabled(true); return settings; }
    private void finished(DocumentVectorService service) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (service.status(7).get("state").equals("running") && System.nanoTime() < until) Thread.sleep(10);
        assertEquals("completed", service.status(7).get("state"));
    }
    @Test void fingerprintIncludesOwnerAndActualContentAndTitle() {
        var d = document(); String hash = DocumentVectorService.fingerprint(d);
        d.setOwnerId(8); assertNotEquals(hash, DocumentVectorService.fingerprint(d));
        d = document(); d.setContent("changed with stale content_hash"); assertNotEquals(hash, DocumentVectorService.fingerprint(d));
        d = document(); d.setTitle("new.txt"); assertNotEquals(hash, DocumentVectorService.fingerprint(d));
    }
    @Test void currentOwnerAndRevisionAreRecheckedAfterRetrieval() throws Exception {
        var mapper = mock(DocumentKnowledgeMapper.class); var embeddings = mock(OllamaEmbeddings.class); var store = mock(PgVectorStore.class);
        var d = document(); var hash = DocumentVectorService.fingerprint(d);
        when(mapper.candidates(7)).thenReturn(List.of(d)); when(mapper.source(7, 1)).thenReturn(d);
        when(store.coverage(anyList())).thenReturn(Map.of(1, 1));
        when(embeddings.embed(anyList(), any())).thenReturn(List.of(new float[1024]));
        when(store.search(anyList(), any())).thenReturn(List.of(new PgVectorStore.Match(99, "foreign", 0, 1),
                new PgVectorStore.Match(1, "stale", 0, 1), new PgVectorStore.Match(1, hash, 99, 1),
                new PgVectorStore.Match(1, hash, 0, .8)));
        var service = new DocumentVectorService(mapper, embeddings, store, settings());
        try {
            var result = service.retrieve(7, "请假", new ChatModelGateway.Cancellation());
            assertEquals(1, result.sources().size()); assertEquals("document", result.sources().get(0).sourceType());
            assertTrue(result.sources().get(0).excerpt().contains("<code>plain</code>"));
            verify(mapper, never()).source(7, 99);
            verify(store).search(eq(List.of(new PgVectorStore.Candidate(1, hash))), any());
            when(mapper.source(7, 1)).thenReturn(null);
            assertTrue(service.retrieve(7, "请假", new ChatModelGateway.Cancellation()).sources().isEmpty());
            var changed = document(); changed.setOwnerId(8); when(mapper.source(7, 1)).thenReturn(changed);
            assertTrue(service.retrieve(7, "请假", new ChatModelGateway.Cancellation()).sources().isEmpty());
        } finally { service.shutdown(); }
    }
    @Test void emptyAndMissingAndPartialIndexHaveExplicitSemantics() throws Exception {
        var mapper = mock(DocumentKnowledgeMapper.class); var embeddings = mock(OllamaEmbeddings.class); var store = mock(PgVectorStore.class);
        var service = new DocumentVectorService(mapper, embeddings, store, settings());
        try {
            assertTrue(service.retrieve(7, "请假", new ChatModelGateway.Cancellation()).sources().isEmpty());
            verifyNoInteractions(store, embeddings);
            when(mapper.candidates(7)).thenReturn(List.of(document()));
            assertEquals(503, assertThrows(AiChatController.AiRequestException.class,
                    () -> service.retrieve(7, "请假", new ChatModelGateway.Cancellation())).status);
            verifyNoInteractions(embeddings);
            var other = document(); other.setId(2);
            when(mapper.candidates(7)).thenReturn(List.of(document(), other));
            when(store.coverage(anyList())).thenReturn(Map.of(1, 1));
            when(embeddings.embed(anyList(), any())).thenReturn(List.of(new float[1024]));
            var result = service.retrieve(7, "请假", new ChatModelGateway.Cancellation());
            assertTrue(result.limited()); assertEquals(1, result.scannedDocuments());
        } finally { service.shutdown(); }
    }
    @Test void repeatedSyncReusesAndJobDetailsArePrivate() throws Exception {
        var mapper = mock(DocumentKnowledgeMapper.class); var embeddings = mock(OllamaEmbeddings.class); var store = mock(PgVectorStore.class);
        var d = document(); when(mapper.candidates(7)).thenReturn(List.of(d)); when(mapper.source(7, 1)).thenReturn(d);
        when(store.current(eq(1), anyString(), eq(1))).thenReturn(true);
        var service = new DocumentVectorService(mapper, embeddings, store, settings());
        try {
            service.sync(7); finished(service);
            assertEquals(1, service.status(7).get("reused")); assertEquals("idle", service.status(8).get("state"));
            verifyNoInteractions(embeddings); verify(store, never()).replace(anyInt(), anyString(), anyList());
        } finally { service.shutdown(); }
    }
    @Test void removalDuringEmbeddingSkipsWriteAndConcurrentSyncIsRejected() throws Exception {
        var mapper = mock(DocumentKnowledgeMapper.class); var embeddings = mock(OllamaEmbeddings.class); var store = mock(PgVectorStore.class);
        var d = document(); when(mapper.candidates(7)).thenReturn(List.of(d)); when(mapper.source(7, 1)).thenReturn(d, (KnowledgeDocument) null);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(embeddings.embed(anyList(), any())).thenAnswer(call -> { entered.countDown(); release.await(3, TimeUnit.SECONDS); return List.of(new float[1024]); });
        var service = new DocumentVectorService(mapper, embeddings, store, settings());
        try {
            service.sync(7); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(429, assertThrows(AiChatController.AiRequestException.class, () -> service.sync(8)).status);
            release.countDown(); finished(service);
            assertEquals(1, service.status(7).get("skipped")); verify(store, never()).replace(anyInt(), anyString(), anyList());
        } finally { release.countDown(); service.shutdown(); }
    }
    @Test void embeddingFailureIsRetryableAndDoesNotReplaceOldRows() throws Exception {
        var mapper = mock(DocumentKnowledgeMapper.class); var embeddings = mock(OllamaEmbeddings.class); var store = mock(PgVectorStore.class);
        var d = document(); when(mapper.candidates(7)).thenReturn(List.of(d)); when(mapper.source(7, 1)).thenReturn(d);
        when(embeddings.embed(anyList(), any())).thenThrow(new java.io.IOException("test-only failure"));
        var service = new DocumentVectorService(mapper, embeddings, store, settings());
        try {
            service.sync(7);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (service.status(7).get("state").equals("running") && System.nanoTime() < until) Thread.sleep(10);
            assertEquals("failed", service.status(7).get("state")); verify(store, never()).replace(anyInt(), anyString(), anyList());
            when(store.current(eq(1), anyString(), eq(1))).thenReturn(true);
            service.sync(7); finished(service);
        } finally { service.shutdown(); }
    }
}
