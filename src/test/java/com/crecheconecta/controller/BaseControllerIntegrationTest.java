package com.crecheconecta.controller;

import com.crecheconecta.dto.*;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.service.EmailService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseControllerIntegrationTest {

    protected static final String ALUNOS = "/api/alunos";

    protected static final LocalDate NASCIMENTO = LocalDate.of(2022, 5, 10);

    private static final String SENHA_TESTE = "SenhaIntegracao123!";

    @LocalServerPort
    private int porta;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private final Map<UUID, String> codigosEnviados = new ConcurrentHashMap<>();

    private RestClient client;

    protected String tokenDirecao;

    @BeforeEach
    void prepararAmbiente() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + porta)
                .defaultStatusHandler(
                        status -> true,
                        (request, response) -> {}
                )
                .build();

        codigosEnviados.clear();

        doAnswer(invocation -> {
            UUID acaoId = invocation.getArgument(0);
            String codigo = invocation.getArgument(2);

            codigosEnviados.put(acaoId, codigo);

            return "email-ficticio-" + acaoId;
        }).when(emailService).enviarCodigo(any(UUID.class), anyString(), anyString(), any());

        Usuario diretor = criarUsuario(Perfil.DIRECAO);
        tokenDirecao = autenticar(diretor);
    }

    protected Usuario criarUsuario(Perfil perfil) {
        var usuario = new Usuario();

        usuario.setNome("Usuário de integração");
        usuario.setEmail("integracao-" + UUID.randomUUID() + "@example.test");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA_TESTE));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);

        return usuarios.saveAndFlush(usuario);
    }

    protected String autenticar(Usuario usuario) {

        var login = chamar(HttpMethod.POST, "/api/auth/login", null, new LoginRequest(usuario.getEmail(), SENHA_TESTE), LoginResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().acaoId()).isNotNull();

        UUID acaoId = login.getBody().acaoId();
        String codigo = codigosEnviados.remove(acaoId);

        assertThat(codigo).isNotNull().matches("[0-9]{6}");

        var confirmacao = chamar(HttpMethod.POST, "/api/auth/confirmar-login", null, new ConfirmarLoginRequest(acaoId, codigo), SessaoResponse.class);

        assertThat(confirmacao.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(confirmacao.getBody()).isNotNull();
        assertThat(confirmacao.getBody().usuarioId())
                .isEqualTo(usuario.getId());
        assertThat(confirmacao.getBody().perfil())
                .isEqualTo(usuario.getPerfil());
        assertThat(confirmacao.getBody().accessToken()).isNotBlank();
        assertThat(confirmacao.getBody().tokenType())
                .isEqualTo("Bearer");

        return confirmacao.getBody().accessToken();
    }

    protected AlunoResponse criarAluno() {

        var request = new CadastrarAlunoRequest("Aluno " + UUID.randomUUID(), NASCIMENTO);

        var resposta = chamar(HttpMethod.POST, ALUNOS, tokenDirecao, request, AlunoResponse.class);

        assertThat(resposta.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().id()).isNotNull();

        return resposta.getBody();
    }

    protected <T> ResponseEntity<T> chamar(HttpMethod metodo, String rota, String token, Object corpo, Class<T> tipoResposta) {

        var request = client.method(metodo).uri(rota);

        if (token != null) {
            request.headers(headers -> headers.setBearerAuth(token));
        }

        if (corpo != null) {
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(corpo);
        }

        return request.retrieve().toEntity(tipoResposta);
    }

    protected void verificarErro(ResponseEntity<ErroApi> resposta, HttpStatus status, String codigo, String mensagem) {

        assertThat(resposta.getStatusCode()).isEqualTo(status);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().status()).isEqualTo(status.value());
        assertThat(resposta.getBody().codigo()).isEqualTo(codigo);
        assertThat(resposta.getBody().detail()).isEqualTo(mensagem);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaginaAlunos(List<AlunoResponse> content, int totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErroApi(int status, String codigo, String detail) {
    }
}