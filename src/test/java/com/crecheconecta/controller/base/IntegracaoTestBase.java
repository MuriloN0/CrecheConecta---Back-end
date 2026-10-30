package com.crecheconecta.controller.base;

import com.crecheconecta.dto.ConfirmarLoginRequest;
import com.crecheconecta.dto.LoginRequest;
import com.crecheconecta.dto.LoginResponse;
import com.crecheconecta.dto.SessaoResponse;
import com.crecheconecta.entity.FinalidadeAcao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

// Classe base com a preparação comum dos testes de integração (subir a aplicação e fazer login)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegracaoTestBase {

    protected static final String SENHA_PADRAO = "SenhaDeTeste@2026";

    private static String hashSenhaPadrao;

    @LocalServerPort
    private int port;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    protected EmailService emailService;

    protected RestClient client;

    @BeforeEach
    protected void configurarCliente() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // Nao lanca excecao em 4xx/5xx: os cenarios de erro sao verificados com assercoes
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    protected Usuario criarUsuario(Perfil perfil) {
        if (hashSenhaPadrao == null) {
            hashSenhaPadrao = passwordEncoder.encode(SENHA_PADRAO);
        }

        long ts = System.nanoTime();
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario " + perfil.name() + " " + ts);
        usuario.setEmail(perfil.name().toLowerCase() + "." + ts + "@teste.local");
        usuario.setSenhaHash(hashSenhaPadrao);
        usuario.setPerfil(perfil);
        return usuarioRepository.save(usuario);
    }

    protected String autenticar(Usuario usuario) {
        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(usuario.getEmail(), SENHA_PADRAO))
                .retrieve()
                .toEntity(LoginResponse.class)
                .getBody();

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigo(
                eq(login.acaoId()),
                eq(usuario.getEmail()),
                codigo.capture(),
                eq(FinalidadeAcao.LOGIN)
        );

        SessaoResponse sessao = client.post().uri("/api/auth/confirmar-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmarLoginRequest(login.acaoId(), codigo.getValue()))
                .retrieve()
                .toEntity(SessaoResponse.class)
                .getBody();

        return sessao.accessToken();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
