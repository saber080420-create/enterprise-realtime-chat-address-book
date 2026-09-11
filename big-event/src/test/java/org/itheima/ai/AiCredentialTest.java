package org.itheima.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiCredentialTest {
    private static final String SECRET = "test-only-credential-1234567890";
    private AiCredentialCipher cipher() { return new AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])); }

    @Test void randomizedAuthenticatedEncryptionIsBoundToOwnerAndMasterKey() {
        var cipher = cipher();
        String encrypted = cipher.encrypt(1, SECRET);
        assertFalse(encrypted.contains(SECRET));
        assertNotEquals(encrypted, cipher.encrypt(1, SECRET));
        assertEquals(SECRET, cipher.decrypt(1, encrypted));
        assertThrows(AiChatController.AiRequestException.class, () -> cipher.decrypt(2, encrypted));
        assertThrows(AiChatController.AiRequestException.class, () -> cipher.decrypt(1, encrypted.substring(0, encrypted.length() - 5) + "AAAAA"));
        byte[] other = new byte[32]; Arrays.fill(other, (byte) 1);
        assertThrows(AiChatController.AiRequestException.class, () -> new AiCredentialCipher(Base64.getEncoder().encodeToString(other)).decrypt(1, encrypted));
        assertFalse(new AiCredentialCipher("").ready());
        assertFalse(new AiCredentialCipher("invalid").ready());
        assertThrows(AiChatController.AiRequestException.class, () -> new AiCredentialCipher("").encrypt(1, SECRET));
    }

    @Test void statusDoesNotDiscloseSecretsAndMissingUserKeyNeverFallsBack() throws Exception {
        var repo = mock(AiCredentialMapper.class);
        when(repo.activeUser(1)).thenReturn(1); when(repo.activeUser(2)).thenReturn(2);
        var template = mock(DeepSeekGateway.class); when(template.model()).thenReturn("test-model");
        when(template.configured()).thenReturn(true);
        var service = new AiCredentialService(repo, cipher(), template);
        service.save(1, SECRET);
        var encrypted = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repo).save(eq(1), encrypted.capture());
        when(repo.read(1)).thenReturn(encrypted.getValue());
        assertEquals(true, service.status(1).get("configured"));
        assertEquals("7890", service.status(1).get("keySuffix"));
        assertFalse(new ObjectMapper().writeValueAsString(service.status(1)).contains(SECRET));
        service.forUser(1); verify(template).withUserKey(SECRET);
        assertEquals(false, service.status(2).get("configured"));
        assertThrows(AiChatController.AiRequestException.class, () -> service.forUser(2));
        assertThrows(AiChatController.AiRequestException.class, () -> service.save(3, SECRET));
        assertThrows(AiChatController.AiRequestException.class, () -> service.save(1, SECRET + "\r\n"));
        service.remove(1); verify(repo).remove(1);
        when(repo.read(1)).thenReturn(null);
        assertThrows(AiChatController.AiRequestException.class, () -> service.forUser(1));
        when(repo.read(1)).thenReturn("corrupt");
        assertEquals(false, service.status(1).get("configured"));
    }

    @Test void controllerUsesLoginNotPayloadUserAndMasksInputToString() throws Exception {
        var service = mock(AiCredentialService.class);
        when(service.status(7)).thenReturn(Map.of("configured", false));
        var mvc = MockMvcBuilders.standaloneSetup(new AiCredentialController(service)).build();
        try {
            ThreadLocalUtil.remove(); mvc.perform(get("/ai/config")).andExpect(status().isUnauthorized());
            ThreadLocalUtil.set(Map.of("id", 7));
            mvc.perform(put("/ai/config").contentType("application/json").content("{\"apiKey\":\"" + SECRET + "\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            verify(service).save(7, SECRET);
            mvc.perform(get("/ai/config")).andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(false));
            mvc.perform(delete("/ai/config")).andExpect(status().isOk()); verify(service).remove(7);
            var input = new AiCredentialController.KeyInput(); input.setApiKey(SECRET);
            assertFalse(input.toString().contains(SECRET));
        } finally { ThreadLocalUtil.remove(); }
    }

    @Test void connectionCheckDoesNotFollowRedirectOrExposeProviderErrorBody() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var code = new java.util.concurrent.atomic.AtomicInteger(200);
        var auth = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/models", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/leak");
            exchange.sendResponseHeaders(code.get(), -1); exchange.close();
        });
        server.start();
        try {
            var gateway = new DeepSeekGateway(new ObjectMapper(), SECRET, "http://127.0.0.1:" + server.getAddress().getPort(), "test");
            gateway.testConnection(); assertEquals("Bearer " + SECRET, auth.get());
            code.set(302); assertThrows(java.io.IOException.class, gateway::testConnection);
            code.set(401); var error = assertThrows(java.io.IOException.class, gateway::testConnection);
            assertFalse(error.getMessage().contains(SECRET));
        } finally { server.stop(0); }
    }
}
